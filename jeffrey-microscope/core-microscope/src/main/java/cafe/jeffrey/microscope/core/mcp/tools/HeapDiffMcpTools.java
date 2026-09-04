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
import cafe.jeffrey.profile.heapdump.model.HeapDumpDiffReport;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpDiffService;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
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

    private static final String DIFF_VIEW = "heap-dump/diff";

    private static final int DEFAULT_TOP = 30;
    private static final int MAX_TOP = 200;

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
            "The heap dump of %s is still being indexed. Open it once in the Jeffrey UI to build the "
                    + "index, then try again - a comparison needs both dumps indexed.";

    private static final String STEP_WHY_RETAINED =
            "A class that grew is an observation. Why the new instances are still reachable is "
                    + "heap_getPathToGCRoot on one of them, and that is what turns growth into a leak "
                    + "claim.";
    private static final String STEP_DOMINATORS =
            "Shallow bytes are what this compares. What each class actually retains needs the "
                    + "dominator tree: heap_getDominatorTreeRoots on either profile, once.";
    private static final String STEP_WORKLOAD =
            "Growth alone is not a leak - a bigger working set grows too. Say which of the two you are "
                    + "claiming, and whether the two dumps covered comparable work.";

    private final ProfileManager profileManager;
    private final Function<String, ProfileManager> baselineResolver;

    public HeapDiffMcpTools(
            ProfileManager profileManager, Function<String, ProfileManager> baselineResolver) {

        this.profileManager = profileManager;
        this.baselineResolver = baselineResolver;
    }

    @Tool(description = "Compare this heap dump against an earlier one, class by class: how the "
            + "instance counts and shallow bytes moved, ranked by growth. This is the definitive leak "
            + "question - one dump cannot tell a leak from a large working set, and two can. Pass the "
            + "earlier dump as the baseline; backwards, every growth reads as a shrink.")
    public String diff(
            @ToolParam(description = "Profile id of the earlier heap dump to measure against. The "
                    + "profile this tool is called on is the later one.")
            String baselineProfileId,
            @ToolParam(description = "How many classes to rank (default 30, maximum 200)")
            Integer topN) {

        HeapDumpManager primary = profileManager.heapDumpManager();
        if (!primary.heapDumpExists()) {
            return NO_PRIMARY_DUMP;
        }
        if (!primary.isCacheReady()) {
            return NOT_INDEXED.formatted("this profile");
        }

        String baselineId = requireBaseline(baselineProfileId);
        HeapDumpManager baseline = baselineResolver.apply(baselineId).heapDumpManager();
        if (!baseline.heapDumpExists()) {
            return NO_BASELINE_DUMP.formatted(baselineId);
        }
        if (!baseline.isCacheReady()) {
            return NOT_INDEXED.formatted("baseline profile " + baselineId);
        }

        HeapDumpDiffReport report = HeapDumpDiffService.diff(primary, baseline, boundedTop(topN));
        return LinkedOutput.json(new HeapDiff(
                report.primarySummary(),
                report.baselineSummary(),
                report.instanceCountDelta(),
                report.shallowBytesDelta(),
                report.entries(),
                NextSteps.builder()
                        .add(STEP_WHY_RETAINED)
                        .add(STEP_DOMINATORS)
                        .add(STEP_WORKLOAD)
                        .build(),
                UiLinks.view(profileManager.info().id(), DIFF_VIEW)));
    }

    private static String requireBaseline(String baselineProfileId) {
        if (baselineProfileId == null || baselineProfileId.isBlank()) {
            throw new IllegalArgumentException(
                    "baselineProfileId is required: the earlier heap dump to measure against");
        }
        return baselineProfileId.trim();
    }

    private static int boundedTop(Integer topN) {
        return topN == null ? DEFAULT_TOP : Math.clamp(topN, 1, MAX_TOP);
    }

    private record HeapDiff(
            Object laterDump,
            Object earlierDump,
            long instanceCountDelta,
            long shallowBytesDelta,
            List<?> classes,
            List<String> nextSteps,
            String uiLink) {
    }
}
