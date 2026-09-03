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

import cafe.jeffrey.flamegraph.ai.AiExportConfig;
import cafe.jeffrey.profile.common.config.GraphComponents;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.shared.common.GraphType;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.model.FlamegraphPanel;
import cafe.jeffrey.profile.model.WeightKind;
import cafe.jeffrey.profile.model.WeightOption;
import cafe.jeffrey.profile.panel.FlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.PanelContext;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ProfilingStartEnd;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.shared.common.model.time.TimeRange;
import cafe.jeffrey.shared.common.model.time.UndefinedTimeRange;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;

/**
 * Flamegraphs of one profile, rendered as the Markdown export rather than as the protobuf the browser
 * draws — the same call tree, written to be read.
 * <p>
 * {@link #list()} comes first on purpose: which event types a recording actually captured varies by
 * profiler configuration, and asking for a graph of an event type that was never recorded returns an
 * empty tree rather than an error. The list is the profile's own answer to "what can I graph".
 */
public class FlamegraphMcpTools {

    private static final String NOTHING_TO_GRAPH =
            "This profile has no flamegraph-capable event types. It may be a heap dump "
                    + "(use the heap_* tools) or a recording without execution samples.";

    private static final String UNIT_BYTES = "bytes";
    private static final String UNIT_NANOSECONDS = "nanoseconds";

    private final ProfileManager profileManager;
    private final JfrFlamegraphPanelProvider jfrPanelProvider;
    private final StackSampleFlamegraphPanelProvider stackSamplePanelProvider;

    public FlamegraphMcpTools(
            ProfileManager profileManager,
            JfrFlamegraphPanelProvider jfrPanelProvider,
            StackSampleFlamegraphPanelProvider stackSamplePanelProvider) {

        this.profileManager = profileManager;
        this.jfrPanelProvider = jfrPanelProvider;
        this.stackSamplePanelProvider = stackSamplePanelProvider;
    }

    @Tool(description = "List the flamegraphs this profile can actually produce: one entry per event "
            + "type it recorded, with its sample and weight totals and the argument defaults that "
            + "event type is normally graphed with. Call this before flamegraph_export to learn the "
            + "valid eventType values for this profile. 'notRecorded' names the standard groups this "
            + "recording is missing — a profiler-configuration finding to report, not an error.")
    public String list() {
        List<FlamegraphPanel> panels = panelProvider().panels(
                profileManager.flamegraphManager().eventSummaries(), PanelContext.PRIMARY);

        List<GraphableType> available = new ArrayList<>();
        List<MissingGroup> notRecorded = new ArrayList<>();
        for (FlamegraphPanel panel : panels) {
            // The JFR provider emits the full catalog of standard sections, filling the ones this
            // recording has no samples for with a zero-sample placeholder so the frontend grid stays
            // complete. Graphing a placeholder yields an empty tree, so it is reported as a gap in what
            // the profiler captured rather than offered as a valid eventType.
            if (panel.event().primary().samples() > 0) {
                available.add(GraphableType.from(panel));
            } else {
                notRecorded.add(new MissingGroup(panel.section(), panel.title()));
            }
        }

        if (available.isEmpty()) {
            return NOTHING_TO_GRAPH;
        }
        return McpToolOutput.json(new GraphableTypes(available, notRecorded));
    }

    /**
     * The grid this profile's format is drawn as — the same split the UI routes make. pprof and OTLP
     * carry their own sample dimensions rather than JFR event types, so running them through the JFR
     * catalog would report every dimension they do have as missing.
     */
    private FlamegraphPanelProvider panelProvider() {
        if (profileManager.info().eventSource().isFlamegraphOnlyImport()) {
            return stackSamplePanelProvider;
        }
        return jfrPanelProvider;
    }

