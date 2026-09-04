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
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.allocation.AllocatedType;
import cafe.jeffrey.profile.manager.model.allocation.AllocationOverview;
import cafe.jeffrey.profile.manager.model.leak.LeakCandidate;
import cafe.jeffrey.profile.manager.model.leak.LeakOverview;
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

    private static final String ALLOCATIONS_VIEW = "allocations";
    private static final String LEAK_CANDIDATES_VIEW = "memory-issues/leak-candidates";

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

    private static final String STEP_WHERE =
            "This says what was allocated, not where. The call paths are flamegraph_export with "
                    + "jdk.ObjectAllocationSample and useWeight true.";
    private static final String STEP_WHEN =
            "When the allocation happened - and the window to scope a flamegraph to - is "
                    + "timeline_hotWindows on the same event type.";
    private static final String STEP_GC_COST =
            "What the allocation cost in collector time is jvm_gc; churn and pause budget are "
                    + "different measurements of the same cause.";
    private static final String STEP_RETAINED =
            "A candidate is an object that survived, not proof of a leak. What retains it needs a heap "
                    + "dump: heap_getPathToGCRoot on the same class, when this installation has one.";
    private static final String STEP_AGE =
            "Age is the discriminator: an object sampled early and still alive at the end outlived "
                    + "every collection in between, which a large short-lived working set does not.";

    private final ProfileManager profileManager;

    public MemoryMcpTools(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Tool(description = "What this application allocated, by type: total bytes, the split between TLAB "
            + "and outside-TLAB, how many distinct types, and the types ranked by bytes. Complements "
            + "the allocation flamegraph rather than repeating it - the flamegraph ranks the code "
            + "doing the allocating, this ranks what came out of it, and the two disagree in useful "
            + "ways when one call site allocates many types or one type comes from everywhere.")
    public String allocations() {
        AllocationOverview overview = profileManager.allocationManager().overview();
        List<AllocatedType> types = profileManager.allocationManager().topTypes();
        if (overview == null || (overview.totalBytes() == 0 && types.isEmpty())) {
            return NO_ALLOCATION_DATA;
        }

        return LinkedOutput.json(new Allocations(
                overview,
                trimTypes(types),
                NextSteps.builder().add(STEP_WHERE).add(STEP_WHEN).add(STEP_GC_COST).build(),
                UiLinks.view(profileId(), ALLOCATIONS_VIEW)));
    }

    @Tool(description = "Objects the JVM sampled and then watched survive garbage collections, with "
            + "their size and age: a leak signal from a plain recording, with no heap dump needed. "
            + "Comes from jdk.OldObjectSample, so it is the only leak evidence available when nobody "
            + "captured a dump and the process has gone. Age matters more than size here - an object "
            + "still alive long after it was sampled outlived every collection since.")
    public String leakCandidates() {
        LeakOverview overview = profileManager.leakCandidatesManager().overview();
        List<LeakCandidate> candidates = profileManager.leakCandidatesManager().candidates();
        if (candidates.isEmpty()) {
            return NO_LEAK_CANDIDATES;
        }

        return LinkedOutput.json(new LeakCandidates(
                overview,
                trimCandidates(candidates),
                NextSteps.builder().add(STEP_AGE).add(STEP_RETAINED).build(),
                UiLinks.view(profileId(), LEAK_CANDIDATES_VIEW)));
    }

    private static List<AllocatedType> trimTypes(List<AllocatedType> types) {
        return types.size() <= MAX_TYPES ? types : types.subList(0, MAX_TYPES);
    }

    private static List<LeakCandidate> trimCandidates(List<LeakCandidate> candidates) {
        return candidates.size() <= MAX_CANDIDATES ? candidates : candidates.subList(0, MAX_CANDIDATES);
    }

    private String profileId() {
        return profileManager.info().id();
    }

    private record Allocations(
            AllocationOverview overview,
            List<AllocatedType> topTypes,
            List<String> nextSteps,
            String uiLink) {
    }

    private record LeakCandidates(
            LeakOverview overview,
            List<LeakCandidate> candidates,
            List<String> nextSteps,
            String uiLink) {
    }
}
