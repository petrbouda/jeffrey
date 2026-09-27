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
import cafe.jeffrey.profile.heapdump.model.ClassDiffEntry;
import cafe.jeffrey.profile.heapdump.model.HeapDumpDiffReport;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpDiffService;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolRequirement;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * What grew between two heap dumps.
 * <p>
 * One dump shows a state, and a state cannot distinguish a leak from a large working set — the
 * caveat every heap answer has to carry. Two dumps taken at different times can: a class whose
 * instances climbed while the application did comparable work is the definition of the thing, and no
 * single-dump report says it. This is the question the twenty-tool {@code heap_} family could not
 * ask.
 */
public class HeapDiffMcpTools {

    private static final MicroscopeView DIFF_VIEW = MicroscopeView.HEAP_DUMP_DIFF;

    /** The query the profile page reads its comparison baseline from. */
    private static final String BASELINE_PARAM = "baseline";

    private static final int DEFAULT_TOP = 30;
    private static final int MAX_TOP = 200;

    private static final String PROFILES_LIST_TOOL = "profiles_list";
    private static final String PREPARE_TOOL = "heap_prepare";
    private static final String STATUS_TOOL = "heap_status";
    private static final String BROWSE_INSTANCES_TOOL = "heap_browseClassInstances";
    private static final String DOMINATOR_ROOTS_TOOL = "heap_getDominatorTreeRoots";
    private static final String PROFILE_ID = "profileId";
    private static final String CLASS_NAME = "className";

    private static final String NO_PRIMARY_DUMP =
            "This profile has no heap dump to compare. profiles_list shows which profiles are heap "
                    + "dumps - their event source reads HEAP_DUMP.";

    private static final String NO_BASELINE_DUMP =
            "The baseline profile '%s' has no heap dump. A comparison needs two of them; "
                    + "profiles_list shows which profiles are heap dumps.";

    /**
     * A dump that exists but has not been indexed is a different answer from one that does not exist,
     * and the difference is actionable - the same distinction the heap_ family already makes.
     */
    private static final String NOT_INDEXED =
            "The heap dump of %s is still being indexed. heap_prepare for that profile builds the index "
                    + "and heap_status follows it; a comparison needs both dumps indexed.";

    private static final String WHY_PROFILES = "lists the profiles; a heap dump's event source reads HEAP_DUMP";
    private static final String WHY_PREPARE = "builds that dump's index";
    private static final String WHY_STATUS = "reports how far that dump's preparation has got";
    private static final String WHY_BROWSE = "lists the instances of the class that grew most, with the object ids "
            + "heap_getPathToGCRoot takes";
    private static final String WHY_DOMINATORS = "names what each object actually retains; this compares shallow bytes";

    private static final String WHY_RETAINED_GUIDANCE =
            "A class that grew is an observation. Why the new instances are still reachable is "
                    + "heap_getPathToGCRoot on one of them, and that is what turns growth into a leak "
                    + "claim.";
    private static final String WORKLOAD_GUIDANCE =
            "Growth alone is not a leak - a bigger working set grows too. Say which of the two you are "
                    + "claiming, and whether the two dumps covered comparable work.";

    private final ProfileManager profileManager;
    private final Function<String, ProfileManager> baselineResolver;
    private final AdvertisedFamilies advertised;

    /**
     * @param advertised the families this installation serves, which gate the next calls
     */
    public HeapDiffMcpTools(
            ProfileManager profileManager, Function<String, ProfileManager> baselineResolver,
            AdvertisedFamilies advertised) {

        this.profileManager = profileManager;
        this.baselineResolver = baselineResolver;
        this.advertised = advertised;
    }

