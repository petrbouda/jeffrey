/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.ide.plugin.idea.recording;

import cafe.jeffrey.ide.plugin.idea.agent.AgentCli;
import cafe.jeffrey.ide.plugin.idea.agent.AgentLaunchers;
import cafe.jeffrey.ide.plugin.idea.recording.web.CefPanelRenderer;
import cafe.jeffrey.ide.plugin.idea.settings.JeffreySettings;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.concurrency.AppExecutorUtil;

import javax.swing.JComponent;
import java.awt.BorderLayout;
import java.nio.file.Path;

/**
 * The recording panel: what Microscope knows about this file, and what it can open.
 *
 * <p>Five states, and the panel only ever shows one — never analysed, being analysed, ready,
 * Microscope not reachable, or an analysis that threw. They are drawn rather than merely logged
 * because the point of the tab is to answer "what happens if I press this" before it is pressed.
 *
 * <p><b>This class draws nothing.</b> It owns the conversation with Microscope, the state machine
 * over it, and the actions the page can trigger; a {@link PanelRenderer} turns that into pixels. Two
 * exist — the IDE's bundled Chromium where it runs, Swing's HTML kit where it does not — and which
 * one is in use is decided once, here, at construction.
 *
 * <p><b>What it deliberately does not draw.</b> No flame graph, no chart, no hot-method list. The
 * ready state shows four figures and the auto-analysis lines, which is the narrow exception the
 * plugin's rule allows; everything past that is a link into Microscope, because a second renderer
 * here is a second place for the two to disagree about what a recording says.
 */
public final class RecordingPanel extends JBPanel<RecordingPanel> implements PanelActions, Disposable {

    private static final Logger LOG = Logger.getInstance(RecordingPanel.class);

    private static final String SETTINGS_DISPLAY_NAME = "Jeffrey Plugin";

    private final Project project;
    private final Path file;
    private final PanelRenderer renderer;

    private volatile MicroscopeClient client;

    public RecordingPanel(Project project, Path file) {
        super(new BorderLayout());
        this.project = project;
        this.file = file;
        this.client = new MicroscopeClient(JeffreySettings.getInstance().microscopeUrl());
        this.renderer = createRenderer();

        add(renderer.component(), BorderLayout.CENTER);

        // The stylesheet is built from the current theme by both renderers, so a theme switch has to
        // be a re-render. There was no listener for this before, and the panel only re-themed by
        // accident when the tab was reselected.
        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(LafManagerListener.TOPIC, (LafManagerListener) source -> renderer.themeChanged());

        renderer.showLoading();
        query();
    }

    /**
     * Chromium where the runtime has it, Swing's HTML kit otherwise.
     *
     * <p>{@code isSupported()} is false on a JBR built without JCEF and inside the JetBrains Client,
     * so the fallback is not theoretical. Anything thrown while building the browser lands here too:
     * a tab that renders plainly beats a tab that renders an exception.
     */
    private PanelRenderer createRenderer() {
        if (CefPanelRenderer.isSupported()) {
            try {
                CefPanelRenderer cef = new CefPanelRenderer(this, file);
                Disposer.register(this, cef);
                return cef;
            } catch (Exception | LinkageError e) {
                LOG.warn("Could not start the embedded browser, falling back to the Swing panel", e);
            }
        } else {
            LOG.info("JCEF is unavailable in this runtime, rendering the recording panel with Swing");
        }
        return new SwingPanelRenderer(this, file);
    }

    /** The component the tab should focus — the renderer's, not this wrapper. */
    public JComponent focusComponent() {
        return renderer.component();
    }

    /** Re-reads the Microscope address, so a corrected URL takes effect without reopening the tab. */
    public void refresh() {
        client = new MicroscopeClient(JeffreySettings.getInstance().microscopeUrl());
        renderer.showLoading();
        query();
    }

    @Override
    public void dispose() {
    }

    private void query() {
        MicroscopeClient current = client;
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            RecordingState state = current.state(file);
            ApplicationManager.getApplication().invokeLater(() -> renderer.render(state));
        });
    }

    // --- actions the page can trigger -----------------------------------------------------------

    @Override
    public void analyze() {
        MicroscopeClient current = client;
        renderer.render(new RecordingState(
                RecordingState.Status.ANALYZING, null, null, file.getFileName().toString(), 0L, null));

        AppExecutorUtil.getAppExecutorService().execute(() -> {
            try {
                current.analyze(file);
                RecordingState state = current.state(file);
                ApplicationManager.getApplication().invokeLater(() -> renderer.render(state));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.warn("Analyzing a recording in Microscope failed: file=" + file, e);
                ApplicationManager.getApplication().invokeLater(() -> renderer.showFailure(message(e)));
            }
        });
    }

    @Override
    public void retry() {
        refresh();
    }

    @Override
    public void checkAgain() {
        query();
    }

    @Override
    public void openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(null, SETTINGS_DISPLAY_NAME);
    }

    @Override
    public void openProfile() {
        withProfile(profileId -> BrowserUtil.browse(client.profileUrl(profileId)));
    }

    @Override
    public void openView(String viewPath) {
        if (viewPath == null || viewPath.isBlank()) {
            return;
        }
        withProfile(profileId -> BrowserUtil.browse(client.viewUrl(profileId, viewPath)));
    }

    /**
     * Hands the agent the profile id, never the file path — neither Claude Code nor Codex can parse a
     * JFR, and Microscope has already done it. The prompt carries no question of its own: the method
     * lives in the agent's {@code analyze-jfr} skill, and the panel does not know what the developer
     * wants to ask.
     *
     * <p>Launching also remembers the agent, which is what the split button's primary half runs next
     * time. Without it that choice would fall to whichever entry {@code AgentCli.ALL} declares first.
     */
    @Override
    public void launchAgent(AgentCli agent) {
        JeffreySettings.getInstance().setPreferredAgent(agent.executable());

        AppExecutorUtil.getAppExecutorService().execute(() -> {
            RecordingState state = client.state(file);
            if (state.profileId() == null) {
                LOG.info("Ignoring an agent launch for a recording with no profile: file=" + file);
                return;
            }
            boolean heapDump = state.summary() != null && state.summary().isHeapDump();
            String command = agent.command(state.profileId(), heapDump);
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    AgentLaunchers.current().launch(project, workingDirectory(), command);
                } catch (Exception e) {
                    LOG.warn("Could not start an agent: agent=" + agent.executable() + " file=" + file, e);
                }
            });
        });
    }

    /**
     * Resolves the profile the panel last saw, rather than one captured when the document was drawn,
     * so a stale page cannot outlive the profile it described.
     */
    private void withProfile(java.util.function.Consumer<String> onProfile) {
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            RecordingState state = client.state(file);
            if (state.profileId() == null) {
                LOG.info("Ignoring a view link for a recording with no profile: file=" + file);
                return;
            }
            onProfile.accept(state.profileId());
        });
    }

    /** The project root, so the agent starts where the developer's code is, not beside the recording. */
    private Path workingDirectory() {
        String basePath = project.getBasePath();
        if (basePath != null) {
            return Path.of(basePath);
        }
        Path parent = file.getParent();
        return parent == null ? file : parent;
    }

    private static String message(Exception cause) {
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
