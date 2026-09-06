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

import java.util.Map;

/**
 * The panel's icons, as inline SVG.
 *
 * <p>Hand-authored rather than reused from {@code AllIcons}, and that is forced rather than chosen.
 * The Swing renderer reaches platform icons through the HTML kit's {@code <icon src="key"/>}
 * extension, which is a Swing view factory and means nothing to Chromium; and pulling the platform's
 * own SVGs out by resource path would pin this plugin to internal paths that move between releases.
 *
 * <p>Every glyph is a 16-unit square drawn in {@code currentColor}, so one sprite serves both themes
 * and every tint the stylesheet applies. Keys match {@link cafe.jeffrey.ide.plugin.idea.recording.ProfileView#iconKey()}
 * exactly — {@code ProfileViewTest} holds that mapping total over both view lists.
 */
final class PanelSvg {

    private static final String FLAME =
            "<path d='M8 1.6s3 3.1 3 5.6c0 0-.2 1.1-1.2 1.6.9-.2 1.4-1.1 1.4-2.3 1.5 1.5 2.3 3 2.3 4.2"
            + "A5.5 5.5 0 0 1 8 14.4a5.5 5.5 0 0 1-5.5-3.7c0-2.2 1.3-4.2 2.9-5.7C6.7 3.8 8 2.7 8 1.6Z'"
            + " fill='none' stroke='currentColor' stroke-width='1.3' stroke-linejoin='round'/>";

    private static final String ANALYSIS =
            "<path d='M1.6 8S4.1 3.8 8 3.8 14.4 8 14.4 8 11.9 12.2 8 12.2 1.6 8 1.6 8Z'"
            + " fill='none' stroke='currentColor' stroke-width='1.3'/>"
            + "<circle cx='8' cy='8' r='1.9' fill='none' stroke='currentColor' stroke-width='1.3'/>";

    private static final String SUBSECOND =
            "<path d='M2.2 2.4h11.6v11.2H2.2z M6.1 2.4v11.2 M10 2.4v11.2 M2.2 6.1h11.6 M2.2 9.9h11.6'"
            + " fill='none' stroke='currentColor' stroke-width='1.2'/>";

    private static final String ALLOCATIONS =
            "<path d='M3.4 4.6h9.2v6.8H3.4z' fill='none' stroke='currentColor' stroke-width='1.3'/>"
            + "<path d='M5.8 4.6V2.4 M8 4.6V2.4 M10.2 4.6V2.4 M5.8 13.6v-2.2 M8 13.6v-2.2 M10.2 13.6v-2.2'"
            + " stroke='currentColor' stroke-width='1.2'/>";

    private static final String GC =
            "<path d='M2.6 4.4h10.8 M6 4.4V2.6h4v1.8 M4.2 4.4l.7 9.2h6.2l.7-9.2'"
            + " fill='none' stroke='currentColor' stroke-width='1.3' stroke-linejoin='round'/>";

    private static final String THREADS =
            "<path d='M2 4h5.4 M9.6 4H14 M2 8h9.4 M2 12h5.4 M9.6 12H14' stroke='currentColor' stroke-width='1.3'/>"
            + "<circle cx='8.5' cy='4' r='1.15' fill='currentColor'/>"
            + "<circle cx='12.5' cy='8' r='1.15' fill='currentColor'/>"
            + "<circle cx='8.5' cy='12' r='1.15' fill='currentColor'/>";

    private static final String JIT =
            "<path d='M9.3 1.7 4 8.6h3.4L6.7 14.3 12 7.1H8.6l.7-5.4Z'"
            + " fill='none' stroke='currentColor' stroke-width='1.3' stroke-linejoin='round'/>";

    private static final String EVENTS =
            "<path d='M2.2 3h11.6v10H2.2z M2.2 6.4h11.6 M6.1 6.4V13 M10 6.4V13'"
            + " fill='none' stroke='currentColor' stroke-width='1.2'/>";

    private static final String TRACES =
            "<rect x='2' y='3.2' width='7' height='2.6' rx='1.3' fill='currentColor'/>"
            + "<rect x='4.4' y='6.7' width='8.6' height='2.6' rx='1.3' fill='currentColor' opacity='.72'/>"
            + "<rect x='6.6' y='10.2' width='5.4' height='2.6' rx='1.3' fill='currentColor' opacity='.48'/>";

    private static final String WARN =
            "<path d='M8 2.3 14.5 13.4H1.5L8 2.3Z'"
            + " fill='none' stroke='currentColor' stroke-width='1.3' stroke-linejoin='round'/>"
            + "<path d='M8 6.3v3.1' stroke='currentColor' stroke-width='1.4' stroke-linecap='round'/>"
            + "<circle cx='8' cy='11.4' r='.85' fill='currentColor'/>";

    private static final String OFFLINE =
            "<circle cx='8' cy='8' r='6.2' fill='none' stroke='currentColor' stroke-width='1.3'/>"
            + "<path d='M3.6 3.6l8.8 8.8' stroke='currentColor' stroke-width='1.3'/>";

    private static final String CHEVRON =
            "<path d='M3.5 6.5 8 11l4.5-4.5' fill='none' stroke='currentColor' stroke-width='1.5'"
            + " stroke-linecap='round' stroke-linejoin='round'/>";

    /** Everything the markup can name. Unknown keys render nothing rather than throwing. */
    private static final Map<String, String> BY_KEY = Map.ofEntries(
            Map.entry("flame", FLAME),
            Map.entry("analysis", ANALYSIS),
            Map.entry("subsecond", SUBSECOND),
            Map.entry("allocations", ALLOCATIONS),
            Map.entry("gc", GC),
            Map.entry("threads", THREADS),
            Map.entry("jit", JIT),
            Map.entry("events", EVENTS),
            Map.entry("traces", TRACES),
            Map.entry("warn", WARN),
            Map.entry("offline", OFFLINE),
            Map.entry("chevron", CHEVRON));

    private PanelSvg() {
    }

    /** Whether a key is drawable — the hook {@code PanelSvgTest} uses to hold the mapping total. */
    static boolean has(String key) {
        return BY_KEY.containsKey(key);
    }

    static String icon(String key, String cssClass) {
        String body = BY_KEY.get(key);
        if (body == null) {
            return "";
        }
        return "<svg class='" + cssClass + "' viewBox='0 0 16 16' aria-hidden='true'>" + body + "</svg>";
    }

    static String icon(String key) {
        return icon(key, "ico");
    }
}
