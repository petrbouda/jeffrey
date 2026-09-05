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
import cafe.jeffrey.flamegraph.ai.WeightContext;
import cafe.jeffrey.profile.common.config.GraphComponents;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.DifferentialFlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.shared.common.GraphType;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

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
 */
public class CompareMcpTools {

    private static final int DEFAULT_MOVEMENT_LIMIT = 15;
    private static final int MAX_MOVEMENT_LIMIT = 100;

    /** Beyond this ratio the recordings are different enough that the reader should be told. */
    private static final double DURATION_NOTICE_RATIO = 1.25;

    private static final String STEP_DRILL =
            "compare_flamegraph shows the call paths a movement travelled through - pass the same "
                    + "eventType and read the frame this ranking flagged.";
    private static final String STEP_RENAME =
            "A method that appears on one side only may be a rename rather than a change. You have the "
                    + "source diff and the profile does not; check before reporting either half.";
    private static final String STEP_WHOLE =
            "This tree prunes by movement, so a frame absent here did not move - it is not missing. "
                    + "flamegraph_export on either profile shows what it costs in absolute terms.";

    private static final String NOTHING_COMPARABLE =
            "These two profiles have no event type in common that can be compared. They may be "
                    + "different recording formats, one may be a heap dump, or they were captured with "
                    + "different profiler settings. Use profiles_features on each to see what it holds.";

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

    private final ProfileManager primaryManager;
    private final Function<String, ProfileManager> baselineResolver;

    public CompareMcpTools(
            ProfileManager primaryManager, Function<String, ProfileManager> baselineResolver) {

        this.primaryManager = primaryManager;
        this.baselineResolver = baselineResolver;
    }

    @Tool(description = "Establish whether two profiles can be compared at all, and on which event "
            + "types. Call this before any other compare_ tool: it reports both recordings' length, "
            + "the event types they have in common with each side's sample and weight totals, and the "
            + "event types only one of them recorded — which is a profiler-configuration difference "
            + "between the runs rather than a change in the application. A comparison of recordings of "
            + "very different length or volume produces numbers that look precise and mean nothing.")
    public String list(
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
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        List<String> onlyInPrimary = exclusiveTypes(primaryManager, comparableCodes);
        List<String> onlyInBaseline = exclusiveTypes(baseline, comparableCodes);

        Comparability comparability = new Comparability(
                side(primaryManager),
                side(baseline),
                comparable,
                onlyInPrimary,
                onlyInBaseline,
                notes(primaryManager.info(), baseline.info(), onlyInPrimary, onlyInBaseline));

        if (comparable.isEmpty()) {
            return NOTHING_COMPARABLE + "\n\n" + McpToolOutput.json(comparability);
        }
        return McpToolOutput.json(comparability);
    }

    @Tool(description = "Rank the methods that moved between two profiles: which the primary spends "
            + "more in (a regression) and which it spends less in. This is the main tool for 'did my "
            + "change make it slower'. Movements are attributed by SELF weight, so a change is charged "
            + "to the method that actually moved rather than to every caller above it, and the "
            + "baseline is scaled onto the primary's recording length first. The output opens with a "
            + "comparability section — read it before reporting any finding.")
    public String movements(
            @ToolParam(required = true, description = "Id of the profile to compare against — the baseline, normally "
                    + "the run from before the change.")
            String baselineProfileId,
            @ToolParam(required = true, description = "JFR event type to compare, e.g. 'jdk.ExecutionSample' for CPU or "
                    + "'jdk.ObjectAllocationSample' for allocation. Use compare_list to see which "
                    + "types these two profiles have in common.")
            String eventType,
            @ToolParam(required = false, description = "How many movements to report in each direction (default 15)")
            Integer limit,
            @ToolParam(required = false, description = "Start of the window, in milliseconds from the beginning of each "
                    + "recording. Applied at the same offset into both. Omit for the whole recording.")
            Long startMs,
            @ToolParam(required = false, description = "End of the window, in milliseconds from the beginning of each "
                    + "recording. Omit for the whole recording.")
            Long endMs,
            @ToolParam(required = false, description = "Compare by the event's weight (bytes allocated, nanoseconds "
                    + "blocked) instead of by sample count")
            Boolean useWeight,
            @ToolParam(required = false, description = "Drop samples of threads that were idle")
            Boolean excludeIdle,
            @ToolParam(required = false, description = "Drop samples that were not executing Java code")
            Boolean excludeNonJava) {

        ProfileManager baseline = baseline(baselineProfileId);
        GraphParameters params = params(eventType, startMs, endMs, useWeight, excludeIdle, excludeNonJava);
        return LinkedOutput.of(
                diffManager(baseline).rankedMovements(params, boundedLimit(limit)),
                List.of(STEP_DRILL, STEP_RENAME),
                UiLinks.profile(primaryManager.info().id()));
    }

