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

import cafe.jeffrey.flamegraph.export.AiExportConfig;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.model.GraphType;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.microscope.model.time.UndefinedTimeRange;
import cafe.jeffrey.profile.common.config.GraphComponents;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.profile.mcp.ToolParamValues;
import cafe.jeffrey.profile.model.FlamegraphPanel;
import cafe.jeffrey.profile.model.WeightKind;
import cafe.jeffrey.profile.model.WeightOption;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Flamegraphs of one profile, rendered as the Markdown export rather than as the protobuf the browser
 * draws — the same call tree, written to be read.
 * <p>
 * {@link #list()} comes first on purpose: which event types a recording actually captured varies by
 * profiler configuration, and asking for a graph of an event type that was never recorded returns an
 * empty tree rather than an error. The list is the profile's own answer to "what can I graph".
 * <p>
 * The export's Markdown stays the text the agent reads. Beside it, the structured record says what
 * the tree was built from — event type, window, threshold, detail, filters — with the next calls and
 * the Microscope page that draws the same graph for the user.
 */
public class FlamegraphMcpTools {

    private static final MicroscopeView GRID_VIEW = MicroscopeView.FLAMEGRAPHS_PRIMARY;

    /** The tools and arguments this family's answers hand back as next calls. */
    private static final String EXPORT_TOOL = "flamegraph_export";
    private static final String HOT_WINDOWS_TOOL = "timeline_hotWindows";
    private static final String THREADS_TOOL = "jvm_threads";
    private static final String FEATURES_TOOL = "profiles_features";
    private static final String PROFILE_ID = "profileId";
    private static final String EVENT_TYPE = "eventType";
    private static final String THREAD_MODE = "threadMode";
    private static final String USE_WEIGHT = "useWeight";
    private static final String EXCLUDE_IDLE = "excludeIdle";
    private static final String EXCLUDE_NON_JAVA = "excludeNonJava";
    private static final String START_EPOCH_MS = "startEpochMs";
    private static final String END_EPOCH_MS = "endEpochMs";

    private static final String EXPORT_WHY =
            "draws this event type at the defaults listed beside it; any eventType in available works the same way";
    private static final String FEATURES_WHY = "says what this profile holds instead";
    private static final String THREADS_WHY = "names the threads that burned this; a flamegraph aggregates across them";
    private static final String PER_THREAD_WHY = "the same graph split per thread";
    private static final String HOT_WINDOWS_WHY =
            "finds when these samples landed: the window to re-export with startEpochMs and endEpochMs";
    private static final String COMPARE_GUIDANCE =
            "Whether this is worse than it was is not visible in one profile: compare_list with a baseline "
                    + "profile's id, then compare_movements.";

    private static final String NOTHING_TO_GRAPH =
            "This profile has no flamegraph-capable event types. It may be a heap dump "
                    + "(use the heap_* tools) or a recording without execution samples.";

    /** The threshold {@code detail: full} prunes at, finer than the configured default. */
    private static final double FULL_DETAIL_THRESHOLD_PCT = 0.5;

    private static final String UNIT_BYTES = "bytes";
    private static final String UNIT_NANOSECONDS = "nanoseconds";

    private final ProfileManager profileManager;
    private final FlamegraphCatalog catalog;
    private final AdvertisedFamilies advertised;

    public FlamegraphMcpTools(
            ProfileManager profileManager,
            JfrFlamegraphPanelProvider jfrPanelProvider,
            StackSampleFlamegraphPanelProvider stackSamplePanelProvider,
            AdvertisedFamilies advertised) {

        this.profileManager = profileManager;
        this.catalog = new FlamegraphCatalog(profileManager, jfrPanelProvider, stackSamplePanelProvider);
        this.advertised = advertised;
    }

    @Tool(description = "Returns the flamegraphs this profile can produce: one entry per event type it "
            + "recorded - the valid eventType values for flamegraph_export - with its sample and "
            + "weight totals and the argument defaults that type is normally graphed with. "
            + "'notRecorded' names the standard groups this recording is missing: a "
            + "profiler-configuration finding, not an error. status EMPTY: nothing in this profile "
            + "can be graphed, and reason says why.")
    @McpOutputSchema(GraphableTypes.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult list() {
        String profileId = profileManager.info().id();
        String uiLink = UiLinks.view(profileId, GRID_VIEW);
        // A placeholder panel graphs to an empty tree, so the catalog's split decides what is offered
        // as a valid eventType and what is reported as a gap in what the profiler captured.
        List<GraphableType> available = catalog.recorded().stream()
                .map(GraphableType::from)
                .toList();
        List<MissingGroup> notRecorded = catalog.notRecorded().stream()
                .map(panel -> new MissingGroup(panel.section(), panel.title()))
                .toList();

        if (available.isEmpty()) {
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(McpNextTool.call(FEATURES_TOOL).with(PROFILE_ID, profileId).why(FEATURES_WHY))
                    .followUp();
            return McpToolResult.of(new GraphableTypes(
                    CatalogueStatus.EMPTY, NOTHING_TO_GRAPH, profileId, available, notRecorded, followUp, uiLink));
        }

        GraphableType first = available.getFirst();
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(McpNextTool.call(EXPORT_TOOL)
                        .with(PROFILE_ID, profileId)
                        .with(EVENT_TYPE, first.eventType())
                        .with(USE_WEIGHT, first.defaultUseWeight())
                        .with(THREAD_MODE, first.defaultThreadMode())
                        .with(EXCLUDE_IDLE, first.defaultExcludeIdle())
                        .with(EXCLUDE_NON_JAVA, first.defaultExcludeNonJava())
                        .why(EXPORT_WHY))
                .followUp();
        return McpToolResult.of(new GraphableTypes(
                CatalogueStatus.OK, null, profileId, available, notRecorded, followUp, uiLink));
    }


    @Tool(description = "Exports a flamegraph as Markdown for reading: a nested call tree where every "
            + "frame carries its total samples, its self samples and its JIT tier, under a preamble "
            + "defining exactly what those numbers mean. The main tool for 'where does the time go'. "
            + "Frames below the prune threshold are dropped and their weight rolled up into the "
            + "parent, so the accounting stays exact. detail 'summary' answers in a few kilobytes "
            + "with the top 25 frames by self and the top 10 paths; 'standard' (the default) is the "
            + "tree at the configured default threshold (2% unless "
            + "jeffrey.microscope.ai-export.flamegraph.min-frame-threshold-pct changes it); 'full' is "
            + "the tree at 0.5%, several times longer. The Markdown is the text; the structured "
            + "result records the event type, window, threshold and filters it was built with, and "
            + "uiLink opens the same graph in Microscope. flamegraph_list names the event types "
            + "this profile can graph.")
    @McpOutputSchema(FlamegraphExport.class)
    @McpToolMeta(maxResultSizeChars = McpToolOutput.MAX_CHARS, cost = McpToolCost.MODERATE)
    public McpToolResult export(
            @ToolParam(required = true, description = "JFR event type to graph, e.g. "
                    + "'jdk.ExecutionSample' for CPU, 'jdk.ObjectAllocationSample' for allocation, "
                    + "'jdk.JavaMonitorEnter' for lock contention. flamegraph_list names what this "
                    + "profile recorded.")
            String eventType,
            @ToolParam(required = false, description = "How much to hand back: SUMMARY (top 25 frames "
                    + "by self and top 10 paths, no tree), STANDARD (the call tree at the "
                    + "configured threshold, the default) or FULL (the call tree at 0.5%). On a "
                    + "large profile FULL can exceed the 120 000-character cap and come back "
                    + "truncated; STANDARD or a coarser thresholdPct fits. An explicit "
                    + "thresholdPct overrides the threshold STANDARD and FULL imply.")
            @ToolParamValues({"SUMMARY", "STANDARD", "FULL"})
            String detail,
            @ToolParam(required = false, description = "Only show frames at or above this percentage of total samples "
                    + "(exclusive range 0-100). Lower means more detail and a longer document; the "
                    + "configured default (2.0 unless changed) is used when omitted. Ignored by "
                    + "detail SUMMARY.")
            @ToolParamBounds(min = 0, max = 100, exclusiveMin = true, exclusiveMax = true)
            Double thresholdPct,
            @ToolParam(required = false, description = "Start of the window, as UTC epoch milliseconds inside the "
                    + "recording (profiles_get gives its span; timeline_hotWindows gives windows). Omit, "
                    + "with endEpochMs, for the whole recording.")
            Long startEpochMs,
            @ToolParam(required = false, description = "End of the window, as UTC epoch milliseconds inside the "
                    + "recording. Omit for the recording's own end.")
            Long endEpochMs,
            @ToolParam(required = false, description = "Split the graph per thread instead of aggregating all threads")
            Boolean threadMode,
            @ToolParam(required = false, description = "Weigh frames by the event's weight (bytes allocated, nanoseconds "
                    + "blocked) instead of by sample count. Omitted, the event type's default: weighed "
                    + "for allocation and blocking, counted otherwise.")
            Boolean useWeight,
            @ToolParam(required = false, description = "Mark the frames whose name matches this pattern - a regular "
                    + "expression as the UI's flamegraph search reads it, or a literal substring such as "
                    + "'OrderService$Batch'. Each matching frame ends with «match», the header adds "
                    + "search_matches (the samples under the matches and their share), and every frame on a "
                    + "path to a match is kept even below thresholdPct. Samples are not filtered.")
            String search,
            @ToolParam(required = false, description = "Drop samples of threads that were idle")
            Boolean excludeIdle,
            @ToolParam(required = false, description = "Drop samples that were not executing Java code")
            Boolean excludeNonJava) {

        Type type = requireEventType(eventType);
        ProfileInfo info = profileManager.info();
        EpochWindow window = requestedWindow(info, startEpochMs, endEpochMs);
        // With no window the whole recording is graphed on the millisecond base the link names it on,
        // so the graph and the page opened from the link cannot differ by a sub-millisecond remainder.
        EpochWindow graphed = window != null
                ? window
                : RecordingSpan.of(info).map(RecordingSpan::whole).orElse(null);
        // Trimmed once, here: the graph searches, the record reports and the link opens the one value.
        String searchPattern = search == null || search.isBlank() ? null : search.strip();
        Detail level = Detail.of(detail);
        AiExportConfig config = level.config(thresholdPct);
        // Resolved here rather than left to the manager, so the graph, the link and every next call
        // are drawn with the one weighting the record reports.
        boolean weighted = useWeight != null ? useWeight : weightedByDefault(type);
        GraphParameters params = GraphParameters.builder()
                .withEventType(type)
                .withTimeRange(timeRange(info, graphed))
                .withThreads(List.of())
                .withThreadMode(Boolean.TRUE.equals(threadMode))
                .withUseWeight(weighted)
                .withExcludeNonJavaSamples(Boolean.TRUE.equals(excludeNonJava))
                .withExcludeIdleSamples(Boolean.TRUE.equals(excludeIdle))
                .withOnlyUnsafeAllocationSamples(false)
                .withParseLocation(true)
                .withGraphType(GraphType.PRIMARY)
                .withGraphComponents(GraphComponents.FLAMEGRAPH_ONLY)
                .withSearchPattern(searchPattern)
                .build();

        String markdown = profileManager.flamegraphManager().generateAiExport(params, config);
        // The whole recording is named too when no window was: without one the view opens on its first
        // hour, which on a longer recording is not the graph just described.
        String uiLink = FlamegraphViewLink.primary(info.id(), type)
                .weighted(weighted)
                .threadMode(Boolean.TRUE.equals(threadMode))
                .excludeIdle(Boolean.TRUE.equals(excludeIdle))
                .excludeNonJava(Boolean.TRUE.equals(excludeNonJava))
                .window(graphed)
                .search(searchPattern)
                .url();
        McpFollowUp followUp = followUp(info.id(), type, window, threadMode, weighted, excludeIdle, excludeNonJava);
        LinkedOutput.Footed text = LinkedOutput.footed(markdown, followUp, uiLink, null);

        FlamegraphExport export = new FlamegraphExport(
                info.id(),
                type.code(),
                level,
                level.threshold(config),
                window,
                Boolean.TRUE.equals(threadMode),
                weighted,
                Boolean.TRUE.equals(excludeIdle),
                Boolean.TRUE.equals(excludeNonJava),
                searchPattern,
                markdown.length(),
                text.truncated(),
                followUp,
                uiLink);
        return McpToolResult.of(text.text(), export);
    }

    /**
     * The weighting an export uses when the caller does not say: the default the flamegraph grid draws
     * this event type with, and for a type the grid does not list, the managers' own rule - weighed for
     * allocation and blocking, counted otherwise.
     */
    private boolean weightedByDefault(Type type) {
        return catalog.panels().stream()
                .filter(panel -> panel.event().code().equals(type.code()))
                .findFirst()
                .map(panel -> panel.weight().defaultOn())
                .orElseGet(() -> type.isAllocationEvent() || type.isBlockingEvent());
    }

    /**
     * Where a flamegraph leads: the threads behind it, the same graph split per thread, and - for a
     * whole-recording graph - the timeline that finds the window worth re-exporting. Each call carries
     * this answer's own event type, window and filters, so it is the same question asked one step
     * further.
     */
    private McpFollowUp followUp(
            String profileId, Type type, EpochWindow window,
            Boolean threadMode, boolean weighted, Boolean excludeIdle, Boolean excludeNonJava) {

        McpNextTool.Call perThread = McpNextTool.call(EXPORT_TOOL)
                .with(PROFILE_ID, profileId)
                .with(EVENT_TYPE, type.code())
                .with(THREAD_MODE, true);
        if (window != null) {
            perThread.with(START_EPOCH_MS, window.startEpochMs()).with(END_EPOCH_MS, window.endEpochMs());
        }
        perThread.with(USE_WEIGHT, weighted);
        if (Boolean.TRUE.equals(excludeIdle)) {
            perThread.with(EXCLUDE_IDLE, true);
        }
        if (Boolean.TRUE.equals(excludeNonJava)) {
            perThread.with(EXCLUDE_NON_JAVA, true);
        }

        McpNextTool.Call hotWindows = McpNextTool.call(HOT_WINDOWS_TOOL)
                .with(PROFILE_ID, profileId)
                .with(EVENT_TYPE, type.code())
                .with(USE_WEIGHT, weighted);

        return NextSteps.builder(advertised)
                .next(McpNextTool.call(THREADS_TOOL).with(PROFILE_ID, profileId).why(THREADS_WHY))
                .nextWhen(!Boolean.TRUE.equals(threadMode), perThread.why(PER_THREAD_WHY))
                .nextWhen(window == null, hotWindows.why(HOT_WINDOWS_WHY))
                .guidance(advertised.hint(AdvertisedFamilies.COMPARE, COMPARE_GUIDANCE))
                .followUp();
    }

    /**
     * A per-call threshold override, or {@code null} to keep the one the server was configured with.
     * Validated here rather than deep in the generator so a bad argument fails as a bad argument.
     * <p>
     * Shared with {@link CompareMcpTools}, whose differential export takes the same argument and has
     * to reject the same values — two copies of one range check is one copy too many to keep in step.
     */
    static AiExportConfig aiExportConfig(Double thresholdPct) {
        if (thresholdPct == null) {
            return null;
        }
        if (!(thresholdPct > 0.0 && thresholdPct < 100.0)) {
            throw new IllegalArgumentException(
                    "thresholdPct must be between 0 and 100 (exclusive): " + thresholdPct);
        }
        return new AiExportConfig(thresholdPct);
    }

    /**
     * The three answers {@code detail} picks between, each knowing the export configuration it stands
     * for. An explicit threshold beats the one a tree level implies: it is the more specific request.
     * On the wire the level is its constant name.
     */
    enum Detail {
        SUMMARY(thresholdPct -> AiExportConfig.summary(), false),
        STANDARD(FlamegraphMcpTools::aiExportConfig, true),
        FULL(thresholdPct -> aiExportConfig(thresholdPct == null ? FULL_DETAIL_THRESHOLD_PCT : thresholdPct), true);

        /** The export for an explicit threshold, or for {@code null} when none was asked for. */
        private final Function<Double, AiExportConfig> config;
        private final boolean prunes;

        Detail(Function<Double, AiExportConfig> config, boolean prunes) {
            this.config = config;
            this.prunes = prunes;
        }

        /** The export this level asks for, or {@code null} to keep the configured one. */
        AiExportConfig config(Double thresholdPct) {
            return config.apply(thresholdPct);
        }

        /**
         * The prune threshold the tree was cut at, when it was asked for; {@code null} for the
         * configured default and for a summary, which is not a pruned tree.
         */
        Double threshold(AiExportConfig export) {
            return prunes && export != null ? export.minFrameThresholdPct() : null;
        }

        /** Omitted or blank is the standard tree; the schema has already refused anything else. */
        static Detail of(String detail) {
            if (detail == null || detail.isBlank()) {
                return STANDARD;
            }
            return valueOf(detail.trim().toUpperCase(Locale.ROOT));
        }
    }

    static Type requireEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        return Type.fromCode(eventType.trim());
    }

    /**
     * The window a caller asked for, checked against the recording, or {@code null} for the whole
     * recording when neither bound was given.
     */
    private static EpochWindow requestedWindow(ProfileInfo info, Long startEpochMs, Long endEpochMs) {
        if (startEpochMs == null && endEpochMs == null) {
            return null;
        }
        return RecordingSpan.require(info).window(startEpochMs, endEpochMs);
    }

    /**
     * The range the managers graph: the window's offsets from the recording start, or the whole
     * recording when there is no window.
     */
    static RelativeTimeRange timeRange(ProfileInfo info, EpochWindow window) {
        if (window == null) {
            return UndefinedTimeRange.INSTANCE.toRelativeTimeRange(info.profilingStartEnd());
        }
        return RecordingSpan.require(info).offsets(window);
    }

    /**
     * What this profile can be graphed by, and which of the standard groups it is missing. The panel
     * grid is a presentation model — it carries an accent color, a bootstrap icon class and a display
     * order — so it is projected down to the arguments a caller can act on rather than handed over
     * whole.
     *
     * @param status EMPTY when nothing in this profile can be graphed; {@code reason} then says why
     */
    record GraphableTypes(
            CatalogueStatus status,
            @McpNullable
            @McpDescription("Why nothing can be graphed; null when status is OK")
            String reason,
            String profileId,
            List<GraphableType> available,
            List<MissingGroup> notRecorded,
            McpFollowUp followUp,
            @McpDescription("The profile's flamegraph grid in the Microscope UI, for the user")
            String uiLink) {
    }

    /**
     * One graphable event type: the {@code eventType} argument to pass to {@code flamegraph_export},
     * what it weighs, and the argument values the UI draws it with by default.
     * <p>
     * {@code weight} and its unit are null together when weighing is meaningless for this event type
     * (execution samples, wall-clock), so a caller can never read a weight without knowing what it
     * counts.
     */
    record GraphableType(
            String eventType,
            String label,
            String section,
            long samples,
            @McpNullable
            Long weight,
            @McpNullable
            String weightLabel,
            @McpNullable
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
    record MissingGroup(String section, String label) {
    }

    /**
     * What a flamegraph export was built from, beside the Markdown that is its text. The tree itself
     * is only in the text: the record is the metadata a caller can act on.
     *
     * @param window the window graphed; null when it was the whole recording
     */
    record FlamegraphExport(
            String profileId,
            String eventType,
            Detail detail,
            @McpNullable
            @McpDescription("The prune threshold asked for, in percent of the total; null for the "
                    + "configured default and for SUMMARY, which is not a pruned tree")
            Double thresholdPct,
            @McpNullable
            @McpDescription("The window graphed, as UTC epoch milliseconds; null for the whole recording")
            EpochWindow window,
            boolean threadMode,
            @McpDescription("Whether frames are weighed rather than counted, as the export resolved it: the "
                    + "event type's default when the call did not say")
            boolean useWeight,
            boolean excludeIdle,
            boolean excludeNonJava,
            @McpNullable
            @McpDescription("The pattern whose frames are marked «match»; null when none was given")
            String search,
            @McpDescription("Characters of the Markdown export, before the text cap")
            int markdownChars,
            @McpDescription("Whether the Markdown was cut to fit the text cap with its footer; a coarser "
                    + "detail or thresholdPct fits")
            boolean truncated,
            McpFollowUp followUp,
            @McpDescription("The same graph in the Microscope flamegraph view - same event type, flags, window "
                    + "and search - for the user")
            String uiLink) {
    }
}
