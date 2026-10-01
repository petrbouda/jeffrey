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
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.finding.McpFinding;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The menu an open question is answered with: every investigation area this installation serves, each
 * either available with the calls that open it or not recorded with what is missing, and at most
 * {@value #MAX_SUGGESTED} marked as where the evidence points.
 * <p>
 * It routes and never diagnoses, like {@link NextSteps}: a suggestion says what points at an area — a
 * rule that fired, the kind of sample the profile mostly holds — never that the area is the problem.
 * The user chooses; the menu runs nothing.
 */
final class InvestigationMenu {

    static final int MAX_SUGGESTED = 2;

    /**
     * The share of the stack samples one kind must hold to be suggested for that alone. Counts of
     * different samplers are not commensurable, so only a clear majority says anything.
     */
    static final double DOMINANT_SAMPLE_SHARE = 0.5;

    private static final Set<McpFinding.Severity> SUGGESTING = Set.of(
            McpFinding.Severity.CRITICAL, McpFinding.Severity.WARNING);

    private static final Map<SampleKind, InvestigationArea> AREA_BY_SAMPLE_KIND = Map.of(
            SampleKind.CPU, InvestigationArea.CPU_HOTSPOTS,
            SampleKind.WALL, InvestigationArea.WALL_CLOCK,
            SampleKind.ALLOCATION, InvestigationArea.ALLOCATION,
            SampleKind.BLOCKING, InvestigationArea.LOCK_CONTENTION);

    private static final String RULE_EVIDENCE = "the rule '%s' flagged %s";
    private static final String SHARE_EVIDENCE = "%s holds %.0f%% of the stack samples (%,d of %,d)";
    private static final String EVENT_TYPE_SEPARATOR = " + ";
    private static final double PERCENT = 100.0;

    private InvestigationMenu() {
    }

    /**
     * The menu for one profile. A heap dump has none: it answers one question, what is retained, and
     * fifteen lines of recording areas it cannot answer would bury that.
     */
    static List<InvestigationOption> of(ProfileFacts facts, AdvertisedFamilies advertised) {
        if (facts.eventSource() == RecordingEventSource.HEAP_DUMP) {
            return List.of();
        }
        Map<InvestigationArea, InvestigationOption> options = new EnumMap<>(InvestigationArea.class);
        for (InvestigationArea area : InvestigationArea.values()) {
            if (area.servedBy(advertised)) {
                option(area, facts, advertised).ifPresent(option -> options.put(area, option));
            }
        }
        suggest(options, facts);
        return List.copyOf(options.values());
    }

    private static Optional<InvestigationOption> option(
            InvestigationArea area, ProfileFacts facts, AdvertisedFamilies advertised) {

        AreaSource source = area.source();
        if (!source.available(facts)) {
            return Optional.of(InvestigationOption.notRecorded(area, source.gap(facts), source.remedy()));
        }
        List<McpNextTool> calls = area.calls().calls(facts, advertised).stream()
                .filter(call -> advertised.servesTool(call.tool()))
                .toList();
        // Its data is there but no call to it is served: not something this installation can offer.
        return calls.isEmpty() ? Optional.empty() : Optional.of(InvestigationOption.available(area, calls));
    }

    /** Fired rules first, most severe first, then the dominant kind of sample; at most two in all. */
    private static void suggest(Map<InvestigationArea, InvestigationOption> options, ProfileFacts facts) {
        List<Suggestion> suggestions = new ArrayList<>();
        for (McpFinding finding : facts.flagged()) {
            if (SUGGESTING.contains(finding.severity())) {
                suggestions.add(new Suggestion(InvestigationArea.forTopic(finding.category()),
                        RULE_EVIDENCE.formatted(finding.title(), finding.severity().name())));
            }
        }
        dominant(facts).ifPresent(suggestions::add);

        int marked = 0;
        for (Suggestion suggestion : suggestions) {
            if (marked == MAX_SUGGESTED) {
                return;
            }
            InvestigationOption option = options.get(suggestion.area());
            if (option != null && option.isAvailable() && !option.suggested()) {
                options.put(suggestion.area(), option.suggestedBy(suggestion.evidence()));
                marked++;
            }
        }
    }

    /**
     * The kind of stack sample that holds a clear majority. Wall-clock samples count idle threads too,
     * so they are weighed only when the profile has no CPU samples to weigh instead.
     */
    private static Optional<Suggestion> dominant(ProfileFacts facts) {
        boolean hasCpu = facts.recorded(SampleKind.CPU).isPresent();
        Map<SampleKind, Long> samples = new EnumMap<>(SampleKind.class);
        Map<SampleKind, List<String>> eventTypes = new EnumMap<>(SampleKind.class);
        for (ProfileFacts.Sampled panel : facts.panels()) {
            boolean weighed = panel.recorded()
                    && AREA_BY_SAMPLE_KIND.containsKey(panel.kind())
                    && !(hasCpu && panel.kind() == SampleKind.WALL);
            if (weighed) {
                samples.merge(panel.kind(), panel.samples(), Long::sum);
                eventTypes.computeIfAbsent(panel.kind(), kind -> new ArrayList<>()).add(panel.eventType());
            }
        }
        long total = samples.values().stream().mapToLong(Long::longValue).sum();
        return samples.entrySet().stream()
                .filter(entry -> entry.getValue() > total * DOMINANT_SAMPLE_SHARE)
                .findFirst()
                .map(entry -> new Suggestion(AREA_BY_SAMPLE_KIND.get(entry.getKey()), SHARE_EVIDENCE.formatted(
                        String.join(EVENT_TYPE_SEPARATOR, eventTypes.get(entry.getKey())),
                        entry.getValue() * PERCENT / total, entry.getValue(), total)));
    }

    private record Suggestion(InvestigationArea area, String evidence) {
    }
}
