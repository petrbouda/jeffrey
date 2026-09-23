/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        for (String key : List.of("flame", "heap", "index", "warn", "offline", "chevron")) {
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
