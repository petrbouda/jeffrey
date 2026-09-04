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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.gc.GCEvent;
import cafe.jeffrey.profile.manager.model.gc.GCGenerationStats;
import cafe.jeffrey.profile.manager.model.gc.GCHeader;
import cafe.jeffrey.profile.manager.model.gc.GCOverviewData;
import cafe.jeffrey.profile.manager.model.gc.GCPauseBucket;
import cafe.jeffrey.shared.common.model.Type;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * The Garbage Collection dashboard: how much of the run the collector stopped the application for,
 * how that was distributed, and what asked for it.
 * <p>
 * Every pause figure here comes from {@code sumOfPauses} and {@code longestPause}, which is the whole
 * reason this exists as a tool. A model writing its own query reaches for the event's {@code duration}
 * instead, and for ZGC, Shenandoah and G1's concurrent cycles that duration spans phases the
 * application ran straight through — it reports pauses that never happened. The builders behind
 * {@link GCOverviewData} have made that distinction since long before any model saw the data.
 */
public record GcSection(ProfileManager profileManager) implements JvmSection {

    public static final String ID = "gc";

    private static final String TITLE = "Garbage Collection";

    /**
     * Longest single collections carried back. Enough to see whether the worst pauses are one outlier
     * or a habit, short enough that the answer is not mostly a table.
     */
    private static final int LONGEST_PAUSES_LIMIT = 10;

    private static final double NANOS_IN_MILLI = 1_000_000d;

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.GARBAGE_COLLECTION,
            Type.YOUNG_GARBAGE_COLLECTION,
            Type.OLD_GARBAGE_COLLECTION,
            Type.G1_GARBAGE_COLLECTION,
            Type.Z_YOUNG_GARBAGE_COLLECTION,
            Type.Z_OLD_GARBAGE_COLLECTION);

    private static final List<String> NEXT_STEPS = List.of(
            "No event in this section names the code that produced the garbage. For the call paths, "
                    + "flamegraph_export with eventType jdk.ObjectAllocationSample and useWeight true.",
            "Pauses that are not collections are in jvm_safepoints. A small budget here does not mean "
                    + "the application was not being stopped.",
            "What is retained rather than churned is a heap-dump question; profiles_features says whether "
                    + "this profile has one.");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public Set<Type> eventTypes() {
        return EVENT_TYPES;
    }

    @Override
    public List<String> nextSteps() {
        return NEXT_STEPS;
    }

    @Override
    public Object render() {
        GCOverviewData overview = profileManager.gcManager().overviewData();
        GCHeader header = overview.header();

        return new GcDashboard(
                profileManager.gcManager().garbageCollectorType().name(),
                pauseBudget(header),
                header.manualGCCalls().systemGCCalls(),
                header.manualGCCalls().diagnosticCommandCalls(),
                generations(overview.generationStats()),
                distribution(overview.pauseDistribution().buckets()),
                longestPauses(overview.longestPauses()));
    }

    private static PauseBudget pauseBudget(GCHeader header) {
        return new PauseBudget(
                header.totalCollections(),
                header.youngCollections(),
                header.oldCollections(),
                header.fullCollections(),
                millis(header.totalGcTime()),
                millis(header.maxPauseTime()),
                millis(header.p95PauseTime()),
                millis(header.p99PauseTime()),
                header.gcThroughput(),
                header.gcOverhead(),
                header.collectionFrequency(),
                header.totalMemoryFreed(),
                header.avgMemoryFreed());
    }

    private static List<Generation> generations(List<GCGenerationStats> stats) {
        return stats.stream()
                .map(stat -> new Generation(
                        stat.generation(),
                        stat.collections(),
                        millis(stat.totalTime()),
                        stat.avgPauseTime(),
                        stat.maxPauseTime(),
                        stat.totalMemoryFreed()))
                .toList();
    }

    private static List<PauseBucket> distribution(List<GCPauseBucket> buckets) {
        return buckets.stream()
                .map(bucket -> new PauseBucket(bucket.range(), bucket.count(), bucket.percentage()))
                .toList();
    }

    private static List<Collection> longestPauses(List<GCEvent> events) {
        return events.stream()
                .limit(LONGEST_PAUSES_LIMIT)
                .map(event -> new Collection(
                        event.getGcId(),
                        event.getCollectorName(),
                        event.getCause(),
                        event.getGenerationType() == null ? null : event.getGenerationType().name(),
                        millis(event.getSumOfPauses()),
                        millis(event.getLongestPause()),
                        event.getBeforeGC(),
                        event.getAfterGC(),
                        event.getFreed()))
                .toList();
    }

    private static double millis(long nanos) {
        return nanos / NANOS_IN_MILLI;
    }

    /**
     * @param collector          the collector Jeffrey detected from the collection events
     * @param pauseBudget        the stop-the-world total this recording paid, and how it was shaped
     * @param systemGcCalls      collections asked for by {@code System.gc()}
     * @param diagnosticGcCalls  collections asked for through a diagnostic command (jcmd)
     */
    private record GcDashboard(
            String collector,
            PauseBudget pauseBudget,
            int systemGcCalls,
            int diagnosticGcCalls,
            List<Generation> generations,
            List<PauseBucket> pauseDistribution,
            List<Collection> longestCollections) {
    }

    private record PauseBudget(
            int collections,
            int youngCollections,
            int oldCollections,
            int fullCollections,
            double totalPauseMillis,
            double longestPauseMillis,
            double p95PauseMillis,
            double p99PauseMillis,
            BigDecimal throughputPct,
            BigDecimal overheadPct,
            BigDecimal collectionsPerMinute,
            long totalFreedBytes,
            long avgFreedBytes) {
    }

    private record Generation(
            String generation,
            int collections,
            double totalPauseMillis,
            BigDecimal avgPauseMillis,
            BigDecimal maxPauseMillis,
            long freedBytes) {
    }

    private record PauseBucket(String range, long count, BigDecimal percentage) {
    }

    private record Collection(
            long gcId,
            String collector,
            String cause,
            String generation,
            double sumOfPausesMillis,
            double longestPauseMillis,
            long heapBeforeBytes,
            long heapAfterBytes,
            long freedBytes) {
    }
}
