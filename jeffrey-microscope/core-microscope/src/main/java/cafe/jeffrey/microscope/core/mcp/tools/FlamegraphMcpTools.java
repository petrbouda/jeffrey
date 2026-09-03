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
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.PanelContext;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ProfilingStartEnd;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.shared.common.model.time.TimeRange;
import cafe.jeffrey.shared.common.model.time.UndefinedTimeRange;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

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

    private final ProfileManager profileManager;
    private final JfrFlamegraphPanelProvider panelProvider;

    public FlamegraphMcpTools(ProfileManager profileManager, JfrFlamegraphPanelProvider panelProvider) {
        this.profileManager = profileManager;
        this.panelProvider = panelProvider;
    }

    @Tool(description = "List the flamegraphs available for this profile: which event types were "
            + "actually recorded, with their sample and weight totals, and the options each graph is "
            + "normally drawn with. Call this before flamegraph_export to learn the valid eventType "
            + "values for this profile.")
    public String list() {
        List<FlamegraphPanel> panels = panelProvider.panels(
                profileManager.flamegraphManager().eventSummaries(), PanelContext.PRIMARY);
        if (panels.isEmpty()) {
            return NOTHING_TO_GRAPH;
        }
        return McpToolOutput.json(panels);
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
}
