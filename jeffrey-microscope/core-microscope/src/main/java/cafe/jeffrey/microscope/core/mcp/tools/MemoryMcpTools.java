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
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.allocation.AllocatedType;
import cafe.jeffrey.profile.manager.model.allocation.AllocationOverview;
import cafe.jeffrey.profile.manager.model.leak.LeakCandidate;
import cafe.jeffrey.profile.manager.model.leak.LeakOverview;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;

/**
 * The memory questions a JFR recording answers on its own, with no heap dump.
 * <p>
 * Two things that neither a flamegraph nor the {@code heap_} family covers. An allocation flamegraph
 * ranks call <em>sites</em> — where the allocating code is; this ranks the <em>types</em> allocated,
 * which is the other axis and often the one that names the problem ({@code byte[]} and
 * {@code char[]} at the top read very differently from a domain class). And leak candidates come
 * from {@code jdk.OldObjectSample}, objects the JVM watched survive collections: a leak signal from
 * a plain recording, available when nobody captured a heap dump and the process is already gone.
 * <p>
 * The heap-occupancy series is deliberately not here. It is chart geometry, and the question it
 * answers — when did memory climb — is {@code timeline_hotWindows} on an allocation event type,
 * which returns windows a flamegraph can then be scoped to rather than a curve.
 */
public class MemoryMcpTools {

    private static final MicroscopeView ALLOCATIONS_VIEW = MicroscopeView.ALLOCATIONS;
    private static final MicroscopeView LEAK_CANDIDATES_VIEW = MicroscopeView.MEMORY_LEAK_CANDIDATES;

    private static final int MAX_TYPES = 40;
    private static final int MAX_CANDIDATES = 40;

    private static final String NO_ALLOCATION_DATA =
            "This profile recorded no allocation events, so there is nothing to attribute by type. "
                    + "flamegraph_list names the event types the recording did capture; allocation "
                    + "needs jdk.ObjectAllocationSample, or the older in/outside-TLAB pair.";

    private static final String NO_LEAK_CANDIDATES =
            "This profile carries no jdk.OldObjectSample events, so the JVM tracked no surviving "
                    + "objects. That sampler is off in most profiles and is enabled per recording - its "
                    + "absence says nothing about whether the application leaks. A heap dump answers "
                    + "the same question from the other side; profiles_features says whether this "
                    + "profile has one.";

    private static final String WHERE_WHY = "where the allocation happened: the call paths, weighed by bytes";
    private static final String WHEN_WHY = "when the allocation happened, and the window to scope a flamegraph to";
    private static final String GC_COST_WHY = "what the allocation cost in collector time";
    private static final String LIST_WHY = "names the event types the recording did capture";
    private static final String FEATURES_WHY = "says whether this profile has a heap dump to answer from the other side";
    private static final String RETAINED_GUIDANCE =
            "A candidate is an object that survived, not proof of a leak. What retains it needs a heap "
                    + "dump: heap_getPathToGCRoot on an instance of the same class, when this "
                    + "installation has one.";
    private static final String AGE_GUIDANCE =
            "Age is the discriminator: an object sampled early and still alive at the end outlived "
                    + "every collection in between, which a large short-lived working set does not.";

    private final ProfileManager profileManager;
    private final AdvertisedFamilies advertised;

    public MemoryMcpTools(ProfileManager profileManager, AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.advertised = advertised;
    }