    @Tool(description = "Compares this heap dump against an earlier one, class by class: how the "
            + "instance counts and shallow bytes moved, ranked by growth. The definitive leak "
            + "question - one dump cannot tell a leak from a large working set, and two can. The "
            + "earlier dump is the baseline; the other way round, every growth reads as a shrink. A side "
            + "without a heap dump, or not indexed yet, is a status naming which.")
    @McpOutputSchema(HeapDiff.class)
    @McpToolMeta(cost = McpToolCost.EXPENSIVE, requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolResult diff(
            @ToolParam(required = true, description = "Profile id of the earlier heap dump to measure against. The "
                    + "profile this tool is called on is the later one.")
            String baselineProfileId,
            @ToolParam(required = false, description = "How many classes to rank (default " + DEFAULT_TOP
                    + ", maximum " + MAX_TOP + ")")
            @ToolParamBounds(defaultValue = DEFAULT_TOP, min = 1, max = MAX_TOP)
            Integer topN) {

        String profileId = profileManager.info().id();
        int top = ToolArguments.boundedLimit(topN, DEFAULT_TOP, MAX_TOP);
        HeapDumpManager primary = profileManager.heapDumpManager();
        if (!primary.heapDumpExists()) {
            return absent(DiffStatus.NO_HEAP_DUMP, NO_PRIMARY_DUMP, profileId, baselineProfileId, top,
                    steps().next(McpNextTool.call(PROFILES_LIST_TOOL).why(WHY_PROFILES)).followUp());
        }
        if (!primary.isCacheReady()) {
            return absent(DiffStatus.NOT_INDEXED, NOT_INDEXED.formatted("this profile"), profileId, baselineProfileId,
                    top, preparing(profileId));
        }

        String baselineId = requireBaseline(baselineProfileId);
        HeapDumpManager baseline = baselineResolver.apply(baselineId).heapDumpManager();
        if (!baseline.heapDumpExists()) {
            return absent(DiffStatus.BASELINE_NO_HEAP_DUMP, NO_BASELINE_DUMP.formatted(baselineId), profileId,
                    baselineId, top, steps().next(McpNextTool.call(PROFILES_LIST_TOOL).why(WHY_PROFILES)).followUp());
        }
        if (!baseline.isCacheReady()) {
            return absent(DiffStatus.BASELINE_NOT_INDEXED, NOT_INDEXED.formatted("baseline profile " + baselineId),
                    profileId, baselineId, top, preparing(baselineId));
        }

        HeapDumpDiffReport report = HeapDumpDiffService.diff(primary, baseline, top);
        Optional<String> grewMost = report.entries().stream()
                .filter(entry -> entry.bytesDelta() > 0)
                .findFirst()
                .map(ClassDiffEntry::className);
        return McpToolResult.of(new HeapDiff(DiffStatus.OK, null, profileId, baselineId,
                DumpTotals.of(report.primarySummary()), DumpTotals.of(report.baselineSummary()),
                report.instanceCountDelta(), report.shallowBytesDelta(), report.entries(), top,
                report.entries().size() < top ? 0 : null,
                steps()
                        .nextWhen(grewMost.isPresent(), onProfile(BROWSE_INSTANCES_TOOL, profileId)
                                .with(CLASS_NAME, grewMost.orElse(null)).why(WHY_BROWSE))
                        .next(onProfile(DOMINATOR_ROOTS_TOOL, profileId).why(WHY_DOMINATORS))
                        .guidance(WHY_RETAINED_GUIDANCE)
                        .guidance(WORKLOAD_GUIDANCE)
                        .followUp(),
                link(profileId, baselineId)));
    }

    private McpToolResult absent(DiffStatus status, String reason, String profileId, String baselineProfileId,
            int top, McpFollowUp followUp) {
        String baseline = baselineProfileId == null || baselineProfileId.isBlank() ? null : baselineProfileId.trim();
        return McpToolResult.of(new HeapDiff(status, reason, profileId, baseline, null, null, null, null, List.of(),
                top, null, followUp, link(profileId, baseline)));
    }

    /** The calls that build the named profile's index and follow it. */
    private McpFollowUp preparing(String profileId) {
        return steps()
                .next(onProfile(PREPARE_TOOL, profileId).why(WHY_PREPARE))
                .next(onProfile(STATUS_TOOL, profileId).why(WHY_STATUS))
                .followUp();
    }

    /** The diff page of this profile, which adopts the baseline the link names. */
    private static String link(String profileId, String baselineId) {
        return UiLinks.view(profileId, DIFF_VIEW, baselineId == null ? Map.of() : Map.of(BASELINE_PARAM, baselineId));
    }

    private NextSteps.Builder steps() {
        return NextSteps.builder(advertised);
    }

    private static McpNextTool.Call onProfile(String tool, String profileId) {
        return McpNextTool.call(tool).with(PROFILE_ID, profileId);
    }

    private static String requireBaseline(String baselineProfileId) {
        if (baselineProfileId == null || baselineProfileId.isBlank()) {
            throw new IllegalArgumentException(
                    "baselineProfileId is required: the earlier heap dump to measure against");
        }
        return baselineProfileId.trim();
    }

    /** Whether both dumps could be compared, and if not, which side is missing what. */
    public enum DiffStatus {
        OK,
        /** This profile has no heap dump. */
        NO_HEAP_DUMP,
        /** This profile's heap dump is not indexed yet. */
        NOT_INDEXED,
        /** The baseline profile has no heap dump. */
        BASELINE_NO_HEAP_DUMP,
        /** The baseline's heap dump is not indexed yet. */
        BASELINE_NOT_INDEXED
    }

    /**
     * @param classes the classes that moved, ranked by the absolute change in shallow bytes; "primary"
     *                is this profile's later dump and "baseline" the earlier one
     */
    public record HeapDiff(
            DiffStatus status,
            @McpNullable
            @McpDescription("Why there is no comparison, and what makes one possible; null when compared")
            String reason,
            String profileId,
            @McpNullable
            @McpDescription("The earlier dump's profile; null only when this profile has no dump to compare")
            String baselineProfileId,
            @McpNullable
            @McpDescription("This profile's dump, the later one; null without a comparison")
            DumpTotals primaryDump,
            @McpNullable
            @McpDescription("The baseline's dump, the earlier one; null without a comparison")
            DumpTotals baselineDump,
            @McpNullable
            Long instanceCountDelta,
            @McpNullable
            Long shallowBytesDelta,
            @McpDescription("The classes that moved, largest change in shallow bytes first; primary is the later "
                    + "dump and baseline the earlier")
            List<ClassDiffEntry> classes,
            @McpDescription("The most classes asked for")
            int topN,
            @McpNullable
            @McpDescription("How many moved classes the cap left out: 0 when the list is shorter than topN, null "
                    + "when it reached topN or nothing was compared")
            Integer omittedClasses,
            McpFollowUp followUp,
            @McpDescription("The heap-dump diff page in the Microscope UI, opened on this baseline, for the user")
            String uiLink) {
    }

    public record DumpTotals(
            @McpDescription("Shallow bytes of every live object")
            long totalBytes,
            long totalInstances,
            int classCount,
            int gcRootCount,
            @McpNullable
            @McpDescription("When the dump was taken, as UTC epoch milliseconds; null when the dump does not say")
            Long takenAtEpochMs) {

        static DumpTotals of(HeapSummary summary) {
            return new DumpTotals(summary.totalBytes(), summary.totalInstances(), summary.classCount(),
                    summary.gcRootCount(), summary.timestamp() == null ? null : summary.timestamp().toEpochMilli());
        }
    }
}
