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

import cafe.jeffrey.ide.plugin.idea.recording.ProfileView;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * The icon sprite must cover every view the panel can draw.
 *
 * <p>The sibling of {@code ProfileViewTest.referencesOnlyRegisteredIcons}, which holds the same
 * promise for the Swing renderer's icon map. A key with no glyph renders nothing at all — a tile with
 * a hole where its icon should be, and no exception to notice.
 */
public class PanelSvgTest {

    @Test
    public void everyRecordingViewHasAGlyph() {
        assertGlyphs(ProfileView.RECORDING);
    }

    @Test
    public void everyHeapViewHasAGlyph() {
        assertGlyphs(ProfileView.HEAP);
    }

    /** The glyphs the document uses directly, outside the tile grid. */
    @Test
    public void thePanelsOwnGlyphsExist() {
        for (String key : List.of("flame", "warn", "offline", "chevron")) {
            assertTrue("missing glyph: " + key, PanelSvg.has(key));
        }
    }

    @Test
    public void anUnknownKeyRendersNothingRatherThanThrowing() {
        assertTrue(PanelSvg.icon("no-such-icon").isEmpty());
    }

    @Test
    public void aGlyphIsTintableSvg() {
        String svg = PanelSvg.icon("flame");

        assertTrue(svg.startsWith("<svg class='ico'"));
        assertTrue("must inherit the CSS colour", svg.contains("currentColor"));
        assertTrue(svg.contains("viewBox='0 0 16 16'"));
    }

    private static void assertGlyphs(List<ProfileView> views) {
        for (ProfileView view : views) {
            assertTrue("no glyph for " + view.label() + " (" + view.iconKey() + ")",
                    PanelSvg.has(view.iconKey()));
        }
    }
}
