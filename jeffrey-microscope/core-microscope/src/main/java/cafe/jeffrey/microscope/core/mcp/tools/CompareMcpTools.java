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

import cafe.jeffrey.flamegraph.export.WeightContext;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.model.GraphType;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.common.config.GraphComponents;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.DifferentialFlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.profile.model.EventSummaryResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Two profiles of the same application, compared — the question a reader in their own repository
 * actually has, which no single-profile tool can answer.
 * <p>
 * The one family that is scoped to a <em>pair</em>. The toolset resolves the {@code profileId} as
 * usual — it is the run under examination — and {@code baselineProfileId} names the run it is measured
 * against, resolved here. Sign convention throughout: positive means the primary spends more, so a
 * positive delta is a regression.
 * <p>
 * {@link #list()} comes first for a reason that is sharper here than elsewhere. Two recordings can
 * always be subtracted, and the result always looks like a finding; whether it <em>is</em> one depends
 * on facts about the recordings — comparable length, comparable volume, the same profiler settings —
 * that the deltas themselves do not show. Establishing that first is not a formality, it is the
 * difference between an analysis and a plausible fiction.
 * <p>
 * A window is given on the primary's clock, in UTC epoch milliseconds, and applied at the same offset
 * into both recordings: two runs of a before/after comparison happened at different times, so the
 * same instant cannot lie in both, but the same phase — the first ten seconds, the minute after
 * warm-up — can. Every windowed answer says where the window landed on each recording's own clock.
 */
public class CompareMcpTools {

    private static final int DEFAULT_MOVEMENT_LIMIT = 15;
    private static final int MAX_MOVEMENT_LIMIT = 100;

    /** Beyond this ratio the recordings are different enough that the reader should be told. */
    private static final double DURATION_NOTICE_RATIO = 1.25;

    /**
     * What the differential page cannot show of a ranking: it draws the graph the movements were ranked
     * from, not the ranking.
     */
    private static final String RANKING_LINK_NOTE =
            "the page draws the differential flamegraph this ranking was read from; the ranking itself is "
                    + "this answer's own";

    /**
     * What the differential page cannot show of a baseline read past the primary's window: the page
     * applies the primary's window to both recordings.
     */
    private static final String BASELINE_CUT_LINK_NOTE =
            "the page compares the baseline over the primary's %d ms window only; this answer read %d ms of "
                    + "the baseline, to its own end";

    private static final String LINK_NOTE_SEPARATOR = "; ";

    /** The tools and arguments this family's answers hand back as next calls. */
    private static final String QUALITY_TOOL = "compare_quality";
    private static final String MOVEMENTS_TOOL = "compare_movements";
    private static final String DIFF_TOOL = "compare_flamegraph";
    private static final String EXPORT_TOOL = "flamegraph_export";
    private static final String FEATURES_TOOL = "profiles_features";
    private static final String PROFILE_ID = "profileId";
    private static final String BASELINE_PROFILE_ID = "baselineProfileId";
    private static final String EVENT_TYPE = "eventType";
    private static final String START_EPOCH_MS = "startEpochMs";
    private static final String END_EPOCH_MS = "endEpochMs";
    private static final String USE_WEIGHT = "useWeight";
    private static final String EXCLUDE_IDLE = "excludeIdle";
    private static final String EXCLUDE_NON_JAVA = "excludeNonJava";

    private static final String QUALITY_WHY =
            "reports stored sampling settings, sample-loss evidence and workload-volume limits for this pair";
    private static final String MOVEMENTS_WHY = "ranks the methods that moved, on the first comparable event type";
    private static final String DRILL_WHY =
            "shows the call paths these movements travelled through, over the same window and filters";
    private static final String PRIMARY_WHOLE_WHY = "shows what the primary spends there in absolute terms";
    private static final String BASELINE_WHOLE_WHY = "shows what the baseline spent there in absolute terms";
    private static final String PRIMARY_FEATURES_WHY = "says what the primary recorded";
    private static final String BASELINE_FEATURES_WHY = "says what the baseline recorded";

    private static final String RENAME_GUIDANCE =
            "A method that appears on one side only may be a rename rather than a change. You have the "
                    + "source diff and the profile does not; check before reporting either half.";
    private static final String NOT_MOVED_GUIDANCE =
            "This tree prunes by movement, so a frame absent here did not move - it is not missing.";

    private static final String NOTHING_COMPARABLE =
            "These two profiles have no event type in common that can be compared. They may be "
                    + "different recording formats, one may be a heap dump, or they were captured with "
                    + "different profiler settings. profiles_features on each shows what it holds.";

    private static final String SAME_PROFILE =
            "profileId and baselineProfileId are the same profile. Pick two different runs to compare.";

    private static final String NOTE_DURATION_MISMATCH =
            "The recordings are of noticeably different length. Sample counts scale with recording "
                    + "time, so compare_movements will scale the baseline onto the primary's time base "
                    + "— valid only if both runs did the same kind of work at the same rate.";
    private static final String NOTE_ONLY_IN_PRIMARY =
            "Some event types were recorded only in the primary. That is a profiler-configuration "
                    + "difference between the two runs, not a change in the application.";
    private static final String NOTE_ONLY_IN_BASELINE =
            "Some event types were recorded only in the baseline. That is a profiler-configuration "
                    + "difference between the two runs, not a change in the application.";
    private static final String NOTE_NOT_COMPARABLE =
            "Some event types were recorded by both runs but are not among the types the differential "
                    + "tools compare. That is neither a configuration difference nor a change in the "
                    + "application; read them with the single-profile tools on each side.";

    private final ProfileManager primaryManager;
    private final Function<String, ProfileManager> baselineResolver;
    private final Clock clock;
    private final AdvertisedFamilies advertised;

    public CompareMcpTools(
            ProfileManager primaryManager,
            Function<String, ProfileManager> baselineResolver,
            Clock clock,
            AdvertisedFamilies advertised) {

        this.primaryManager = primaryManager;
        this.baselineResolver = baselineResolver;
        this.clock = clock;
        this.advertised = advertised;
    }

    @Tool(description = "Reports whether two profiles can be compared at all, and on which event types: "
            + "both recordings' span as UTC epoch milliseconds, the event types they have in common "
            + "with each side's sample and weight totals, and the event types only one of them "
            + "recorded - a profiler-configuration difference between the runs rather than a change "
            + "in the application. A comparison of recordings of very different length or volume "
            + "produces numbers that look precise and mean nothing. status EMPTY: nothing the two "
            + "have in common can be compared.")
    @McpOutputSchema(Comparability.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult list(
            @ToolParam(required = true, description = "Id of the profile to compare against — the baseline, normally "
                    + "the run from before the change. As listed by profiles_list.")
            String baselineProfileId) {

        ProfileManager baseline = baseline(baselineProfileId);
        List<EventSummaryResult> shared = diffManager(baseline).eventSummaries();

        List<ComparableType> comparable = shared.stream()
                .map(ComparableType::from)
                .toList();

        Set<String> comparableCodes = comparable.stream()
                .map(ComparableType::eventType)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Long> primarySamples = recordedSamples(primaryManager);
        Map<String, Long> baselineSamples = recordedSamples(baseline);
        List<String> onlyInPrimary = exclusiveTypes(primarySamples, baselineSamples, comparableCodes);
        List<String> onlyInBaseline = exclusiveTypes(baselineSamples, primarySamples, comparableCodes);
        List<String> recordedByBothNotComparable =
                sharedNotComparable(primarySamples, baselineSamples, comparableCodes);

        String primaryId = primaryManager.info().id();
        String baselineId = baseline.info().id();
        boolean nothing = comparable.isEmpty();
        McpFollowUp followUp = nothing
                ? NextSteps.builder(advertised)
                        .next(NextCalls.to(FEATURES_TOOL).with(PROFILE_ID, primaryId).why(PRIMARY_FEATURES_WHY))
                        .next(NextCalls.to(FEATURES_TOOL).with(PROFILE_ID, baselineId).why(BASELINE_FEATURES_WHY))
                        .followUp()
                : NextSteps.builder(advertised)
                        .next(quality(primaryId, baselineId))
                        .next(NextCalls.to(MOVEMENTS_TOOL)
                                .with(PROFILE_ID, primaryId)
                                .with(BASELINE_PROFILE_ID, baselineId)
                                .with(EVENT_TYPE, comparable.getFirst().eventType())
                                .why(MOVEMENTS_WHY))
                        .followUp();

        return McpToolResult.of(new Comparability(
                nothing ? CatalogueStatus.EMPTY : CatalogueStatus.OK,
                nothing ? NOTHING_COMPARABLE : null,
                side(primaryManager),
                side(baseline),
                comparable,
                onlyInPrimary,
                onlyInBaseline,
                recordedByBothNotComparable,
                notes(primaryManager.info(), baseline.info(),
                        onlyInPrimary, onlyInBaseline, recordedByBothNotComparable),
                followUp,
                pairLink(primaryId, baselineId)));
    }

    @Tool(description = "Ranks the methods that moved between two profiles: those the primary spends "
            + "more in (a regression) and those it spends less in. The main tool for 'did my change "
            + "make it slower'. Movements are attributed by SELF weight, so a change is charged to "
            + "the method that actually moved rather than to every caller above it, and the baseline "
            + "is scaled onto the primary's recording length. The Markdown ranking is the text and "
            + "opens with a comparability section that qualifies every finding below it; the "
            + "structured result records what was compared, including where a window landed on "
            + "each recording.")
    @McpOutputSchema(MovementRanking.class)
    @McpToolMeta(cost = McpToolCost.EXPENSIVE)
    public McpToolResult movements(
            @ToolParam(required = true, description = "Id of the profile to compare against — the baseline, normally "
                    + "the run from before the change.")
            String baselineProfileId,
            @ToolParam(required = true, description = "JFR event type to compare, e.g. "
                    + "'jdk.ExecutionSample' for CPU or 'jdk.ObjectAllocationSample' for allocation. "
                    + "compare_list names the types these two profiles have in common.")
            String eventType,
            @ToolParam(required = false, description = "How many movements to report in each direction (default "
                    + DEFAULT_MOVEMENT_LIMIT + ", maximum " + MAX_MOVEMENT_LIMIT + ")")
            @ToolParamBounds(defaultValue = DEFAULT_MOVEMENT_LIMIT, min = 1, max = MAX_MOVEMENT_LIMIT)
            Integer limit,
            @ToolParam(required = false, description = "Start of the window, as UTC epoch milliseconds inside the "
                    + "primary's recording; applied at the same offset from the start into the baseline's. "
                    + "Omit, with endEpochMs, for both whole recordings.")
            Long startEpochMs,
            @ToolParam(required = false, description = "End of the window, as UTC epoch milliseconds inside the "
                    + "primary's recording, at the same offset into the baseline's. Omit for each "
                    + "recording's own end.")
            Long endEpochMs,
            @ToolParam(required = false, description = "Compare by the event's weight (bytes allocated, nanoseconds "
                    + "blocked) instead of by sample count")
            Boolean useWeight,
            @ToolParam(required = false, description = "Drop samples of threads that were idle")
            Boolean excludeIdle,
            @ToolParam(required = false, description = "Drop samples that were not executing Java code")
            Boolean excludeNonJava) {

        ProfileManager baseline = baseline(baselineProfileId);
        Request request = new Request(FlamegraphMcpTools.requireEventType(eventType), startEpochMs, endEpochMs,
                useWeight, excludeIdle, excludeNonJava);
        PairWindow window = pairWindow(baseline, request);
        int rows = ToolArguments.boundedLimit(limit, DEFAULT_MOVEMENT_LIMIT, MAX_MOVEMENT_LIMIT);
        String markdown = diffManager(baseline).rankedMovements(params(request, window), rows);

        String primaryId = primaryManager.info().id();
        String baselineId = baseline.info().id();
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(quality(primaryId, baselineId))
                .next(request.applyTo(NextCalls.to(DIFF_TOOL)
                                .with(PROFILE_ID, primaryId)
                                .with(BASELINE_PROFILE_ID, baselineId))
                        .why(DRILL_WHY))
                .guidance(RENAME_GUIDANCE)
                .followUp();
        String uiLink = graphLink(baseline, request, window);
        String uiLinkNote = joinNotes(RANKING_LINK_NOTE, baselineCutNote(baseline, window));
        LinkedOutput.Footed text = LinkedOutput.footed(markdown, followUp, uiLink, uiLinkNote);
        return McpToolResult.of(text.text(), new MovementRanking(
                primaryId, baselineId, request.type().code(), rows, window.compared(),
                useWeight, request.excludesIdle(), request.excludesNonJava(),
                markdown.length(), text.truncated(),
                followUp, uiLink, uiLinkNote));
    }

    @Tool(description = "Exports the differential flamegraph of two profiles as Markdown: the merged "
            + "call tree, every frame carrying what the primary spends there, what the baseline "
            + "spent, and the movement between them - the call paths a method flagged by "
            + "compare_movements moved through. Subtrees in which nothing moved are pruned, so "
            + "absence here means 'did not change', not 'not present'. The Markdown is the text; "
            + "the structured result records what was compared, including where a window landed on "
            + "each recording.")
    @McpOutputSchema(DifferentialExport.class)
    @McpToolMeta(maxResultSizeChars = McpToolOutput.MAX_CHARS, cost = McpToolCost.EXPENSIVE)
    public McpToolResult flamegraph(
            @ToolParam(required = true, description = "Id of the profile to compare against — the baseline, normally "
                    + "the run from before the change.")
            String baselineProfileId,
            @ToolParam(required = true, description = "JFR event type to compare. compare_list names "
                    + "the types these two profiles have in common.")
            String eventType,
            @ToolParam(required = false, description = "Keep only subtrees containing a movement of at least this "
                    + "percentage of the primary's total (exclusive range 0-100). Lower means more "
                    + "detail and a longer document; the configured default is used when omitted.")
            @ToolParamBounds(min = 0, max = 100, exclusiveMin = true, exclusiveMax = true)
            Double thresholdPct,
            @ToolParam(required = false, description = "Start of the window, as UTC epoch milliseconds inside the "
                    + "primary's recording; applied at the same offset from the start into the baseline's. "
                    + "Omit, with endEpochMs, for both whole recordings.")
            Long startEpochMs,
            @ToolParam(required = false, description = "End of the window, as UTC epoch milliseconds inside the "
                    + "primary's recording, at the same offset into the baseline's. Omit for each "
                    + "recording's own end.")
            Long endEpochMs,
            @ToolParam(required = false, description = "Compare by the event's weight (bytes allocated, nanoseconds "
                    + "blocked) instead of by sample count")
            Boolean useWeight,
            @ToolParam(required = false, description = "Drop samples of threads that were idle")
            Boolean excludeIdle,
            @ToolParam(required = false, description = "Drop samples that were not executing Java code")
            Boolean excludeNonJava) {

        ProfileManager baseline = baseline(baselineProfileId);
        Request request = new Request(FlamegraphMcpTools.requireEventType(eventType), startEpochMs, endEpochMs,
                useWeight, excludeIdle, excludeNonJava);
        PairWindow window = pairWindow(baseline, request);
        String markdown = diffManager(baseline).generateAiExport(
                params(request, window), FlamegraphMcpTools.aiExportConfig(thresholdPct));

        String primaryId = primaryManager.info().id();
        String baselineId = baseline.info().id();
        ComparedWindow compared = window.compared();
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(quality(primaryId, baselineId))
                .next(request.plainExport(primaryId, compared == null ? null : compared.primary())
                        .why(PRIMARY_WHOLE_WHY))
                .next(request.plainExport(baselineId, compared == null ? null : compared.baseline())
                        .why(BASELINE_WHOLE_WHY))
                .guidance(NOT_MOVED_GUIDANCE)
                .guidance(RENAME_GUIDANCE)
                .followUp();
        String uiLink = graphLink(baseline, request, window);
        String uiLinkNote = baselineCutNote(baseline, window);
        LinkedOutput.Footed text = LinkedOutput.footed(markdown, followUp, uiLink, uiLinkNote);
        return McpToolResult.of(text.text(), new DifferentialExport(
                primaryId, baselineId, request.type().code(), thresholdPct, compared,
                useWeight, request.excludesIdle(), request.excludesNonJava(),
                markdown.length(), text.truncated(),
                followUp, uiLink, uiLinkNote));
    }

    @Tool(description = "Assesses the recording evidence behind a comparison: both identities and "
            + "durations, event overlap, stored sampling-setting differences, reported CPU-time "
            + "sample loss and observed server-event volumes. A whole-recording current-state "
            + "snapshot, with bounded complete records and omission counts. Reports per-workload "
            + "normalization unavailable when complete operation counts are not established.")
    @McpOutputSchema(ComparisonQuality.QualityDocument.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult quality(
            @ToolParam(required = true, description = "Baseline profile id, as listed by profiles_list")
            String baselineProfileId) {
        return ComparisonQuality.result(primaryManager, baseline(baselineProfileId), clock);
    }

    /** The pair's page for the user: the differential grid of the primary, opened on this baseline. */
    static String pairLink(String primaryProfileId, String baselineProfileId) {
        return FlamegraphViewLink.pair(primaryProfileId, baselineProfileId);
    }

    /**
     * The differential graph of this comparison: the baseline, the event type, the filters, the
     * weighting the comparison resolved, and the window on the primary's clock - the whole primary when
     * none was given, since without a window the view opens on its first hour.
     */
    private String graphLink(ProfileManager baseline, Request request, PairWindow window) {
        boolean weighted = request.useWeight() != null
                ? request.useWeight()
                : DifferentialFlamegraphManager.weightedByDefault(request.type());
        return FlamegraphViewLink.differential(primaryManager.info().id(), baseline.info().id(), request.type())
                .weighted(weighted)
                .excludeIdle(request.excludesIdle())
                .excludeNonJava(request.excludesNonJava())
                .window(primaryDrawn(window))
                .url();
    }

    /**
     * What the page leaves out of the baseline, or {@code null} when it shows all this answer read.
     * The page applies the primary's window at the same offsets to the baseline, so a baseline this
     * answer read on past the end of that window - an open end, or no window, on a longer baseline - is
     * cut there.
     */
    private String baselineCutNote(ProfileManager baseline, PairWindow window) {
        EpochWindow drawn = primaryDrawn(window);
        Long baselineRead = window.compared() != null
                ? lengthOf(window.compared().baseline())
                : RecordingSpan.of(baseline.info()).map(RecordingSpan::durationMs).orElse(null);
        if (drawn == null || baselineRead == null || baselineRead <= lengthOf(drawn)) {
            return null;
        }
        return BASELINE_CUT_LINK_NOTE.formatted(lengthOf(drawn), baselineRead);
    }

    /** The primary's window the page draws: the one compared, or the whole primary recording. */
    private EpochWindow primaryDrawn(PairWindow window) {
        if (window.compared() != null) {
            return window.compared().primary();
        }
        return RecordingSpan.of(primaryManager.info()).map(RecordingSpan::whole).orElse(null);
    }

    private static long lengthOf(EpochWindow window) {
        return window.endEpochMs() - window.startEpochMs();
    }

    private static String joinNotes(String first, String second) {
        return second == null ? first : first + LINK_NOTE_SEPARATOR + second;
    }

    private static McpNextTool quality(String primaryId, String baselineId) {
        return NextCalls.to(QUALITY_TOOL)
                .with(PROFILE_ID, primaryId)
                .with(BASELINE_PROFILE_ID, baselineId)
                .why(QUALITY_WHY);
    }

    /**
     * The window a comparison was asked for, placed on each recording's own clock at the same offset
     * from its start, and the offsets the differential manager reads. An open end stays open, so the
     * longer recording is not clipped to the shorter one's length.
     */
    private PairWindow pairWindow(ProfileManager baseline, Request request) {
        if (request.startEpochMs() == null && request.endEpochMs() == null) {
            return PairWindow.WHOLE;
        }
        RecordingSpan primarySpan = RecordingSpan.require(primaryManager.info());
        RecordingSpan baselineSpan = RecordingSpan.require(baseline.info());
        EpochWindow onPrimary = primarySpan.window(request.startEpochMs(), request.endEpochMs());
        long from = primarySpan.offsetOf(onPrimary.startEpochMs());
        Long to = request.endEpochMs() == null ? null : primarySpan.offsetOf(onPrimary.endEpochMs());
        EpochWindow onBaseline = baselineSpan.windowAt(from, to);
        return new PairWindow(
                new ComparedWindow(onPrimary, onBaseline),
                new RelativeTimeRange(Duration.ofMillis(from), to == null ? null : Duration.ofMillis(to)));
    }

    private ProfileManager baseline(String baselineProfileId) {
        if (baselineProfileId == null || baselineProfileId.isBlank()) {
            throw new IllegalArgumentException("baselineProfileId is required");
        }
        String trimmed = baselineProfileId.trim();
        if (trimmed.equals(primaryManager.info().id())) {
            throw new IllegalArgumentException(SAME_PROFILE);
        }
        return baselineResolver.apply(trimmed);
    }

    private DifferentialFlamegraphManager diffManager(ProfileManager baseline) {
        return primaryManager.diffFlamegraphManager(baseline);
    }

    private static GraphParameters params(Request request, PairWindow window) {
        return GraphParameters.builder()
                .withEventType(request.type())
                .withTimeRange(window.offsets())
                .withThreads(List.of())
                // Per-thread mode splits the tree by thread name, and thread names differ between two
                // runs (pool-1-thread-7 is not the same worker twice), so every branch would read as
                // appeared/vanished. A comparison is always thread-aggregated.
                .withThreadMode(false)
                .withUseWeight(request.useWeight())
                .withExcludeNonJavaSamples(request.excludesNonJava())
                .withExcludeIdleSamples(request.excludesIdle())
                .withOnlyUnsafeAllocationSamples(false)
                .withParseLocation(true)
                .withGraphType(GraphType.DIFFERENTIAL)
                .withGraphComponents(GraphComponents.FLAMEGRAPH_ONLY)
                .build();
    }

    /** Every event type one profile recorded, with its sample count, in the order the manager lists them. */
    private static Map<String, Long> recordedSamples(ProfileManager manager) {
        Map<String, Long> samples = new LinkedHashMap<>();
        for (EventSummaryResult summary : manager.flamegraphManager().eventSummaries()) {
            if (summary.primary().samples() > 0) {
                samples.put(summary.code(), summary.primary().samples());
            }
        }
        return samples;
    }

    /**
     * Event types one profile recorded and the other did not at all. Reported rather than dropped:
     * a type missing from one side is a difference between the two profiler configurations, and a
     * reader who does not know that will read its absence as the application no longer doing it.
     * <p>
     * "Recorded" is decided by the other side's sample count, not by the differential set: the
     * differential tools compare a handful of types, and a type both runs recorded that is not among
     * them used to be reported as exclusive to <em>both</em>, with a note asserting a configuration
     * difference that did not exist.
     */
    private static List<String> exclusiveTypes(
            Map<String, Long> thisSide, Map<String, Long> otherSide, Set<String> comparableCodes) {

        List<String> exclusive = new ArrayList<>();
        for (String code : thisSide.keySet()) {
            if (!comparableCodes.contains(code) && !otherSide.containsKey(code)) {
                exclusive.add(code);
            }
        }
        return exclusive;
    }

    /** Event types both runs recorded that the differential tools nonetheless cannot compare. */
    private static List<String> sharedNotComparable(
            Map<String, Long> primary, Map<String, Long> baseline, Set<String> comparableCodes) {

        List<String> shared = new ArrayList<>();
        for (String code : primary.keySet()) {
            if (!comparableCodes.contains(code) && baseline.containsKey(code)) {
                shared.add(code);
            }
        }
        return shared;
    }

    private static ProfileSide side(ProfileManager manager) {
        ProfileInfo info = manager.info();
        return new ProfileSide(info.id(), info.name(), epochMs(info.profilingStartedAt()),
                epochMs(info.profilingFinishedAt()), ProfileEvidence.durationMillis(info));
    }

    private static Long epochMs(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    /**
     * Zero when the recording carries no window, the same as the recording being too short to
     * compare. {@code ProfileInfo.duration()} itself dereferences both timestamps, and a profile
     * imported from a format without them -- or one whose parse never reached the end -- has neither.
     */
    private static long durationMillis(ProfileInfo info) {
        Long millis = ProfileEvidence.durationMillis(info);
        return millis == null ? 0L : millis;
    }

    private static List<String> notes(
            ProfileInfo primary,
            ProfileInfo baseline,
            List<String> onlyInPrimary,
            List<String> onlyInBaseline,
            List<String> recordedByBothNotComparable) {

        List<String> notes = new ArrayList<>();
        if (durationsDiverge(primary, baseline)) {
            notes.add(NOTE_DURATION_MISMATCH);
        }
        if (!onlyInPrimary.isEmpty()) {
            notes.add(NOTE_ONLY_IN_PRIMARY);
        }
        if (!onlyInBaseline.isEmpty()) {
            notes.add(NOTE_ONLY_IN_BASELINE);
        }
        if (!recordedByBothNotComparable.isEmpty()) {
            notes.add(NOTE_NOT_COMPARABLE);
        }
        return notes;
    }

    private static boolean durationsDiverge(ProfileInfo primary, ProfileInfo baseline) {
        long primaryMillis = durationMillis(primary);
        long baselineMillis = durationMillis(baseline);
        if (primaryMillis <= 0 || baselineMillis <= 0) {
            return true;
        }
        double ratio = (double) Math.max(primaryMillis, baselineMillis)
                / Math.min(primaryMillis, baselineMillis);
        return ratio > DURATION_NOTICE_RATIO;
    }

    /**
     * What one comparison was asked for: the event type, the window as given on the primary's clock,
     * and the filters. The next calls repeat it as it was given, so a drill-down compares exactly what
     * this answer compared.
     */
    private record Request(
            Type type, Long startEpochMs, Long endEpochMs, Boolean useWeight, Boolean excludeIdle, Boolean excludeNonJava) {

        boolean excludesIdle() {
            return Boolean.TRUE.equals(excludeIdle);
        }

        boolean excludesNonJava() {
            return Boolean.TRUE.equals(excludeNonJava);
        }

        /** This comparison's event type, window and filters, added to a call of the same family. */
        McpNextTool.Call applyTo(McpNextTool.Call call) {
            call.with(EVENT_TYPE, type.code());
            if (startEpochMs != null) {
                call.with(START_EPOCH_MS, startEpochMs.longValue());
            }
            if (endEpochMs != null) {
                call.with(END_EPOCH_MS, endEpochMs.longValue());
            }
            return filters(call);
        }

        /**
         * One side's own flamegraph of the same event type and filters, over the window as it landed
         * on that side's clock.
         */
        McpNextTool.Call plainExport(String profileId, EpochWindow window) {
            McpNextTool.Call call = NextCalls.to(EXPORT_TOOL)
                    .with(PROFILE_ID, profileId)
                    .with(EVENT_TYPE, type.code());
            if (window != null) {
                call.with(START_EPOCH_MS, window.startEpochMs()).with(END_EPOCH_MS, window.endEpochMs());
            }
            return filters(call);
        }

        private McpNextTool.Call filters(McpNextTool.Call call) {
            if (useWeight != null) {
                call.with(USE_WEIGHT, useWeight.booleanValue());
            }
            if (excludesIdle()) {
                call.with(EXCLUDE_IDLE, true);
            }
            if (excludesNonJava()) {
                call.with(EXCLUDE_NON_JAVA, true);
            }
            return call;
        }
    }

    /** The window as it landed on both recordings, and the offsets the differential manager reads. */
    private record PairWindow(ComparedWindow compared, RelativeTimeRange offsets) {

        /** No window: both recordings whole. */
        static final PairWindow WHOLE = new PairWindow(null, null);
    }

    /**
     * One comparison window on each recording's own clock: the same offsets from each one's start.
     */
    record ComparedWindow(
            @McpDescription("The window on the primary's recording, as UTC epoch milliseconds")
            EpochWindow primary,
            @McpDescription("The same offsets on the baseline's recording, as UTC epoch milliseconds")
            EpochWindow baseline) {
    }

    /**
     * What the pair can be compared on, and everything that decides whether it should be.
     *
     * @param status EMPTY when nothing the two have in common can be compared; {@code reason} says why
     */
    record Comparability(
            CatalogueStatus status,
            @McpNullable
            @McpDescription("Why nothing can be compared; null when status is OK")
            String reason,
            ProfileSide primary,
            ProfileSide baseline,
            List<ComparableType> comparable,
            List<String> onlyInPrimary,
            List<String> onlyInBaseline,
            List<String> recordedByBothNotComparable,
            List<String> notes,
            McpFollowUp followUp,
            @McpDescription("The differential flamegraphs of this pair in the Microscope UI, for the user")
            String uiLink) {
    }

    record ProfileSide(
            String profileId,
            @McpNullable
            String name,
            @McpNullable
            @McpDescription("When the recording started, as UTC epoch milliseconds; null when unknown")
            Long startedAtEpochMs,
            @McpNullable
            @McpDescription("When the recording finished, as UTC epoch milliseconds; null when unknown")
            Long finishedAtEpochMs,
            @McpNullable
            @McpDescription("How long the recording ran; null when unknown")
            Long durationMs) {
    }

    /**
     * What a ranking of movements compared, beside the Markdown ranking that is its text.
     *
     * @param limit how many movements were asked for in each direction, after the cap
     */
    record MovementRanking(
            String profileId,
            String baselineProfileId,
            String eventType,
            int limit,
            @McpNullable
            @McpDescription("Where the window landed on each recording; null when both were compared whole")
            ComparedWindow window,
            @McpNullable
            @McpDescription("Whether the comparison weighs rather than counts; null keeps the event type's default")
            Boolean useWeight,
            boolean excludeIdle,
            boolean excludeNonJava,
            @McpDescription("Characters of the Markdown ranking, before the text cap")
            int markdownChars,
            @McpDescription("Whether the Markdown was cut to fit the text cap with its footer")
            boolean truncated,
            McpFollowUp followUp,
            @McpDescription("The differential flamegraph this ranking was read from in the Microscope UI - same "
                    + "baseline, event type, window and filters - for the user")
            String uiLink,
            @McpDescription("What uiLink cannot show: the ranking itself, and any part of the baseline the "
                    + "page cuts at the end of the primary's window")
            String uiLinkNote) {
    }

    /**
     * What a differential flamegraph export compared, beside the Markdown tree that is its text.
     */
    record DifferentialExport(
            String profileId,
            String baselineProfileId,
            String eventType,
            @McpNullable
            @McpDescription("The movement threshold asked for, in percent of the primary's total; null for "
                    + "the configured default")
            Double thresholdPct,
            @McpNullable
            @McpDescription("Where the window landed on each recording; null when both were compared whole")
            ComparedWindow window,
            @McpNullable
            @McpDescription("Whether the comparison weighs rather than counts; null keeps the event type's default")
            Boolean useWeight,
            boolean excludeIdle,
            boolean excludeNonJava,
            @McpDescription("Characters of the Markdown export, before the text cap")
            int markdownChars,
            @McpDescription("Whether the Markdown was cut to fit the text cap with its footer; a coarser "
                    + "thresholdPct fits")
            boolean truncated,
            McpFollowUp followUp,
            @McpDescription("The same differential flamegraph in the Microscope UI - same baseline, event type, "
                    + "window and filters - for the user")
            String uiLink,
            @McpNullable
            @McpDescription("What uiLink cannot show: the part of the baseline the page cuts at the end of the "
                    + "primary's window; null when it shows the whole comparison")
            String uiLinkNote) {
    }

    /**
     * One event type both profiles recorded, with each side's totals so a reader can see the volumes
     * they are about to compare before asking for a delta.
     */
    record ComparableType(
            String eventType,
            String label,
            long primarySamples,
            long baselineSamples,
            @McpNullable
            Long primaryWeight,
            @McpNullable
            Long baselineWeight,
            @McpNullable
            String weightUnit) {

        static ComparableType from(EventSummaryResult summary) {
            WeightContext weight = WeightContext.of(Type.fromCode(summary.code()));
            boolean weighted = weight.weighted();
            return new ComparableType(
                    summary.code(),
                    summary.label(),
                    summary.primary().samples(),
                    summary.secondary().samples(),
                    weighted ? summary.primary().weight() : null,
                    weighted ? summary.secondary().weight() : null,
                    weight.weightUnit());
        }
    }
}
