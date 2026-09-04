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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.custom.model.method.MethodStats;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTimingData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingHeader;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingOverviewData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingSlowestData;
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

    private static final String TIMESERIES_VIEW = "technologies/method-tracing/timeseries";
    private static final String SLOWEST_VIEW = "technologies/method-tracing/slowest";
    private static final String TIMING_VIEW = "technologies/method-tracing/timing";

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

    private static final String STEP_SLOWEST =
            "Whether a method's cost is spread evenly or concentrated in a few pathological calls is "
                    + "methodtracing_slowest.";
    private static final String STEP_TIMING =
            "The JVM's own per-method aggregates - count with min, average and max - are in "
                    + "methodtracing_timing, and survive when per-invocation traces were not recorded.";
    private static final String STEP_CALLERS =
            "These are the instrumented methods themselves, not who called them. For the callers, "
                    + "flamegraph_export with eventType jdk.MethodTrace.";
    private static final String STEP_OVERVIEW =
            "The ranked view of the same data - by invocation count and by total time - is in "
                    + "methodtracing_overview.";

    private final ProfileManager profileManager;

    public MethodTracingMcpTools(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Tool(description = "The method-tracing dashboard: total invocations, duration percentiles and the "
            + "number of distinct methods, plus the methods ranked by invocation count and by total "
            + "time. Use it to see which instrumented method dominates - the by-duration and by-count "
            + "rankings disagreeing is the usual sign of a cheap method called far too often.")
    public String overview() {
        if (DashboardFeature.missing(profileManager, FeatureType.METHOD_TRACING_DASHBOARD)) {
            return NO_METHOD_TRACING_DATA;
        }

        MethodTracingOverviewData data = profileManager.custom().methodTracingManager().overview();
        if (data.header().totalInvocations() == 0) {
            return NO_TRACE_INVOCATIONS;
        }

        return LinkedOutput.json(new MethodTracingDashboard(
                data.header(),
                data.topMethodsByCount(),
                data.topMethodsByDuration(),
                NextSteps.builder().add(STEP_SLOWEST).add(STEP_TIMING).add(STEP_CALLERS).build(),
                UiLinks.view(profileId(), TIMESERIES_VIEW)));
    }

    @Tool(description = "The slowest individual method invocations, each with the thread it ran on. "
            + "Use it after methodtracing_overview to see whether a method's cost is spread evenly or "
            + "concentrated in a few pathological calls.")
    public String slowest() {
        if (DashboardFeature.missing(profileManager, FeatureType.METHOD_TRACING_DASHBOARD)) {
            return NO_METHOD_TRACING_DATA;
        }

        MethodTracingSlowestData data = profileManager.custom().methodTracingManager().slowest();
        if (data.slowestTraces().isEmpty()) {
            return NO_TRACE_INVOCATIONS;
        }

        return LinkedOutput.json(new SlowestResult(
                data,
                NextSteps.builder().add(STEP_OVERVIEW).add(STEP_CALLERS).build(),
                UiLinks.view(profileId(), SLOWEST_VIEW)));
    }

    @Tool(description = "Per-method timing statistics as the JVM aggregated them: invocation count with "
            + "minimum, average and maximum duration for each method. This is the jdk.MethodTiming "
            + "half of method tracing and can be present when no per-invocation traces were recorded.")
    public String timing() {
        if (DashboardFeature.missing(profileManager, FeatureType.METHOD_TRACING_DASHBOARD)) {
            return NO_METHOD_TRACING_DATA;
        }

        MethodTimingData data = profileManager.custom().methodTracingManager().methodTiming();
        if (data.methods().isEmpty()) {
            return NO_TIMING_STATISTICS;
        }

        return LinkedOutput.json(new TimingResult(
                data,
                NextSteps.builder().add(STEP_OVERVIEW).add(STEP_CALLERS).build(),
                UiLinks.view(profileId(), TIMING_VIEW)));
    }

    private String profileId() {
        return profileManager.info().id();
    }

    /**
     * The overview minus its two chart series.
     */
    private record MethodTracingDashboard(
            MethodTracingHeader header,
            List<MethodStats> topMethodsByCount,
            List<MethodStats> topMethodsByDuration,
            List<String> nextSteps,
            String uiLink) {
    }

    private record SlowestResult(
            MethodTracingSlowestData slowest, List<String> nextSteps, String uiLink) {
    }

    private record TimingResult(MethodTimingData timing, List<String> nextSteps, String uiLink) {
    }
}