    @Tool(description = "Export the differential flamegraph of two profiles as Markdown: the merged "
            + "call tree, with every frame carrying what the primary spends there, what the baseline "
            + "spent, and the movement between them. Use this to drill into a method compare_movements "
            + "has already flagged — it shows the call paths the movement travelled through. Subtrees "
            + "in which nothing moved are pruned, so absence here means 'did not change', not 'not "
            + "present'.")
    public String flamegraph(
            @ToolParam(required = true, description = "Id of the profile to compare against — the baseline, normally "
                    + "the run from before the change.")
            String baselineProfileId,
            @ToolParam(required = true, description = "JFR event type to compare. Use compare_list to see which types "
                    + "these two profiles have in common.")
            String eventType,
            @ToolParam(required = false, description = "Keep only subtrees containing a movement of at least this "
                    + "percentage of the primary's total (exclusive range 0-100). Lower means more "
                    + "detail and a longer document; the configured default is used when omitted.")
            Double thresholdPct,
            @ToolParam(required = false, description = "Start of the window, in milliseconds from the beginning of each "
                    + "recording. Applied at the same offset into both. Omit for the whole recording.")
            Long startMs,
            @ToolParam(required = false, description = "End of the window, in milliseconds from the beginning of each "
                    + "recording. Omit for the whole recording.")
            Long endMs,
            @ToolParam(required = false, description = "Compare by the event's weight (bytes allocated, nanoseconds "
                    + "blocked) instead of by sample count")
            Boolean useWeight,
            @ToolParam(required = false, description = "Drop samples of threads that were idle")
            Boolean excludeIdle,
            @ToolParam(required = false, description = "Drop samples that were not executing Java code")
            Boolean excludeNonJava) {

        ProfileManager baseline = baseline(baselineProfileId);
        GraphParameters params = params(eventType, startMs, endMs, useWeight, excludeIdle, excludeNonJava);
        return LinkedOutput.of(
                diffManager(baseline).generateAiExport(params, aiExportConfig(thresholdPct)),
                List.of(STEP_WHOLE, STEP_RENAME),
                UiLinks.profile(primaryManager.info().id()));
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

    private GraphParameters params(
            String eventType,
            Long startMs,
            Long endMs,
            Boolean useWeight,
            Boolean excludeIdle,
            Boolean excludeNonJava) {

        Type type = FlamegraphMcpTools.requireEventType(eventType);
        return GraphParameters.builder()
                .withEventType(type)
                .withTimeRange(FlamegraphMcpTools.timeRange(primaryManager.info(), startMs, endMs))
                .withThreads(List.of())
                // Per-thread mode splits the tree by thread name, and thread names differ between two
                // runs (pool-1-thread-7 is not the same worker twice), so every branch would read as
                // appeared/vanished. A comparison is always thread-aggregated.
                .withThreadMode(false)
                .withUseWeight(useWeight)
                .withExcludeNonJavaSamples(Boolean.TRUE.equals(excludeNonJava))
                .withExcludeIdleSamples(Boolean.TRUE.equals(excludeIdle))
                .withOnlyUnsafeAllocationSamples(false)
                .withParseLocation(true)
                .withGraphType(GraphType.DIFFERENTIAL)
                .withGraphComponents(GraphComponents.FLAMEGRAPH_ONLY)
                .build();
    }

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

    private static int boundedLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_MOVEMENT_LIMIT;
        }
        return Math.min(limit, MAX_MOVEMENT_LIMIT);
    }

    /**
     * Event types one profile recorded and the pair cannot be compared on. Reported rather than
     * dropped: a type missing from one side is a difference between the two profiler configurations,
     * and a reader who does not know that will read its absence as the application no longer doing it.
     */
    private static List<String> exclusiveTypes(ProfileManager manager, Set<String> comparableCodes) {
        List<String> exclusive = new ArrayList<>();
        for (EventSummaryResult summary : manager.flamegraphManager().eventSummaries()) {
            if (summary.primary().samples() > 0 && !comparableCodes.contains(summary.code())) {
                exclusive.add(summary.code());
            }
        }
        return exclusive;
    }

    private static ProfileSide side(ProfileManager manager) {
        ProfileInfo info = manager.info();
        Duration duration = info.duration() == null ? Duration.ZERO : info.duration();
        return new ProfileSide(info.id(), info.name(), duration.toString(), duration.toMillis());
    }

    private static List<String> notes(
            ProfileInfo primary,
            ProfileInfo baseline,
            List<String> onlyInPrimary,
            List<String> onlyInBaseline) {

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
        return notes;
    }

    private static boolean durationsDiverge(ProfileInfo primary, ProfileInfo baseline) {
        long primaryMillis = primary.duration() == null ? 0L : primary.duration().toMillis();
        long baselineMillis = baseline.duration() == null ? 0L : baseline.duration().toMillis();
        if (primaryMillis <= 0 || baselineMillis <= 0) {
            return true;
        }
        double ratio = (double) Math.max(primaryMillis, baselineMillis)
                / Math.min(primaryMillis, baselineMillis);
        return ratio > DURATION_NOTICE_RATIO;
    }

    /**
     * What the pair can be compared on, and everything that decides whether it should be.
     */
    private record Comparability(
            ProfileSide primary,
            ProfileSide baseline,
            List<ComparableType> comparable,
            List<String> onlyInPrimary,
            List<String> onlyInBaseline,
            List<String> notes) {
    }

    private record ProfileSide(String profileId, String name, String duration, long durationMs) {
    }

    /**
     * One event type both profiles recorded, with each side's totals so a reader can see the volumes
     * they are about to compare before asking for a delta.
     */
    private record ComparableType(
            String eventType,
            String label,
            long primarySamples,
            long baselineSamples,
            Long primaryWeight,
            Long baselineWeight,
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
