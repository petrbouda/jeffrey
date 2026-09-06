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

import cafe.jeffrey.ide.plugin.idea.recording.web.CefPanelRenderer;

import javax.swing.JComponent;

/**
 * How the panel's content gets drawn.
 *
 * <p>Two implementations, chosen once at construction. {@link CefPanelRenderer} renders the panel as
 * a real web document in the IDE's bundled Chromium, which is what buys grid, rounded corners, hover
 * states and a stylesheet that can be expressed in one place. {@link SwingPanelRenderer} is the older
 * pane wearing Swing's HTML kit, kept for the machines where {@code JBCefApp.isSupported()} says no —
 * a JBR without JCEF, and the JetBrains Client.
 *
 * <p>The fallback costs nothing to keep because it is the code that already existed. It is not held
 * to visual parity with the CEF renderer and never will be: Swing's engine drops border-radius,
 * flexbox and {@code :hover} silently, which is the whole reason the other one exists.
 *
 * <p><b>Not sealed</b>, though it has exactly two implementations and wants to be. A sealed type may
 * only permit subtypes in its own package unless both live in a named module, and the JCEF renderer
 * belongs in {@code recording.web} beside the document and stylesheet it is meaningless without.
 * Sealing it would mean flattening that package into this one, which is a worse trade than losing an
 * exhaustiveness check nothing switches over.
 */
public interface PanelRenderer {

    /** The component to put in the tab. Stable for the renderer's lifetime. */
    JComponent component();

    /** Draws whatever Microscope last said about the file. */
    void render(RecordingState state);

    /** Drawn while the first request is in flight. */
    void showLoading();

    /** Drawn when asking Microscope to analyse the file threw. */
    void showFailure(String message);

    /**
     * Redraws the current content against a changed look and feel.
     *
     * <p>Both renderers bake theme colours into their stylesheet at render time — neither engine has
     * anything like a live custom property that follows the IDE — so a theme switch has to be a
     * re-render, and the renderer is the only thing that still knows what it was showing.
     */
    void themeChanged();
}
