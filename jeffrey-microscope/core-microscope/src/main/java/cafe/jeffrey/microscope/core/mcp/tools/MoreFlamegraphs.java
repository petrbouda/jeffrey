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
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.profile.mcp.McpNextTool;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every recorded event type with stacks that no investigation area opens on a flamegraph — native
 * allocation, native leaks, method traces, custom events, a second sampler of a kind an area already
 * graphs (CPU-time beside execution samples, thread park beside monitor enter) — each with the export
 * that draws it. The menu holds four groups of four, so these are listed beside it rather than as
 * areas of their own; the user picks one by name.
 */
final class MoreFlamegraphs {

    /** The longest list beside the menu; the rest are counted, and flamegraph_list names them all. */
    static final int MAX_LISTED = 10;

    private static final String WHY = "the call tree of the %s samples";

    private MoreFlamegraphs() {
    }

    /**
     * One recorded type no area graphs, with the call that does.
     *
     * @param samples how many samples of it the profile recorded
     */
    record MoreFlamegraph(
            String eventType,
            String label,
            long samples,
            @McpDescription("The flamegraph_export that draws it; send it unchanged")
            McpNextTool export) {
    }

    /**
     * @param listed  the types no area graphs, most samples first, at most {@link #MAX_LISTED}
     * @param omitted how many more there are
     */
    record Listing(List<MoreFlamegraph> listed, int omitted) {

        static final Listing NONE = new Listing(List.of(), 0);

        Listing {
            listed = List.copyOf(listed);
            if (omitted < 0) {
                throw new IllegalArgumentException("an omitted count is never negative: omitted=" + omitted);
            }
        }
    }

    /**
     * @param menu the menu built for the same facts: the types its options' exports graph are left out
     */
    static Listing of(ProfileFacts facts, List<InvestigationOption> menu, AdvertisedFamilies advertised) {
        if (!advertised.servesTool(FollowUpCalls.FLAMEGRAPH_EXPORT)) {
            return Listing.NONE;
        }
        Set<String> graphedByAnArea = menu.stream()
                .flatMap(option -> option.nextTools().stream())
                .filter(call -> FollowUpCalls.FLAMEGRAPH_EXPORT.equals(call.tool()))
                .map(MoreFlamegraphs::eventTypeOf)
                .collect(Collectors.toSet());
        List<ProfileFacts.Graphable> rest = facts.graphable().stream()
                .filter(type -> type.samples() > 0 && !graphedByAnArea.contains(type.eventType()))
                .sorted(Comparator.comparingLong(ProfileFacts.Graphable::samples).reversed()
                        .thenComparing(ProfileFacts.Graphable::eventType))
                .toList();
        List<MoreFlamegraph> listed = rest.stream()
                .limit(MAX_LISTED)
                .map(type -> new MoreFlamegraph(type.eventType(), type.label(), type.samples(),
                        NextCalls.to(FollowUpCalls.FLAMEGRAPH_EXPORT)
                                .with(FollowUpCalls.PROFILE_ID, facts.profileId())
                                .with(FollowUpCalls.EVENT_TYPE, type.eventType())
                                .why(WHY.formatted(labelOf(type)))))
                .toList();
        return new Listing(listed, rest.size() - listed.size());
    }

    private static String eventTypeOf(McpNextTool call) {
        return call.arguments().value().path(FollowUpCalls.EVENT_TYPE).asString();
    }

    private static String labelOf(ProfileFacts.Graphable type) {
        return type.label() == null || type.label().isBlank() ? type.eventType() : type.label();
    }
}
