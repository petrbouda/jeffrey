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
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    /** How often the panel asks about a running index build. Stages take seconds to minutes. */
    private static final long INDEX_POLL_SECONDS = 2;

    /**
     * How many "nothing running" answers in a row end the watch after a build was requested. The
     * run is registered asynchronously, so the first poll can land before it exists; three in a row
     * mean it never will.
     */
    private static final int INDEX_IDLE_POLLS_TOLERATED = 3;

    /**
     * How many polls in a row may fail before the watch gives up. One missed answer is a hiccup;
     * five is a Microscope that stopped, or an endpoint that is not there, and a panel that keeps
     * asking every two seconds forever helps nobody.
     */
    private static final int INDEX_FAILED_POLLS_TOLERATED = 5;

    private final Project project;
    private final Path file;
    private final PanelRenderer renderer;

    /** One watch at a time: a second "Build index" while one is running must not start a second loop. */
    private final AtomicBoolean watchingIndex = new AtomicBoolean();

    private volatile MicroscopeClient client;
    private volatile boolean disposed;

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
     *
     * <p>The guard wraps the {@code isSupported()} call as well, not only the constructor. The JCEF
     * classes are an optional dependency (a plugin of their own since 2026.2), and when they are
     * absent the first mention of {@link CefPanelRenderer} fails while that class is being linked
     * — before any line of it runs. That {@link LinkageError} has to be caught here, one frame up.
     */
    private PanelRenderer createRenderer() {
        try {
            if (CefPanelRenderer.isSupported()) {
                CefPanelRenderer cef = new CefPanelRenderer(this, file);
                Disposer.register(this, cef);
                return cef;
            }
            LOG.info("JCEF is unavailable in this runtime, rendering the recording panel with Swing");
        } catch (LinkageError e) {
            LOG.info("JCEF classes are not present in this IDE, rendering the recording panel with Swing: "
                    + e);
        } catch (Exception e) {
            LOG.warn("Could not start the embedded browser, falling back to the Swing panel", e);
        }
        return new SwingPanelRenderer(this, file);
    }

    /** The component the tab should focus — the renderer's, not this wrapper. */
    public JComponent focusComponent() {
        return renderer.component();
    }

    /** Re-reads the Microscope address, so a corrected URL takes effect without reopening the tab. */
    public void refresh() {
        MicroscopeClient previous = client;
        client = new MicroscopeClient(JeffreySettings.getInstance().microscopeUrl());
        previous.close();
        renderer.showLoading();
        query();
    }

    @Override
    public void dispose() {
        disposed = true;
        client.close();
    }

    /**
     * Asks Microscope about the file and draws the answer. A dump whose index is missing gets one
     * more question — is a build already running? — so a tab opened while Microscope is indexing
     * shows the build rather than offering to start a second one.
     */
    private void query() {
        MicroscopeClient current = client;
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            RecordingState state = current.state(file);
            if (state.needsHeapIndex()) {
                HeapIndexBuild build = progressOrNull(current, state.profileId());
                if (build != null && !build.failed()) {
                    RecordingState watched = state.withIndexBuild(build);
                    show(watched);
                    watchIndexBuild(current, watched);
                    return;
                }
            }
            show(state);
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
                query();
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

    /**
     * Starts the index build and follows it. The panel redraws on every poll — the callout is what
     * moves, the rest of the page stays — and drops back to an ordinary ready state, figures and all,
     * once the pipeline is no longer running.
     */
    @Override
    public void buildIndex() {
        MicroscopeClient current = client;
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            RecordingState state = current.state(file);
            if (!state.needsHeapIndex()) {
                LOG.info("Ignoring an index build for a file that does not need one: file=" + file);
                show(state);
                return;
            }
            try {
                current.buildHeapIndex(state.profileId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOG.warn("Starting a heap index build in Microscope failed: file=" + file, e);
                show(state.withIndexBuild(new HeapIndexBuild(
                        HeapIndexBuild.Phase.FAILED, 0, 0, null, 0L, message(e))));
                return;
            }
            show(state.withIndexBuild(new HeapIndexBuild(
                    HeapIndexBuild.Phase.RUNNING, 0, 0, null, 0L, null)));
            watchIndexBuild(current, state);
        });
    }

    /**
     * Polls the build until it stops, one scheduled poll at a time rather than a thread that sleeps.
     * A closed tab ends the watch at the next poll; the build itself carries on in Microscope.
     */
    private void watchIndexBuild(MicroscopeClient current, RecordingState state) {
        if (!watchingIndex.compareAndSet(false, true)) {
            return;
        }
        pollIndexBuild(current, state, 0, 0);
    }

    private void pollIndexBuild(MicroscopeClient current, RecordingState state, int idlePolls, int failedPolls) {
        if (disposed) {
            watchingIndex.set(false);
            return;
        }
        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
            HeapIndexBuild build;
            try {
                build = current.heapIndexProgress(state.profileId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                watchingIndex.set(false);
                return;
            } catch (Exception e) {
                // One missed poll is not a failed build; the next one will say. Keep watching, up
                // to a point — then stop and draw whatever Microscope will answer about the file.
                if (failedPolls + 1 < INDEX_FAILED_POLLS_TOLERATED) {
                    LOG.info("Could not read the heap index progress, will ask again: file=" + file, e);
                    pollIndexBuild(current, state, idlePolls, failedPolls + 1);
                    return;
                }
                LOG.warn("Gave up following the heap index build: file=" + file, e);
                watchingIndex.set(false);
                query();
                return;
            }

            if (build == null) {
                // Idle or completed. Right after the request the run may not exist yet, so a few of
                // these are patience; more than that and there is nothing to wait for.
                if (idlePolls + 1 < INDEX_IDLE_POLLS_TOLERATED && state.indexBuild() == null) {
                    pollIndexBuild(current, state, idlePolls + 1, 0);
                    return;
                }
                watchingIndex.set(false);
                query();
                return;
            }
            RecordingState watched = state.withIndexBuild(build);
            show(watched);
            if (build.failed()) {
                watchingIndex.set(false);
                return;
            }
            pollIndexBuild(current, watched, 0, 0);
        }, INDEX_POLL_SECONDS, TimeUnit.SECONDS);
    }

    private static HeapIndexBuild progressOrNull(MicroscopeClient current, String profileId) {
        try {
            return current.heapIndexProgress(profileId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void show(RecordingState state) {
        if (disposed) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> renderer.render(state));
    }

    /**
     * Opens the profile where its kind says it should open — a recording on the dashboard, a dump on
     * its overview. The bare profile URL is not used: Microscope redirects it to the JFR dashboard
     * whatever the profile holds, and a heap dump would land on a summary of events it has none of.
     */
    @Override
    public void openProfile() {
        withProfile(state -> {
            RecordingState.ProfileSummary summary = state.summary();
            String url = summary == null
                    ? client.profileUrl(state.profileId())
                    : client.viewUrl(state.profileId(), summary.landingPath());
            BrowserUtil.browse(url);
        });
    }

    @Override
    public void openView(String viewPath) {
        if (viewPath == null || viewPath.isBlank()) {
            return;
        }
        withProfile(state -> BrowserUtil.browse(client.viewUrl(state.profileId(), viewPath)));
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
    private void withProfile(Consumer<RecordingState> onProfile) {
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            RecordingState state = client.state(file);
            if (state.profileId() == null) {
                LOG.info("Ignoring a view link for a recording with no profile: file=" + file);
                return;
            }
            onProfile.accept(state);
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