    @Tool(description = "Reports what this application allocated, by type: total bytes, the split "
            + "between TLAB and outside-TLAB, how many distinct types, and the types ranked by "
            + "bytes (the top 40, with omittedTypes counting the rest). Complements the allocation "
            + "flamegraph rather than repeating it - the flamegraph ranks the code doing the "
            + "allocating, this ranks what came out of it, and the two disagree in useful ways when "
            + "one call site allocates many types or one type comes from everywhere. status "
            + "NOT_RECORDED: no allocation event was recorded.")
    @McpOutputSchema(Allocations.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult allocations() {
        String uiLink = UiLinks.view(profileId(), ALLOCATIONS_VIEW);
        AllocationOverview overview = profileManager.allocationManager().overview();
        List<AllocatedType> types = profileManager.allocationManager().topTypes();
        if (overview == null || (overview.totalBytes() == 0 && types.isEmpty())) {
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(call(FollowUpCalls.FLAMEGRAPH_LIST).why(LIST_WHY))
                    .followUp();
            return McpToolResult.of(new Allocations(DashboardStatus.NOT_RECORDED, NO_ALLOCATION_DATA, profileId(),
                    null, List.of(), null, followUp, uiLink));
        }

        // The type the recording allocated with: sampled when it carries no TLAB events, the TLAB
        // pair otherwise - a call naming the other one would draw an empty graph.
        String eventType = overview.sampled()
                ? FollowUpCalls.SAMPLED_ALLOCATION_EVENT
                : FollowUpCalls.TLAB_ALLOCATION_EVENT;
        List<AllocatedType> shown = ToolArguments.firstOf(types, MAX_TYPES);
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(call(FollowUpCalls.FLAMEGRAPH_EXPORT)
                        .with(FollowUpCalls.EVENT_TYPE, eventType)
                        .with(FollowUpCalls.USE_WEIGHT, true)
                        .why(WHERE_WHY))
                .next(call(FollowUpCalls.TIMELINE_HOT_WINDOWS)
                        .with(FollowUpCalls.EVENT_TYPE, eventType)
                        .with(FollowUpCalls.USE_WEIGHT, true)
                        .why(WHEN_WHY))
                .next(call(FollowUpCalls.JVM_GC).why(GC_COST_WHY))
                .followUp();
        return McpToolResult.of(new Allocations(DashboardStatus.OK, null, profileId(),
                AllocationTotals.of(overview),
                shown.stream().map(AllocatedClass::of).toList(),
                overview.distinctTypes() - named(shown),
                followUp,
                uiLink));
    }

    @Tool(description = "Returns objects the JVM sampled and then watched survive garbage collections, "
            + "with their size and age in nanoseconds, the largest first (the top 40, with "
            + "omittedCandidates counting the rest): a leak signal from a plain recording, with no "
            + "heap dump needed. Comes from jdk.OldObjectSample, so it is the only leak evidence "
            + "available when nobody captured a dump and the process has gone. Age matters more "
            + "than size here - an object still alive long after it was sampled outlived every "
            + "collection since. status NOT_RECORDED: the sampler was off.")
    @McpOutputSchema(LeakCandidates.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult leakCandidates() {
        String uiLink = UiLinks.view(profileId(), LEAK_CANDIDATES_VIEW);
        LeakOverview overview = profileManager.leakCandidatesManager().overview();
        List<LeakCandidate> candidates = profileManager.leakCandidatesManager().candidates();
        if (candidates.isEmpty()) {
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(call(FollowUpCalls.PROFILES_FEATURES).why(FEATURES_WHY))
                    .followUp();
            return McpToolResult.of(new LeakCandidates(DashboardStatus.NOT_RECORDED, NO_LEAK_CANDIDATES,
                    profileId(), null, List.of(), null, followUp, uiLink));
        }

        List<LeakCandidate> shown = ToolArguments.firstOf(candidates, MAX_CANDIDATES);
        McpFollowUp followUp = NextSteps.builder(advertised)
                .guidance(AGE_GUIDANCE)
                .guidance(advertised.hint(AdvertisedFamilies.HEAP, RETAINED_GUIDANCE))
                .followUp();
        return McpToolResult.of(new LeakCandidates(DashboardStatus.OK, null, profileId(), overview,
                shown.stream().map(Candidate::of).toList(), candidates.size() - shown.size(), followUp, uiLink));
    }

    /**
     * The rows that are named classes. The manager sums allocations whose event named no class into one
     * unknown row, which the overview's distinct-type count leaves out, so the cut is counted in named
     * types to stay exact.
     */
    private static int named(List<AllocatedType> types) {
        return (int) types.stream().filter(type -> !AllocatedType.UNKNOWN_CLASS.equals(type.className())).count();
    }

    private McpNextTool.Call call(String tool) {
        return McpNextTool.call(tool).with(FollowUpCalls.PROFILE_ID, profileId());
    }

    private String profileId() {
        return profileManager.info().id();
    }

    /**
     * What was allocated in all.
     *
     * @param sampled whether the figures come from jdk.ObjectAllocationSample rather than the TLAB pair
     */
    record AllocationTotals(
            long totalBytes,
            long inTlabBytes,
            long outsideTlabBytes,
            int distinctTypes,
            @McpNullable
            @McpDescription("The type allocating the most bytes; null when no event named a class")
            String dominantType,
            @McpDescription("Whether the figures come from jdk.ObjectAllocationSample rather than the TLAB pair")
            boolean sampled) {

        static AllocationTotals of(AllocationOverview overview) {
            return new AllocationTotals(overview.totalBytes(), overview.inTlabBytes(), overview.outsideTlabBytes(),
                    overview.distinctTypes(), overview.dominantType(), overview.sampled());
        }
    }

    record AllocatedClass(String className, long bytes, long count) {

        static AllocatedClass of(AllocatedType type) {
            return new AllocatedClass(type.className(), type.bytes(), type.count());
        }
    }

    /** One surviving object; the class is null when its event did not name it. */
    record Candidate(
            @McpNullable
            String className,
            long objectSizeBytes,
            long objectAgeNanos,
            int arrayElements,
            long lastKnownHeapUsageBytes) {

        static Candidate of(LeakCandidate candidate) {
            return new Candidate(candidate.className(), candidate.objectSizeBytes(), candidate.objectAgeNanos(),
                    candidate.arrayElements(), candidate.lastKnownHeapUsageBytes());
        }
    }

    record Allocations(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there is no dashboard; null when status is OK")
            String reason,
            String profileId,
            @McpNullable
            @McpDescription("What was allocated in all; null when status is NOT_RECORDED")
            AllocationTotals overview,
            @McpDescription("The types allocating the most bytes, at most 40")
            List<AllocatedClass> topTypes,
            @McpNullable
            @McpDescription("Named types left out of topTypes, counted from distinctTypes; the '<unknown>' row of "
                    + "allocations that named no class is not a type and is not counted; null when status is NOT_RECORDED")
            Integer omittedTypes,
            McpFollowUp followUp,
            @McpDescription("The allocations page in the Microscope UI, for the user")
            String uiLink) {
    }

    record LeakCandidates(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there are no candidates; null when status is OK")
            String reason,
            String profileId,
            @McpNullable
            @McpDescription("How many candidates, the largest and the oldest; null when status is NOT_RECORDED")
            LeakOverview overview,
            @McpDescription("The largest surviving objects, largest first, at most 40")
            List<Candidate> candidates,
            @McpNullable
            @McpDescription("Candidates left out of candidates by its cap of 40; null when status is NOT_RECORDED")
            Integer omittedCandidates,
            McpFollowUp followUp,
            @McpDescription("The leak-candidates page in the Microscope UI, for the user")
            String uiLink) {
    }
}
