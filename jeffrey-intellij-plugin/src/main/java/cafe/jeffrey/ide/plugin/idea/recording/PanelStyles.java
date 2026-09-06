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

import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.text.html.StyleSheet;
import java.awt.Color;

/**
 * The panel's stylesheet, built from the IDE's current theme.
 *
 * <p>Swing's HTML engine has no custom properties, so the colours are baked in as hex at render time
 * and the sheet is rebuilt when the look and feel changes. That is the one real tax of rendering the
 * panel as markup, and it is about twenty lines.
 *
 * <p>Deliberately does not style {@code a}: the platform's own kit already colours links with the
 * theme's link colour and handles the hover underline, and overriding that is how a plugin ends up
 * with links that are the wrong blue in one theme.
 */
final class PanelStyles {

    /** Font sizes, in points, relative to nothing — Swing's HTML engine wants absolute sizes here. */
    private static final int TITLE_PT = 15;
    private static final int FIGURE_PT = 15;
    private static final int SMALL_PT = 11;

    private PanelStyles() {
    }

    static StyleSheet current() {
        String text = hex(UIUtil.getLabelForeground());
        String secondary = hex(UIUtil.getContextHelpForeground());
        String dim = hex(UIUtil.getLabelDisabledForeground());
        String border = hex(JBColor.border());
        String alarm = hex(JBColor.namedColor("Label.errorForeground", JBColor.RED));

        StyleSheet sheet = new StyleSheet();
        add(sheet, "body", "color:" + text + "; margin:0;");
        add(sheet, "td", "vertical-align:top;");
        add(sheet, "hr", "color:" + border + ";");

        add(sheet, ".big", "font-size:" + TITLE_PT + "pt; font-weight:bold;");
        add(sheet, ".num", "font-size:" + FIGURE_PT + "pt; font-weight:bold;");
        add(sheet, ".alarm", "color:" + alarm + ";");
        add(sheet, ".sml", "font-size:" + SMALL_PT + "pt; color:" + secondary + ";");
        add(sheet, ".hd", "font-size:" + SMALL_PT + "pt; font-weight:bold; color:" + secondary + ";");
        add(sheet, ".warn", "color:" + alarm + ";");
        add(sheet, ".off", "color:" + dim + ";");
        add(sheet, ".mono", "font-family:monospace;");

        // cellspacing supplies the gutter between tiles; the negative margin pulls the grid back flush
        // with the text above it, since the engine has no gap and no way to drop the outer spacing.
        add(sheet, ".tiles", "margin-left:-" + JBUI.scale(6) + "px;");
        add(sheet, ".tile", "border:1px solid " + border + "; padding:"
                + JBUI.scale(7) + "px " + JBUI.scale(9) + "px;");
        // Square corners, and nothing to be done: the engine drops border-radius silently.
        add(sheet, ".tile.off", "border:1px dashed " + border + ";");
        return sheet;
    }

    private static void add(StyleSheet sheet, String selector, String rules) {
        sheet.addRule(selector + " { " + rules + " }");
    }

    private static String hex(Color color) {
        return "#" + ColorUtil.toHex(color);
    }
}
