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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.tools.NextSteps;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.gc.GCEvent;
import cafe.jeffrey.profile.manager.model.gc.GCGenerationStats;
import cafe.jeffrey.profile.manager.model.gc.GCHeader;
import cafe.jeffrey.profile.manager.model.gc.GCOverviewData;
import cafe.jeffrey.profile.manager.model.gc.GCPauseBucket;
import cafe.jeffrey.profile.mcp.McpFollowUp;

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
public record GcSection(ProfileManager profileManager) implements JvmSection<GcSection.GcDashboard> {

    public static final String ID = "gc";

    private static final String TITLE = "Garbage Collection";

    /**
     * Longest single collections carried back. Enough to see whether the worst pauses are one outlier
     * or a habit, short enough that the answer is not mostly a table.
     */
    private static final int LONGEST_PAUSES_LIMIT = 10;

    private static final double NANOS_IN_MILLI = 1_000_000d;

    private static final String UNKNOWN_COLLECTOR = "UNKNOWN";

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.GARBAGE_COLLECTION,
            Type.YOUNG_GARBAGE_COLLECTION,
            Type.OLD_GARBAGE_COLLECTION,
            Type.G1_GARBAGE_COLLECTION,
            Type.Z_YOUNG_GARBAGE_COLLECTION,
            Type.Z_OLD_GARBAGE_COLLECTION);

    private static final String ALLOCATION_PATHS_WHY =
            "names the code that produced the garbage, which no event in this section does";
    private static final String SAFEPOINTS_WHY =
            "shows the pauses that are not collections; a small budget here does not mean the "
                    + "application was not being stopped";
    private static final String FEATURES_WHY =
            "says whether this profile has a heap dump, for what is retained rather than churned";

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
    public MicroscopeView view() {
        return MicroscopeView.GARBAGE_COLLECTION;
    }

    @Override
    public void followUp(NextSteps.Builder next, GcDashboard dashboard) {
        String profileId = profileManager.info().id();
        SectionCalls.allocationPaths(next, profileManager, ALLOCATION_PATHS_WHY);
        next.next(SectionCalls.on(SectionCalls.JVM_SAFEPOINTS, profileId).why(SAFEPOINTS_WHY))
                .next(SectionCalls.on(SectionCalls.PROFILES_FEATURES, profileId).why(FEATURES_WHY));
    }

    @Override
    public GcDashboard render() {
        GCOverviewData overview = profileManager.gcManager().overviewData();
        GCHeader header = overview.header();

        return new GcDashboard(
                profileManager.gcManager().garbageCollectorType()
                        .map(GarbageCollectorType::name)
                        .orElse(UNKNOWN_COLLECTOR),
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
                decimal(header.gcThroughput()),
                decimal(header.gcOverhead()),
                decimal(header.collectionFrequency()),
                header.totalMemoryFreed(),
                header.avgMemoryFreed());
    }

    private static List<Generation> generations(List<GCGenerationStats> stats) {
        return stats.stream()
                .map(stat -> new Generation(
                        stat.generation(),
                        stat.collections(),
                        millis(stat.totalTime()),
                        decimal(stat.avgPauseTime()),
                        decimal(stat.maxPauseTime()),
                        stat.totalMemoryFreed()))
                .toList();
    }

    private static List<PauseBucket> distribution(List<GCPauseBucket> buckets) {
        return buckets.stream()
                .map(bucket -> new PauseBucket(bucket.range(), bucket.count(), decimal(bucket.percentage())))
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

    /** A figure the builders compute exactly, as the JSON number the schema declares; null stays null. */
    private static Double decimal(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    /**
     * @param collector          the collector Jeffrey detected from the collection events
     * @param pauseBudget        the stop-the-world total this recording paid, and how it was shaped
     * @param systemGcCalls      collections asked for by {@code System.gc()}
     * @param diagnosticGcCalls  collections asked for through a diagnostic command (jcmd)
     */
    public record GcDashboard(
            String collector,
            PauseBudget pauseBudget,
            int systemGcCalls,
            int diagnosticGcCalls,
            List<Generation> generations,
            List<PauseBucket> pauseDistribution,
            @McpDescription("The " + LONGEST_PAUSES_LIMIT + " longest collections by sum of pauses")
            List<Collection> longestCollections) {
    }

    public record PauseBudget(
            int collections,
            int youngCollections,
            int oldCollections,
            int fullCollections,
            double totalPauseMs,
            double longestPauseMs,
            double p95PauseMs,
            double p99PauseMs,
            @McpNullable
            Double throughputPct,
            @McpNullable
            Double overheadPct,
            @McpNullable
            Double collectionsPerMinute,
            long totalFreedBytes,
            long avgFreedBytes) {
    }

    public record Generation(
            @McpNullable
            String generation,
            int collections,
            double totalPauseMs,
            @McpNullable
            Double avgPauseMs,
            @McpNullable
            Double maxPauseMs,
            long freedBytes) {
    }

    public record PauseBucket(
            @McpNullable
            String range,
            long count,
            @McpNullable
            Double percentage) {
    }

    public record Collection(
            long gcId,
            @McpNullable
            String collector,
            @McpNullable
            String cause,
            @McpNullable
            String generation,
            double sumOfPausesMs,
            double longestPauseMs,
            long heapBeforeBytes,
            long heapAfterBytes,
            long freedBytes) {
    }

    /**
     * What {@code jvm_gc} answers: the envelope every section shares, around this section's dashboard.
     */
    public record Answer(
            SectionStatus status,
            @McpNullable
            @McpDescription(SectionHeader.REASON)
            String reason,
            String profileId,
            @McpDescription(SectionHeader.SECTION)
            String section,
            String title,
            @McpNullable
            @McpDescription(SectionHeader.DASHBOARD)
            GcDashboard dashboard,
            McpFollowUp followUp,
            @McpDescription(SectionHeader.UI_LINK)
            String uiLink) {

        public static Answer of(SectionHeader header, GcDashboard dashboard) {
            return new Answer(header.status(), header.reason(), header.profileId(), header.section(),
                    header.title(), dashboard, header.followUp(), header.uiLink());
        }
    }
}
