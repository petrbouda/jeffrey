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
 * straight off a file — the rest are one hop away in Microscope's own sidebar, which is where
 * choosing among forty of them belongs.
 *
 * <p>Two lists, because a heap dump and a recording answer different questions. Nothing in
 * {@link #RECORDING} means anything for a dump — there is no flame graph of a heap — and nothing in
 * {@link #HEAP} means anything for a recording. Offering one list for both would fill half the grid
 * with tiles that open empty pages.
 *
 * @param iconKey the key this view's icon is registered under with the HTML kit, used as the
 *                {@code src} of an {@code <icon>} tag
 * @param label   what the panel calls it
 * @param blurb   one line saying what the page answers — the reason the tiles exist rather than a
 *                list of names, since "dominator tree" means nothing to a reader new to Microscope
 * @param path    the route's own sub-path under {@code /profiles/{profileId}/}
 * @param feature the {@code FeatureType} name that gates it, or null when the view is always offered
 */
public record ProfileView(String iconKey, String label, String blurb, String path, String feature) {

    /** How many tiles sit in a row. Three fits the blurbs without the labels wrapping. */
    public static final int COLUMNS = 3;

    /** Where the auto-analysis findings the panel lists come from, for the link beside them. */
    public static final ProfileView AUTO_ANALYSIS =
            new ProfileView("analysis", "Auto-analysis", "What Jeffrey flagged", "auto-analysis", null);

    /** A JFR, pprof or OTLP recording. */
    public static final List<ProfileView> RECORDING = List.of(
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

    /**
     * A heap dump. Leak suspects leads because it is the verdict — the closest thing a dump has to
     * auto-analysis, and the answer to the question that made someone take the dump.
     */
    public static final List<ProfileView> HEAP = List.of(
            new ProfileView("analysis", "Leak suspects", "What is holding the memory", "heap-dump/leak-suspects", null),
            new ProfileView("allocations", "Biggest objects", "The largest retained graphs", "heap-dump/biggest-objects", null),
            new ProfileView("traces", "Dominator tree", "What keeps what alive", "heap-dump/dominator-tree", null),
            new ProfileView("events", "Histogram", "Instances and bytes per class", "heap-dump/histogram", null),
            new ProfileView("gc", "GC roots", "Why objects survive collection", "heap-dump/gc-roots", null),
            new ProfileView("threads", "Threads", "Stacks and what they retain", "heap-dump/threads", null),
            new ProfileView("jit", "Class loaders", "Loaders and what survived a redeploy", "heap-dump/class-loader-analysis", null),
            new ProfileView("flame", "Collections", "Wasted capacity in collections", "heap-dump/collection-analysis", null),
            new ProfileView("subsecond", "OQL", "Query the heap directly", "heap-dump/oql", null));

    /**
     * Whether this profile has data behind the view.
     *
     * <p>An ungated view is always available — which is the honest answer, not an optimistic one:
     * Microscope gates only a handful of features, so most of these tiles are offered whether or not
     * the profile has anything behind them. Traces is the one that is commonly absent.
     */
    public boolean isAvailable(List<String> disabledFeatures) {
        return feature == null || !disabledFeatures.contains(feature);
    }

    /** What the tile says when the profile has no data for it. */
    public String unavailableBlurb() {
        return "Not in this recording";
    }
}
