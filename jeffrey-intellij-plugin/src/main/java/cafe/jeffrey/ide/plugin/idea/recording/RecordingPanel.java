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
import cafe.jeffrey.ide.plugin.idea.settings.JeffreySettings;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.ExtendableHTMLViewFactory;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The recording panel: what Microscope knows about this file, and what it can open.
 *
 * <p>Four states, and the panel only ever shows one — never analysed, being analysed, ready, or
 * Microscope not reachable. They are drawn rather than merely logged because the point of the tab is
 * to answer "what happens if I press this" before it is pressed.
 *
 * <p><b>Hybrid on purpose.</b> The body is markup in two {@link JEditorPane}s wearing the platform's
 * HTML kit — a document is what this content is, and tables express it better than nested
 * {@code GridBagLayout}s. The buttons and the progress bar stay real Swing between them, because an
 * HTML-drawn button always reads as fake and a fake button in an IDE is worse than an ugly one.
 *
 * <p><b>What it deliberately does not draw.</b> No flame graph, no chart, no hot-method list. The
 * ready state shows four figures and the auto-analysis lines, which is the narrow exception the
 * plugin's rule allows; everything past that is a link into Microscope, because a second renderer
 * here is a second place for the two to disagree about what a recording says.
 */
public final class RecordingPanel extends JBPanel<RecordingPanel> {

    private static final Logger LOG = Logger.getInstance(RecordingPanel.class);

    private static final String SETTINGS_DISPLAY_NAME = "Jeffrey Plugin";

    private static final int PANEL_INSET = 20;
    private static final int SECTION_GAP = 16;
    private static final int BUTTON_GAP = 8;
    private static final int PROGRESS_WIDTH = 210;

    private final Project project;
    private final Path file;
    private final JPanel content = new JBPanel<>();

    private volatile MicroscopeClient client;

    public RecordingPanel(Project project, Path file) {
        super(new BorderLayout());
        this.project = project;
        this.file = file;
        this.client = new MicroscopeClient(JeffreySettings.getInstance().microscopeUrl());

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(JBUI.Borders.empty(PANEL_INSET));
        content.setOpaque(false);

        JBScrollPane scroll = new JBScrollPane(content);
        scroll.setBorder(JBUI.Borders.empty());
        add(scroll, BorderLayout.CENTER);

        showLoading();
        query();
    }

    /** Re-reads the Microscope address, so a corrected URL takes effect without reopening the tab. */
    public void refresh() {
        client = new MicroscopeClient(JeffreySettings.getInstance().microscopeUrl());
        showLoading();
        query();
    }

