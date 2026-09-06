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

package cafe.jeffrey.ide.plugin.idea.recording.web;

import cafe.jeffrey.ide.plugin.idea.agent.AgentCli;
import cafe.jeffrey.ide.plugin.idea.agent.AgentRow;
import cafe.jeffrey.ide.plugin.idea.recording.PanelActions;
import cafe.jeffrey.ide.plugin.idea.recording.PanelRenderer;
import cafe.jeffrey.ide.plugin.idea.recording.RecordingState;
import cafe.jeffrey.ide.plugin.idea.settings.JeffreySettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import com.intellij.util.ui.UIUtil;

import javax.swing.JComponent;
import java.nio.file.Path;

/**
 * The panel rendered as a real web page in the IDE's bundled Chromium.
 *
 * <p>This is what buys the design: grid, rounded corners, {@code :hover}, a stylesheet with custom
 * properties, and inline SVG that tints itself. Swing's HTML engine drops every one of those
 * silently, which is why {@code SwingPanelRenderer} looks the way it does and why it is now only the
 * fallback.
 *
 * <p><b>One browser, one Chromium render process, one editor tab.</b> That is not free, so the
 * browser is a {@link Disposable} registered with the tab: closing the recording reaps the process.
 *
 * <p><b>Clicks come back over one query.</b> The page has no {@code href} anywhere and cannot
 * navigate; every control carries a {@code data-action} string, and a single {@link JBCefJSQuery}
 * carries that string to Java. The handler runs on a CEF thread, so everything it triggers is posted
 * to the EDT.
 */
public final class CefPanelRenderer implements PanelRenderer, Disposable {

    private static final Logger LOG = Logger.getInstance(CefPanelRenderer.class);

    private static final String VIEW_PREFIX = "view:";
    private static final String AGENT_PREFIX = "agent:";
    private static final String ACTION_ANALYZE = "analyze";
    private static final String ACTION_RETRY = "retry";
    private static final String ACTION_CHECK = "check";
    private static final String ACTION_SETTINGS = "settings";
    private static final String ACTION_OPEN = "open";

    private final PanelActions actions;
    private final Path file;
    private final JBCefBrowser browser;
    private final JBCefJSQuery query;
    private final String bridgeScript;

    /** The last thing drawn, so a look-and-feel change can be redrawn without asking Microscope. */
    private volatile Runnable lastRender = () -> {
    };

    /** Whether the IDE's bundled Chromium can actually run here. */
    public static boolean isSupported() {
        return JBCefApp.isSupported();
    }

    public CefPanelRenderer(PanelActions actions, Path file) {
        this.actions = actions;
        this.file = file;
        this.browser = JBCefBrowser.createBuilder().setOffScreenRendering(false).build();
        this.query = JBCefJSQuery.create((JBCefBrowserBase) browser);

        Disposer.register(this, browser);
        Disposer.register(browser, query);

        query.addHandler(payload -> {
            ApplicationManager.getApplication().invokeLater(() -> dispatch(payload));
            return null;
        });

        // The page's own script calls window.__jeffrey(action); this is what that name resolves to.
        this.bridgeScript = "window.__jeffrey=function(action){" + query.inject("action") + "};";

        browser.getComponent().setBackground(UIUtil.getPanelBackground());
    }

    @Override
    public JComponent component() {
        return browser.getComponent();
    }

    @Override
    public void render(RecordingState state) {
        AgentRow agents = AgentRow.resolve(AgentCli.ALL, JeffreySettings.getInstance().preferredAgent());
        WebPanelHtml.Content content = new WebPanelHtml.Content(
                state,
                file,
                JeffreySettings.getInstance().microscopeUrl(),
                agents,
                JeffreySettings.getInstance().areAgentsEnabled());
        load(() -> WebPanelHtml.document(content, bridgeScript));
    }

    @Override
    public void showLoading() {
        load(() -> WebPanelHtml.loading(bridgeScript));
    }

    @Override
    public void showFailure(String message) {
        load(() -> WebPanelHtml.failure(message, file.getFileName().toString(), size(), bridgeScript));
    }

    @Override
    public void themeChanged() {
        browser.getComponent().setBackground(UIUtil.getPanelBackground());
        lastRender.run();
    }

    @Override
    public void dispose() {
    }

    /**
     * Loads a document and remembers how to build it again.
     *
     * <p>The supplier rather than the string, because a redraw after a theme change has to rebuild the
     * stylesheet from the new look and feel — replaying the old string would replay the old colours.
     */
    private void load(java.util.function.Supplier<String> document) {
        lastRender = () -> browser.loadHTML(document.get());
        lastRender.run();
    }

    private long size() {
        try {
            return java.nio.file.Files.size(file);
        } catch (Exception e) {
            return 0L;
        }
    }

    private void dispatch(String action) {
        if (action == null || action.isBlank()) {
            return;
        }
        if (action.startsWith(VIEW_PREFIX)) {
            actions.openView(action.substring(VIEW_PREFIX.length()));
            return;
        }
        if (action.startsWith(AGENT_PREFIX)) {
            launchAgent(action.substring(AGENT_PREFIX.length()));
            return;
        }
        switch (action) {
            case ACTION_ANALYZE -> actions.analyze();
            case ACTION_RETRY -> actions.retry();
            case ACTION_CHECK -> actions.checkAgain();
            case ACTION_SETTINGS -> actions.openSettings();
            case ACTION_OPEN -> actions.openProfile();
            // An action the page sent that this build does not know is a bug in the pairing, not in
            // the developer's click — say so in the log and do nothing visible.
            default -> LOG.warn("Unknown panel action: action=" + action + " file=" + file);
        }
    }

    private void launchAgent(String executable) {
        AgentRow agents = AgentRow.resolve(AgentCli.ALL, JeffreySettings.getInstance().preferredAgent());
        AgentCli agent = agents.byExecutable(executable);
        if (agent == null) {
            LOG.warn("Unknown agent requested by the panel: executable=" + executable);
            return;
        }
        actions.launchAgent(agent);
    }
}