    @Tool(description = "Export a flamegraph as Markdown for reading: a nested call tree where every "
            + "frame carries its total samples, its self samples and its JIT tier, plus a preamble "
            + "defining exactly what those numbers mean. This is the main tool for 'where does the time "
            + "go'. Frames below the prune threshold are dropped and their weight rolled up into the "
            + "parent, so the accounting stays exact.")
    public String export(
            @ToolParam(description = "JFR event type to graph, e.g. 'jdk.ExecutionSample' for CPU, "
                    + "'jdk.ObjectAllocationSample' for allocation, 'jdk.JavaMonitorEnter' for lock "
                    + "contention. Use flamegraph_list to see what this profile recorded.")
            String eventType,
            @ToolParam(description = "Only show frames at or above this percentage of total samples "
                    + "(exclusive range 0-100). Lower means more detail and a longer document; the "
                    + "configured default is used when omitted.")
            Double thresholdPct,
            @ToolParam(description = "Start of the window, in milliseconds from the beginning of the "
                    + "recording. Omit for the whole recording.")
            Long startMs,
            @ToolParam(description = "End of the window, in milliseconds from the beginning of the "
                    + "recording. Omit for the whole recording.")
            Long endMs,
            @ToolParam(description = "Split the graph per thread instead of aggregating all threads")
            Boolean threadMode,
            @ToolParam(description = "Weigh frames by the event's weight (bytes allocated, nanoseconds "
                    + "blocked) instead of by sample count")
            Boolean useWeight,
            @ToolParam(description = "Highlight frames matching this substring")
            String search,
            @ToolParam(description = "Drop samples of threads that were idle")
            Boolean excludeIdle,
            @ToolParam(description = "Drop samples that were not executing Java code")
            Boolean excludeNonJava) {

        Type type = requireEventType(eventType);
        GraphParameters params = GraphParameters.builder()
                .withEventType(type)
                .withTimeRange(timeRange(profileManager.info(), startMs, endMs))
                .withThreads(List.of())
                .withThreadMode(Boolean.TRUE.equals(threadMode))
                .withUseWeight(useWeight)
                .withExcludeNonJavaSamples(Boolean.TRUE.equals(excludeNonJava))
                .withExcludeIdleSamples(Boolean.TRUE.equals(excludeIdle))
                .withOnlyUnsafeAllocationSamples(false)
                .withParseLocation(true)
                .withGraphType(GraphType.PRIMARY)
                .withGraphComponents(GraphComponents.FLAMEGRAPH_ONLY)
                .withSearchPattern(search)
                .build();

        return McpToolOutput.capped(
                profileManager.flamegraphManager().generateAiExport(params, aiExportConfig(thresholdPct)));
    }

    /**
     * A per-call threshold override, or {@code null} to keep the one the server was configured with.
     * Validated here rather than deep in the generator so a bad argument fails as a bad argument.
     */
    private static AiExportConfig aiExportConfig(Double thresholdPct) {
        if (thresholdPct == null) {
            return null;
        }
        if (!(thresholdPct > 0.0 && thresholdPct < 100.0)) {
            throw new IllegalArgumentException(
                    "thresholdPct must be between 0 and 100 (exclusive): " + thresholdPct);
        }
        return new AiExportConfig(thresholdPct);
    }

    static Type requireEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        return Type.fromCode(eventType.trim());
    }

    /**
     * The window to graph. Both bounds are relative milliseconds, because an MCP client has the
     * recording's start from {@code profiles_get} but no reason to do epoch arithmetic.
     */
    static RelativeTimeRange timeRange(ProfileInfo profileInfo, Long startMs, Long endMs) {
        ProfilingStartEnd startEnd = profileInfo.profilingStartEnd();
        if (startMs == null && endMs == null) {
            return UndefinedTimeRange.INSTANCE.toRelativeTimeRange(startEnd);
        }
        long from = startMs == null ? 0L : startMs;
        long to = endMs == null ? profileInfo.duration().toMillis() : endMs;
        if (to <= from) {
            throw new IllegalArgumentException(
                    "endMs must be greater than startMs: startMs=" + from + " endMs=" + to);
        }
        return TimeRange.create(from, to, false).toRelativeTimeRange(startEnd);
    }

    /**
     * What this profile can be graphed by, and which of the standard groups it is missing. The panel
     * grid is a presentation model — it carries an accent color, a bootstrap icon class and a display
     * order — so it is projected down to the arguments a caller can act on rather than handed over
     * whole.
     */
    private record GraphableTypes(List<GraphableType> available, List<MissingGroup> notRecorded) {
    }

    /**
     * One graphable event type: the {@code eventType} argument to pass to {@code flamegraph_export},
     * what it weighs, and the argument values the UI draws it with by default.
     * <p>
     * {@code weight} and its unit are null together when weighing is meaningless for this event type
     * (execution samples, wall-clock), so a caller can never read a weight without knowing what it
     * counts.
     */
    private record GraphableType(
            String eventType,
            String label,
            String section,
            long samples,
            Long weight,
            String weightLabel,
            String weightUnit,
            boolean defaultUseWeight,
            boolean defaultThreadMode,
            boolean defaultExcludeIdle,
            boolean defaultExcludeNonJava) {

        static GraphableType from(FlamegraphPanel panel) {
            WeightOption weight = panel.weight();
            boolean weighable = weight.applicable();
            return new GraphableType(
                    panel.event().code(),
                    panel.title(),
                    panel.section(),
                    panel.event().primary().samples(),
                    weighable ? panel.event().primary().weight() : null,
                    weighable ? weight.label() : null,
                    weighable ? unitOf(weight.kind()) : null,
                    weight.defaultOn(),
                    panel.threadMode().defaultOn(),
                    panel.excludeIdle().defaultOn(),
                    panel.excludeNonJava().defaultOn());
        }

        private static String unitOf(WeightKind kind) {
            return switch (kind) {
                case BYTES -> UNIT_BYTES;
                case DURATION -> UNIT_NANOSECONDS;
            };
        }
    }

    /**
     * A standard group this recording captured nothing for — the profiler was not configured to
     * collect it. Worth reporting to the reader; not a valid {@code eventType}.
     */
    private record MissingGroup(String section, String label) {
    }
}
