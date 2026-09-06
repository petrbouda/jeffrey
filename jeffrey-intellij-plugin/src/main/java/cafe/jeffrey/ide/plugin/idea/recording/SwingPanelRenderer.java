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
import cafe.jeffrey.ide.plugin.idea.settings.JeffreySettings;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The panel drawn with Swing's HTML kit — the fallback, for machines where JCEF cannot run.
 *
 * <p>This was the whole panel until the CEF renderer arrived, and it is kept verbatim rather than
 * rewritten: it already works, and a fallback that is exercised only on unusual setups is exactly the
 * code you do not want to have just changed.
 *
 * <p><b>It is not held to visual parity, and cannot be.</b> Swing's engine drops {@code border-radius},
 * flexbox, grid, {@code gap} and {@code :hover} silently, so the layout here is tables and the tiles
 * have square corners. That is the reason {@link cafe.jeffrey.ide.plugin.idea.recording.web.CefPanelRenderer}
 * exists; the point of keeping this one is that an IDE without Chromium still gets a working tab
 * rather than a message explaining why it has none.
 *
 * <p>Hybrid on purpose: the body is markup in two {@link JEditorPane}s, and the buttons stay real
 * Swing between them, because this engine has no way to draw a button that does not look drawn.
 */
public final class SwingPanelRenderer implements PanelRenderer {

    private static final int PANEL_INSET = 20;
    private static final int SECTION_GAP = 16;
    private static final int BUTTON_GAP = 8;
    private static final int PROGRESS_WIDTH = 210;

    private final PanelActions actions;
    private final Path file;
    private final JPanel content = new JBPanel<>();
    private final JBScrollPane scroll;

    /** The last thing drawn, so a look-and-feel change can rebuild the stylesheet from the new theme. */
    private Runnable lastRender = () -> {
    };

    public SwingPanelRenderer(PanelActions actions, Path file) {
        this.actions = actions;
        this.file = file;

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(JBUI.Borders.empty(PANEL_INSET));
        content.setOpaque(false);

        this.scroll = new JBScrollPane(content);
        scroll.setBorder(JBUI.Borders.empty());
    }

    @Override
    public JComponent component() {
        return scroll;
    }

    @Override
    public void render(RecordingState state) {
        draw(() -> {
            String url = JeffreySettings.getInstance().microscopeUrl();
            add(htmlPane(PanelHtml.header(state, file, url)));
            add(gap());
            add(buttonsFor(state));
            add(gap());
            add(htmlPane(PanelHtml.details(state, file, url)));
        });
    }

    @Override
    public void showLoading() {
        draw(() -> add(htmlPane(
                "<html><body><span class='sml'>Asking Microscope about this file…</span></body></html>")));
    }

    @Override
    public void showFailure(String message) {
        draw(() -> {
            add(htmlPane("<html><body><span class='big'>The analysis did not finish</span><br>"
                    + "<span class='warn'>" + Html.escape(message) + "</span></body></html>"));
            add(gap());
            add(buttonRow(retryButton(), settingsButton()));
        });
    }

    @Override
    public void themeChanged() {
        lastRender.run();
    }

    private void draw(Runnable sections) {
        lastRender = () -> {
            content.removeAll();
            sections.run();
            content.add(Box.createVerticalGlue());
            content.revalidate();
            content.repaint();
        };
        lastRender.run();
    }

    // --- buttons --------------------------------------------------------------------------------

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
        column.add(buttonRow(button("Check again", event -> actions.checkAgain())));
        return leftAligned(column);
    }

    /**
     * Open in Microscope, then one button per known agent.
     *
     * <p>A flat row rather than the CEF renderer's split button: Swing has a split button, and wiring
     * one here would be new code in the fallback for a row that is two or three buttons wide on the
     * machines that see it. An agent that is not installed keeps its button, disabled, so the row
     * looks the same everywhere.
     */
    private JButton[] readyButtons(RecordingState state) {
        boolean heapDump = state.summary() != null && state.summary().isHeapDump();

        List<JButton> buttons = new ArrayList<>();
        buttons.add(button("Open in Microscope", event -> actions.openProfile()));
        if (JeffreySettings.getInstance().areAgentsEnabled()) {
            for (AgentCli agent : AgentCli.ALL) {
                buttons.add(agentButton(agent, heapDump));
            }
        }
        return buttons.toArray(new JButton[0]);
    }

    private JButton agentButton(AgentCli agent, boolean heapDump) {
        JButton button = button("Analyse with " + agent.displayName(), event -> actions.launchAgent(agent));
        if (!agent.isInstalled()) {
            button.setEnabled(false);
        }
        return button;
    }

    private JButton analyzeButton() {
        return button("Analyze in Microscope", event -> actions.analyze());
    }

    private JButton retryButton() {
        return button("Try again", event -> actions.retry());
    }

    private JButton settingsButton() {
        return button("Settings…", event -> actions.openSettings());
    }

    private JButton button(String text, ActionListener onClick) {
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

    // --- the HTML surface -----------------------------------------------------------------------

    /**
     * A read-only pane wearing the platform's HTML kit, with the panel's icons registered and the
     * stylesheet built from the current theme.
     *
     * <p>Every link's {@code href} is a Microscope view path, so one listener serves the whole
     * document.
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
                actions.openView(event.getDescription());
            }
        });
        return leftAligned(pane);
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
