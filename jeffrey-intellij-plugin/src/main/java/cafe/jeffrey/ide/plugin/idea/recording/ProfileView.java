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

import java.util.List;

/**
 * One Microscope view the panel offers, as a tile.
 *
 * <p>Curated rather than a mirror of every route, for the same reason {@code ProfileMcpTools.VIEWS}
 * is: a name here is a promise that the page answers something. These are the ones worth a click
 * straight off a recording — the rest are one hop away in Microscope's own sidebar, which is where
 * choosing among forty of them belongs.
 *
 * @param iconKey  the key this view's icon is registered under with the HTML kit, used as the
 *                 {@code src} of an {@code <icon>} tag
 * @param label    what the panel calls it
 * @param blurb    one line saying what the page answers — the reason the tiles exist rather than a
 *                 list of names, since "sub-second" means nothing to a reader new to Microscope
 * @param path     the route's own sub-path under {@code /profiles/{profileId}/}
 * @param feature  the {@code FeatureType} name that gates it, or null when the view is always
 *                 offered. Only two of these views are gated at all; see {@link #isAvailable}
 */
public record ProfileView(String iconKey, String label, String blurb, String path, String feature) {

    /** Where the auto-analysis findings the panel lists come from, for the link beside them. */
    public static final ProfileView AUTO_ANALYSIS =
            new ProfileView("analysis", "Auto-analysis", "What Jeffrey flagged", "auto-analysis", null);

    /**
     * In the order the tiles are laid out — three rows of three, reading left to right. The gated two
     * are last in their rows on purpose: a dimmed tile at the end of a row reads as an absence, one in
     * the middle reads as a hole.
     */
    public static final List<ProfileView> ALL = List.of(
            new ProfileView("flame", "Flame graph", "Where the samples landed", "flamegraphs/primary", null),
            AUTO_ANALYSIS,
            new ProfileView("subsecond", "Sub-second", "Second-by-second heatmap", "subsecond/primary", "SUBSECOND"),
            new ProfileView("allocations", "Allocations", "What the run allocated", "allocations", null),
            new ProfileView("gc", "Garbage collection", "Pauses and collector work", "garbage-collection", null),
            new ProfileView("threads", "Threads", "Per-thread time and states", "thread-statistics", null),
            new ProfileView("jit", "JIT compilation", "What the compiler did", "jit-compilation", null),
            new ProfileView("events", "Event types", "Everything recorded", "event-types", null),
            // The traces feature's own landing page. Its other routes — attribute search, values,
            // latency — are hops from here rather than entries of their own, the way the sidebar has it.
            new ProfileView("traces", "Traces", "Spans and operations", "traces/operations", "TRACES"));

    /** How many tiles sit in a row. Three fits the blurbs without the labels wrapping. */
    public static final int COLUMNS = 3;

    /**
     * Whether this profile has data behind the view.
     *
     * <p>An ungated view is always available — which is the honest answer, not an optimistic one:
     * Microscope gates only a handful of features, so most of these tiles are offered whether or not
     * the recording has anything behind them. Traces is the one that is commonly absent, and it is the
     * reason this check exists at all.
     */
    public boolean isAvailable(List<String> disabledFeatures) {
        return feature == null || !disabledFeatures.contains(feature);
    }

    /** What the tile says when the recording has no data for it. */
    public String unavailableBlurb() {
        return "Not in this recording";
    }
}
