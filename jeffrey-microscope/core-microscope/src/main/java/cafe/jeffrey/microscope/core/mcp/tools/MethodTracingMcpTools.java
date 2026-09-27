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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.custom.model.method.MethodStats;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTimingData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTimingStat;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingHeader;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingOverviewData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingSlowestData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingSlowestHeader;
import cafe.jeffrey.profile.manager.custom.model.method.SlowestMethodTrace;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;

/**
 * Instrumented method timings - JEP 520 method tracing, not distributed tracing. For request-level
 * spans see the {@code traces_} family.
 * <p>
 * Two independent event types back this, and a recording routinely has one without the other:
 * {@code METHOD_TRACE} carries per-invocation durations and feeds the overview and the slowest list,
 * while {@code METHOD_TIMING} carries pre-aggregated per-method statistics. The dashboard is enabled
 * when either exists, so each tool has to say which half is empty rather than report zeros as a
 * measurement.
 */
public class MethodTracingMcpTools {

    private static final MicroscopeView TIMESERIES_VIEW = MicroscopeView.METHOD_TRACING_TIMESERIES;
    private static final MicroscopeView SLOWEST_VIEW = MicroscopeView.METHOD_TRACING_SLOWEST;
    private static final MicroscopeView TIMING_VIEW = MicroscopeView.METHOD_TRACING_TIMING;

    /** The JVM's own tally has no bound of its own, so the most-invoked methods are kept. */
    private static final int MAX_TIMED_METHODS = 100;

    private static final String NO_METHOD_TRACING_DATA =
            "This profile holds no method-tracing data: the recording captured neither jdk.MethodTrace "
                    + "nor jdk.MethodTiming events. Method tracing has to be enabled and given a filter "
                    + "before a recording starts.";

    /**
     * Deliberately phrased as what was measured rather than as "the profile has no jdk.MethodTrace
     * events": the aggregate can come back empty for a recording that does hold those events, and a
     * message asserting their absence would then be checkably wrong.
     */
    private static final String NO_TRACE_INVOCATIONS =
            "Method tracing aggregated no per-invocation data for this profile - no jdk.MethodTrace "
                    + "invocations were counted, so there are no per-method durations and no slowest "
                    + "calls to rank. The pre-aggregated jdk.MethodTiming half, if the recording has "
                    + "one, is what methodtracing_timing returns.";

    private static final String NO_TIMING_STATISTICS =
            "Method tracing aggregated no jdk.MethodTiming statistics for this profile. "
                    + "Per-invocation data, if the recording has any, is in methodtracing_overview.";

    private static final String SLOWEST_WHY =
            "whether a method's cost is spread evenly or concentrated in a few pathological calls";
    private static final String TIMING_WHY =
            "the JVM's own per-method aggregates, which survive when per-invocation traces were not recorded";
    private static final String CALLERS_WHY = "who called the instrumented methods";
    private static final String OVERVIEW_WHY = "the methods ranked by invocation count and by total time";
    private static final String CALLERS_GUIDANCE =
            "These are the instrumented methods themselves, not who called them. For the callers, "
                    + "flamegraph_export with eventType jdk.MethodTrace, when the recording carries it.";

    private final ProfileManager profileManager;
    private final AdvertisedFamilies advertised;

    public MethodTracingMcpTools(ProfileManager profileManager, AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.advertised = advertised;
    }

