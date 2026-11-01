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

import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.model.Type;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The Microscope page an investigation area is seen on, for the user: what the menu links beside the
 * area so its page opens in one click, before or instead of an analysis. A flamegraph area links the
 * exact graph its export draws — the event type, the default weighting and the whole recording, as
 * {@code flamegraph_export}'s own uiLink.
 */
@FunctionalInterface
interface AreaPage {

    /** The page for this profile; empty when it has none to show. */
    Optional<String> uiLink(ProfileFacts facts);

    /** One view of the profile, with no query of its own. */
    static AreaPage view(MicroscopeView view) {
        return facts -> Optional.of(UiLinks.view(facts.profileId(), view));
    }

    /** The flamegraph of the first recorded panel of the kind: the graph the area's export draws. */
    static AreaPage flamegraph(SampleKind kind) {
        return facts -> facts.recorded(kind)
                .map(panel -> flamegraphLink(facts, panel.eventType(), panel.weighted()));
    }

    /** The first page whose data the profile has, in the order given. */
    static AreaPage firstOf(Shown... pages) {
        List<Shown> ordered = Arrays.asList(pages);
        return facts -> ordered.stream()
                .filter(page -> page.when().test(facts))
                .findFirst()
                .map(page -> UiLinks.view(facts.profileId(), page.view()));
    }

    /** One graph of one event type, as {@code flamegraph_export} links it with no option given. */
    static String flamegraphLink(ProfileFacts facts, String eventType, boolean weighted) {
        return FlamegraphViewLink.primary(facts.profileId(), Type.fromCode(eventType))
                .weighted(weighted)
                .window(facts.wholeRecording())
                .url();
    }

    /** A page and what the profile has to hold for it to show anything. */
    record Shown(Predicate<ProfileFacts> when, MicroscopeView view) {
    }
}