    private void query() {
        MicroscopeClient current = client;
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            RecordingState state = current.state(file);
            ApplicationManager.getApplication().invokeLater(() -> render(state));
        });
    }

    private void analyze() {
        MicroscopeClient current = client;
        render(new RecordingState(
                RecordingState.Status.ANALYZING, null, null, file.getFileName().toString(), 0L, null));

        AppExecutorUtil.getAppExecutorService().execute(() -> {
            try {
                current.analyze(file);
                RecordingState state = current.state(file);
                ApplicationManager.getApplication().invokeLater(() -> render(state));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.warn("Analyzing a recording in Microscope failed: file=" + file, e);
                ApplicationManager.getApplication().invokeLater(() -> showFailure(e));
            }
        });
    }

    // --- rendering ----------------------------------------------------------------------------

    /**
     * Header pane, then the buttons, then the details pane. Two panes rather than one because the
     * actions belong between them, and a Swing row cannot live inside an HTML document.
     */
    private void render(RecordingState state) {
        String url = JeffreySettings.getInstance().microscopeUrl();

        content.removeAll();
        add(htmlPane(PanelHtml.header(state, file, url)));
        add(gap());
        add(buttonsFor(state));
        add(gap());
        add(htmlPane(PanelHtml.details(state, file, url)));
        content.add(Box.createVerticalGlue());
        content.revalidate();
        content.repaint();
    }

    private void showLoading() {
        content.removeAll();
        add(htmlPane("<html><body><span class='sml'>Asking Microscope about this file…</span></body></html>"));
        content.add(Box.createVerticalGlue());
        content.revalidate();
        content.repaint();
    }

    private void showFailure(Exception cause) {
        String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        content.removeAll();
        add(htmlPane("<html><body><span class='big'>The analysis did not finish</span><br>"
                + "<span class='warn'>" + PanelHtml.escape(message) + "</span></body></html>"));
        add(gap());
        add(buttonRow(retryButton(), settingsButton()));
        content.add(Box.createVerticalGlue());
        content.revalidate();
        content.repaint();
    }

    private JComponent buttonsFor(RecordingState state) {
        return switch (state.status()) {
            case NOT_IMPORTED, IMPORTED -> buttonRow(analyzeButton());
            // No "analyze again": a recording file does not change, so re-analysing the same bytes
            // would only import a second copy and build an identical profile. A file that really has
            // changed no longer matches by name and size, and comes back as never analysed anyway.
            case READY -> buttonRow(readyButtons(state));
            case ANALYZING -> analyzingControls();
            // Settings appears only here and on a failure — it is the one place the answer is likely
            // to be a wrong address. A button that is always present and almost never the fix teaches
            // the reader to skip the whole row.
            case UNAVAILABLE -> buttonRow(retryButton(), settingsButton());
        };
    }

    private JComponent analyzingControls() {
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(JBUI.scale(PROGRESS_WIDTH), progress.getPreferredSize().height));
        progress.setMaximumSize(progress.getPreferredSize());
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel column = new JBPanel<>();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        column.add(progress);
        column.add(Box.createVerticalStrut(JBUI.scale(BUTTON_GAP)));
        column.add(buttonRow(button("Check again", event -> query())));
        return leftAligned(column);
    }

    /**
     * Open in Microscope, then one button per known agent.
     *
     * <p>An agent that is not installed keeps its button, disabled, rather than disappearing: the row
     * then looks the same on every machine, and a developer who has never heard of the Codex support
     * can at least see that it exists.
     */
    private JButton[] readyButtons(RecordingState state) {
        boolean heapDump = state.summary() != null && state.summary().isHeapDump();

        List<JButton> buttons = new ArrayList<>();
        buttons.add(openButton(state.profileId()));
        if (JeffreySettings.getInstance().areAgentsEnabled()) {
            for (AgentCli agent : AgentCli.ALL) {
                buttons.add(agentButton(agent, state.profileId(), heapDump));
            }
        }
        return buttons.toArray(new JButton[0]);
    }

    private JButton agentButton(AgentCli agent, String profileId, boolean heapDump) {
        JButton button = button("Analyse with " + agent.displayName(),
                event -> launchAgent(agent, profileId, heapDump));
        if (!agent.isInstalled()) {
            button.setEnabled(false);
        }
        return button;
    }

    /**
     * Hands the agent the profile id, never the file path — neither Claude Code nor Codex can parse a
     * JFR, and Microscope has already done it. The prompt carries no question of its own: the method
     * lives in the agent's {@code analyze-jfr} skill, and the panel does not know what the developer
     * wants to ask.
     */
    private void launchAgent(AgentCli agent, String profileId, boolean heapDump) {
        Path workingDirectory = workingDirectory();
        String command = agent.command(profileId, heapDump);
        try {
            AgentLaunchers.current().launch(project, workingDirectory, command);
        } catch (Exception e) {
            LOG.warn("Could not start an agent: agent=" + agent.executable() + " file=" + file, e);
        }
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

    private JButton analyzeButton() {
        return button("Analyze in Microscope", event -> analyze());
    }

    private JButton openButton(String profileId) {
        return button("Open in Microscope", event -> BrowserUtil.browse(client.profileUrl(profileId)));
    }

    private JButton retryButton() {
        return button("Try again", event -> refresh());
    }

    private JButton settingsButton() {
        return button("Settings…", event ->
                ShowSettingsUtil.getInstance().showSettingsDialog(null, SETTINGS_DISPLAY_NAME));
    }

    private JButton button(String text, java.awt.event.ActionListener onClick) {
        JButton button = new JButton(text);
        button.addActionListener(onClick);
        return button;
    }

    private JComponent buttonRow(JButton... buttons) {
        JPanel row = new JBPanel<>();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        for (int i = 0; i < buttons.length; i++) {
            if (i > 0) {
                row.add(Box.createHorizontalStrut(JBUI.scale(BUTTON_GAP)));
            }
            row.add(buttons[i]);
        }
        return leftAligned(row);
    }

    // --- the HTML surface ---------------------------------------------------------------------

    /**
     * A read-only pane wearing the platform's HTML kit, with the panel's icons registered and the
     * stylesheet built from the current theme.
     *
     * <p>Every link's {@code href} is a Microscope view path, so one listener serves the whole
     * document — the reason nine {@code ActionLink}s and their nine lambdas are gone.
     */
    private JComponent htmlPane(String html) {
        HTMLEditorKit kit = new HTMLEditorKitBuilder()
                .withViewFactoryExtensions(
                        ExtendableHTMLViewFactory.Extensions.icons(PanelIcons.BY_KEY),
                        ExtendableHTMLViewFactory.Extensions.WORD_WRAP)
                .withStyleSheet(PanelStyles.current())
                .build();

        JEditorPane pane = new JEditorPane();
        pane.setEditorKit(kit);
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(JBUI.Borders.empty());
        pane.setBackground(UIUtil.getPanelBackground());
        pane.setText(html);
        pane.setCaretPosition(0);
        pane.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openView(event.getDescription());
            }
        });
        return leftAligned(pane);
    }

    /**
     * Opens a view path in the browser. Resolved against the profile the panel last saw rather than
     * captured per link, so a stale document cannot outlive the profile it described.
     */
    private void openView(String viewPath) {
        if (viewPath == null || viewPath.isBlank()) {
            return;
        }
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            RecordingState state = client.state(file);
            if (state.profileId() == null) {
                LOG.info("Ignoring a view link for a recording with no profile: file=" + file);
                return;
            }
            BrowserUtil.browse(client.viewUrl(state.profileId(), viewPath));
        });
    }

    private void add(JComponent section) {
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(section);
    }

    private JComponent gap() {
        return (JComponent) Box.createVerticalStrut(JBUI.scale(SECTION_GAP));
    }

    /**
     * Keeps a section at its natural height instead of letting BoxLayout stretch it down the tab,
     * which is what turns a four-row header into a header with a hundred pixels of dead space in it.
     */
    private JComponent leftAligned(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        return component;
    }
}