    @Tool(description = "Returns the method-tracing dashboard: total invocations, duration percentiles "
            + "in nanoseconds and the number of distinct methods, plus the methods ranked by "
            + "invocation count and by total time - which instrumented method dominates. The "
            + "by-duration and by-count rankings disagreeing is the usual sign of a cheap method "
            + "called far too often. status NOT_RECORDED: no method-tracing event was recorded, or "
            + "no per-invocation jdk.MethodTrace data was aggregated; reason says which.")
    @McpOutputSchema(MethodTracingDashboard.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult overview() {
        String uiLink = UiLinks.view(profileId(), TIMESERIES_VIEW);
        if (nothingRecorded()) {
            return McpToolResult.of(new MethodTracingDashboard(DashboardStatus.NOT_RECORDED, NO_METHOD_TRACING_DATA,
                    profileId(), null, List.of(), List.of(), noFollowUp(), uiLink));
        }

        MethodTracingOverviewData data = profileManager.custom().methodTracingManager().overview();
        if (data.header().totalInvocations() == 0) {
            return McpToolResult.of(new MethodTracingDashboard(DashboardStatus.NOT_RECORDED, NO_TRACE_INVOCATIONS,
                    profileId(), null, List.of(), List.of(), timingOnly(), uiLink));
        }

        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(call(FollowUpCalls.METHOD_TRACING_SLOWEST).why(SLOWEST_WHY))
                .next(call(FollowUpCalls.METHOD_TRACING_TIMING).why(TIMING_WHY))
                .next(callers())
                .followUp();
        return McpToolResult.of(new MethodTracingDashboard(DashboardStatus.OK, null, profileId(),
                Totals.of(data.header()),
                data.topMethodsByCount().stream().map(MethodRow::of).toList(),
                data.topMethodsByDuration().stream().map(MethodRow::of).toList(),
                followUp,
                uiLink));
    }

    @Tool(description = "Returns the slowest individual method invocations, slowest first, each with "
            + "its duration in nanoseconds and the thread it ran on - whether a method's cost is "
            + "spread evenly or concentrated in a few pathological calls. status NOT_RECORDED: no "
            + "method-tracing event was recorded, or no per-invocation data was aggregated.")
    @McpOutputSchema(SlowestInvocations.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult slowest() {
        String uiLink = UiLinks.view(profileId(), SLOWEST_VIEW);
        if (nothingRecorded()) {
            return McpToolResult.of(new SlowestInvocations(DashboardStatus.NOT_RECORDED, NO_METHOD_TRACING_DATA,
                    profileId(), null, List.of(), noFollowUp(), uiLink));
        }

        MethodTracingSlowestData data = profileManager.custom().methodTracingManager().slowest();
        if (data.slowestTraces().isEmpty()) {
            return McpToolResult.of(new SlowestInvocations(DashboardStatus.NOT_RECORDED, NO_TRACE_INVOCATIONS,
                    profileId(), null, List.of(), timingOnly(), uiLink));
        }

        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(call(FollowUpCalls.METHOD_TRACING_OVERVIEW).why(OVERVIEW_WHY))
                .next(callers())
                .followUp();
        return McpToolResult.of(new SlowestInvocations(DashboardStatus.OK, null, profileId(),
                SlowestSummary.of(data.header()),
                data.slowestTraces().stream().map(Invocation::of).toList(),
                followUp,
                uiLink));
    }

    @Tool(description = "Returns per-method timing statistics as the JVM aggregated them: invocation "
            + "count with minimum, average and maximum duration in nanoseconds for each method, the "
            + "most invoked 100 kept and omittedMethods counting the rest. This is the "
            + "jdk.MethodTiming half of method tracing and can be present when no per-invocation "
            + "traces were recorded. status NOT_RECORDED: no method-tracing event was recorded, or "
            + "no jdk.MethodTiming statistics were aggregated.")
    @McpOutputSchema(MethodTiming.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult timing() {
        String uiLink = UiLinks.view(profileId(), TIMING_VIEW);
        if (nothingRecorded()) {
            return McpToolResult.of(new MethodTiming(DashboardStatus.NOT_RECORDED, NO_METHOD_TRACING_DATA,
                    profileId(), null, List.of(), null, noFollowUp(), uiLink));
        }

        MethodTimingData data = profileManager.custom().methodTracingManager().methodTiming();
        if (data.methods().isEmpty()) {
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(call(FollowUpCalls.METHOD_TRACING_OVERVIEW).why(OVERVIEW_WHY))
                    .followUp();
            return McpToolResult.of(new MethodTiming(DashboardStatus.NOT_RECORDED, NO_TIMING_STATISTICS,
                    profileId(), null, List.of(), null, followUp, uiLink));
        }

        List<MethodTimingStat> shown = ToolArguments.firstOf(data.methods(), MAX_TIMED_METHODS);
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(call(FollowUpCalls.METHOD_TRACING_OVERVIEW).why(OVERVIEW_WHY))
                .guidance(advertised.hint(AdvertisedFamilies.FLAMEGRAPH, CALLERS_GUIDANCE))
                .followUp();
        return McpToolResult.of(new MethodTiming(DashboardStatus.OK, null, profileId(), data.totalInvocations(),
                shown.stream().map(TimedMethod::of).toList(), data.methods().size() - shown.size(), followUp, uiLink));
    }

    private boolean nothingRecorded() {
        return DashboardFeature.missing(profileManager, FeatureType.METHOD_TRACING_DASHBOARD);
    }

    /** Neither half was recorded, so there is no call that would find more; the reason says what to record. */
    private McpFollowUp noFollowUp() {
        return NextSteps.builder(advertised).followUp();
    }

    /** The per-invocation half is empty; the aggregated half may not be. */
    private McpFollowUp timingOnly() {
        return NextSteps.builder(advertised)
                .next(call(FollowUpCalls.METHOD_TRACING_TIMING).why(TIMING_WHY))
                .followUp();
    }

    private McpNextTool callers() {
        return call(FollowUpCalls.FLAMEGRAPH_EXPORT)
                .with(FollowUpCalls.EVENT_TYPE, FollowUpCalls.METHOD_TRACE_EVENT)
                .why(CALLERS_WHY);
    }

    private McpNextTool.Call call(String tool) {
        return McpNextTool.call(tool).with(FollowUpCalls.PROFILE_ID, profileId());
    }

    private String profileId() {
        return profileManager.info().id();
    }

    /** Every invocation's totals, with every duration in nanoseconds. */
    record Totals(
            long totalInvocations,
            long totalDurationNanos,
            long maxDurationNanos,
            long p99DurationNanos,
            long p95DurationNanos,
            long avgDurationNanos,
            long uniqueMethodCount) {

        static Totals of(MethodTracingHeader header) {
            return new Totals(header.totalInvocations(), header.totalDuration(), header.maxDuration(),
                    header.p99Duration(), header.p95Duration(), header.avgDuration(), header.uniqueMethodCount());
        }
    }

    /** One method in a ranking; its method name is null when the event did not carry one. */
    record MethodRow(
            String className,
            @McpNullable
            String methodName,
            long invocationCount,
            long totalDurationNanos,
            long avgDurationNanos,
            long maxDurationNanos,
            @McpDescription("This method's share of all traced time, in percent")
            double percentOfTotal) {

        static MethodRow of(MethodStats stats) {
            return new MethodRow(stats.className(), Figures.name(stats.methodName()), stats.invocationCount(),
                    stats.totalDuration(), stats.avgDuration(), stats.maxDuration(), stats.percentOfTotal());
        }
    }

    record SlowestSummary(long p99DurationNanos, long p95DurationNanos, long uniqueMethodCount) {

        static SlowestSummary of(MethodTracingSlowestHeader header) {
            return new SlowestSummary(header.p99Duration(), header.p95Duration(), header.uniqueMethodCount());
        }
    }

    /** One invocation; its method and thread are null when the event did not name them. */
    record Invocation(
            String className,
            @McpNullable
            String methodName,
            long durationNanos,
            @McpNullable
            String threadName) {

        static Invocation of(SlowestMethodTrace trace) {
            String thread = SlowestMethodTrace.UNKNOWN_THREAD.equals(trace.threadName()) ? null : trace.threadName();
            return new Invocation(trace.className(), Figures.name(trace.methodName()), trace.duration(), thread);
        }
    }

    /** One method's aggregate as the JVM recorded it; the builder labels a method without a class. */
    record TimedMethod(
            String className,
            String methodName,
            long invocations,
            long minNanos,
            long avgNanos,
            long maxNanos) {

        static TimedMethod of(MethodTimingStat stat) {
            return new TimedMethod(stat.className(), stat.methodName(), stat.invocations(), stat.minNanos(),
                    stat.avgNanos(), stat.maxNanos());
        }
    }

    /** The overview minus its two chart series. */
    record MethodTracingDashboard(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there is no dashboard, and which half is missing; null when status is OK")
            String reason,
            String profileId,
            @McpNullable
            @McpDescription("Every invocation's totals; null when status is NOT_RECORDED")
            Totals header,
            List<MethodRow> topMethodsByCount,
            List<MethodRow> topMethodsByDuration,
            McpFollowUp followUp,
            @McpDescription("The method-tracing time series in the Microscope UI, for the user")
            String uiLink) {
    }

    record SlowestInvocations(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there are no invocations, and which half is missing; null when status is OK")
            String reason,
            String profileId,
            @McpNullable
            @McpDescription("The percentiles the ranking sits in; null when status is NOT_RECORDED")
            SlowestSummary header,
            @McpDescription("The slowest invocations, slowest first, as many as the profile keeps")
            List<Invocation> invocations,
            McpFollowUp followUp,
            @McpDescription("The slowest-invocations page in the Microscope UI, for the user")
            String uiLink) {
    }

    record MethodTiming(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there are no statistics, and which half is missing; null when status is OK")
            String reason,
            String profileId,
            @McpNullable
            @McpDescription("Invocations the JVM counted across every method; null when status is NOT_RECORDED")
            Long totalInvocations,
            @McpDescription("The most invoked methods, most first, at most 100")
            List<TimedMethod> methods,
            @McpNullable
            @McpDescription("Methods left out of methods by its cap of 100; null when status is NOT_RECORDED")
            Integer omittedMethods,
            McpFollowUp followUp,
            @McpDescription("The method-timing page in the Microscope UI, for the user")
            String uiLink) {
    }
}
