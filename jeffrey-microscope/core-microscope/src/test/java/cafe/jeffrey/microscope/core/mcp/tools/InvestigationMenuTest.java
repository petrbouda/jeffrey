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
import cafe.jeffrey.microscope.core.mcp.tools.InvestigationOption.Availability;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.JvmSections.SectionAvailability;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolWeight;
import cafe.jeffrey.profile.mcp.finding.McpFinding;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationMenuTest {

    private static final String PROFILE_ID = "p-1";
    private static final String EXECUTION = "jdk.ExecutionSample";
    private static final String CPU_TIME = "jdk.CPUTimeSample";
    private static final String WALL = "profiler.WallClockSample";
    private static final String ALLOCATION = "jdk.ObjectAllocationSample";
    private static final String MONITOR = "jdk.JavaMonitorEnter";
    private static final List<String> JVM_SECTION_IDS =
            List.of("gc", "safepoints", "jit", "threads", "nativeMemory", "container", "configuration");

    /** A recording with every kind of data, built up and narrowed by each test. */
    private static final class Facts {
        private RecordingEventSource source = RecordingEventSource.ASYNC_PROFILER;
        private final Set<FeatureType> disabled = new HashSet<>();
        private final Set<String> eventTypes = new HashSet<>(Set.of(
                "jdk.SocketRead", "jdk.FileWrite", "jdk.OldObjectSample"));
        private final Map<String, Long> samples = new LinkedHashMap<>(Map.of(
                EXECUTION, 1_000L, CPU_TIME, 0L, WALL, 5_000L, ALLOCATION, 500L, MONITOR, 300L));
        private final Set<String> missingSections = new HashSet<>();
        private AutoAnalysisStatus status = AutoAnalysisStatus.COMPUTED;
        private final List<McpFinding> flagged = new ArrayList<>();

        Facts samples(String eventType, long count) {
            samples.put(eventType, count);
            return this;
        }

        Facts disable(FeatureType... features) {
            disabled.addAll(Arrays.asList(features));
            return this;
        }

        Facts noSection(String id) {
            missingSections.add(id);
            return this;
        }

        Facts noEventTypes() {
            eventTypes.clear();
            return this;
        }

        Facts status(AutoAnalysisStatus status) {
            this.status = status;
            return this;
        }

        Facts source(RecordingEventSource source) {
            this.source = source;
            return this;
        }

        Facts flagged(String topic, McpFinding.Severity severity) {
            flagged.add(McpFinding.of(topic, topic + "-rule").severity(severity).title(topic + " rule").build());
            return this;
        }

        ProfileFacts build() {
            Map<String, SampleKind> kinds = Map.of(EXECUTION, SampleKind.CPU, CPU_TIME, SampleKind.CPU,
                    WALL, SampleKind.WALL, ALLOCATION, SampleKind.ALLOCATION, MONITOR, SampleKind.BLOCKING);
            List<ProfileFacts.Sampled> panels = samples.entrySet().stream()
                    .map(entry -> new ProfileFacts.Sampled(kinds.get(entry.getKey()), entry.getKey(), entry.getValue()))
                    .toList();
            Set<String> recorded = new HashSet<>(eventTypes);
            samples.forEach((type, count) -> {
                if (count > 0) {
                    recorded.add(type);
                }
            });
            List<SectionAvailability> sections = JVM_SECTION_IDS.stream()
                    .map(id -> new SectionAvailability(id, id, "jvm_" + id, !missingSections.contains(id),
                            List.of("jdk." + id)))
                    .toList();
            return new ProfileFacts(PROFILE_ID, source, disabled, recorded, panels, sections, status, flagged);
        }
    }

    private static Map<InvestigationArea, InvestigationOption> menu(Facts facts) {
        return menu(facts, EVERY_FAMILY);
    }

    private static Map<InvestigationArea, InvestigationOption> menu(Facts facts, AdvertisedFamilies advertised) {
        Map<InvestigationArea, InvestigationOption> byArea = new EnumMap<>(InvestigationArea.class);
        for (InvestigationOption option : InvestigationMenu.of(facts.build(), advertised)) {
            byArea.put(option.area(), option);
        }
        return byArea;
    }

    private static List<String> tools(InvestigationOption option) {
        return option.nextTools().stream().map(McpNextTool::tool).toList();
    }

    @Nested
    class Catalogue {

        /** One question per group with up to four options is how a host puts the menu to the user. */
        @Test
        void everyGroupHoldsAtMostFourAreas() {
            Map<InvestigationGroup, Long> sizes = Arrays.stream(InvestigationArea.values())
                    .collect(Collectors.groupingBy(InvestigationArea::group, Collectors.counting()));

            assertEquals(Set.of(InvestigationGroup.values()), sizes.keySet());
            sizes.forEach((group, size) -> assertTrue(size <= 4, group + " holds " + size));
        }

        @Test
        void titlesAreShortEnoughForAnOptionLabel() {
            for (InvestigationArea area : InvestigationArea.values()) {
                assertTrue(area.title().split(" ").length <= 5, area.title());
            }
        }

        @Test
        void noTopicIsClaimedByTwoAreas() {
            Set<String> seen = new HashSet<>();
            for (InvestigationArea area : InvestigationArea.values()) {
                for (String topic : area.topics()) {
                    assertTrue(seen.add(topic), topic);
                }
            }
        }

        @Test
        void aTopicNoAreaClaimsPointsAtTheRuleFindings() {
            assertEquals(InvestigationArea.RULE_FINDINGS, InvestigationArea.forTopic("constant_pools"));
            assertEquals(InvestigationArea.GC_PAUSES, InvestigationArea.forTopic("garbage_collection"));
        }
    }

    @Nested
    class Areas {

        @Test
        void aFullRecordingOffersEveryArea() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts());

            assertEquals(Set.of(InvestigationArea.values()), menu.keySet());
            menu.values().forEach(option -> assertEquals(Availability.AVAILABLE, option.availability(), option.title()));
        }

        @Test
        void eachAreaOpensWithItsOwnCalls() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts());

            McpNextTool cpu = menu.get(InvestigationArea.CPU_HOTSPOTS).nextTools().getFirst();
            assertEquals("flamegraph_export", cpu.tool());
            assertEquals(EXECUTION, Json.toTree(cpu.arguments()).get("eventType").asString());
            assertEquals(PROFILE_ID, Json.toTree(cpu.arguments()).get("profileId").asString());
            assertEquals(List.of("jvm_gc", "jvm_safepoints"), tools(menu.get(InvestigationArea.GC_PAUSES)));
            assertEquals(List.of("jdbc_overview", "jdbc_pools"), tools(menu.get(InvestigationArea.DATABASE)));
            assertEquals(List.of("io_overview", "io_overview"), tools(menu.get(InvestigationArea.IO_WAITING)));
            assertEquals(List.of("traces_overview"), tools(menu.get(InvestigationArea.SLOW_ENDPOINTS)));
            McpNextTool timeline = menu.get(InvestigationArea.HOT_WINDOWS).nextTools().getFirst();
            assertEquals(WALL, Json.toTree(timeline.arguments()).get("eventType").asString());
        }

        @Test
        void anAreaWeighsAsItsHeaviestCall() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts());

            assertEquals(McpToolWeight.HEAVY, menu.get(InvestigationArea.CPU_HOTSPOTS).weight());
            assertEquals(McpToolWeight.MEDIUM, menu.get(InvestigationArea.GC_PAUSES).weight());
            assertEquals(McpToolWeight.MEDIUM, menu.get(InvestigationArea.CONTAINER_AND_FLAGS).weight());
        }

        @Test
        void aMissingSamplerNamesItsEventAndHowToRecordIt() {
            InvestigationOption cpu = menu(new Facts().samples(EXECUTION, 0)).get(InvestigationArea.CPU_HOTSPOTS);

            assertEquals(Availability.NOT_RECORDED, cpu.availability());
            assertTrue(cpu.gap().contains(EXECUTION) && cpu.gap().contains(CPU_TIME), cpu.gap());
            assertTrue(cpu.remedy().contains("event=cpu"), cpu.remedy());
            assertNull(cpu.weight());
            assertTrue(cpu.nextTools().isEmpty());
        }

        @Test
        void theCpuTimeSamplerIsGraphedWhenTheOlderOneIsMissing() {
            InvestigationOption cpu = menu(new Facts().samples(EXECUTION, 0).samples(CPU_TIME, 900))
                    .get(InvestigationArea.CPU_HOTSPOTS);

            assertEquals(CPU_TIME, Json.toTree(cpu.nextTools().getFirst().arguments()).get("eventType").asString());
        }

        @Test
        void endpointsFallBackToHttpWithoutTraces() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts().disable(FeatureType.TRACES));

            McpNextTool http = menu.get(InvestigationArea.SLOW_ENDPOINTS).nextTools().getFirst();
            assertEquals("http_overview", http.tool());
            assertEquals("SERVER", Json.toTree(http.arguments()).get("direction").asString());
        }

        @Test
        void endpointsWithNeitherTracesNorServedRequestsAreAGap() {
            InvestigationOption endpoints = menu(new Facts().disable(FeatureType.TRACES, FeatureType.HTTP_SERVER_DASHBOARD))
                    .get(InvestigationArea.SLOW_ENDPOINTS);

            assertEquals(Availability.NOT_RECORDED, endpoints.availability());
            assertTrue(endpoints.gap().contains("no traces"), endpoints.gap());
        }

        @Test
        void aJvmSectionWithoutEventsIsAGap() {
            InvestigationOption jit = menu(new Facts().noSection("jit")).get(InvestigationArea.JIT);

            assertEquals(Availability.NOT_RECORDED, jit.availability());
            assertTrue(jit.gap().contains("jvm_jit"), jit.gap());
        }

        @Test
        void unrecordedIoAndLeakEventsAreGaps() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts().noEventTypes());

            assertEquals(Availability.NOT_RECORDED, menu.get(InvestigationArea.IO_WAITING).availability());
            assertEquals(Availability.NOT_RECORDED, menu.get(InvestigationArea.LEAK_CANDIDATES).availability());
        }

        @Test
        void rulesThatDidNotRunAreOfferedAsTheSlowCall() {
            McpNextTool rules = menu(new Facts().status(AutoAnalysisStatus.NOT_COMPUTED))
                    .get(InvestigationArea.RULE_FINDINGS).nextTools().getFirst();

            assertTrue(Json.toTree(rules.arguments()).get("compute").asBoolean());
        }

        @Test
        void rulesThatCannotRunAreAGap() {
            InvestigationOption rules = menu(new Facts().status(AutoAnalysisStatus.CANNOT_COMPUTE))
                    .get(InvestigationArea.RULE_FINDINGS);

            assertEquals(Availability.NOT_RECORDED, rules.availability());
        }

        @Test
        void anAreaWhoseFamilyIsWithheldIsLeftOut() {
            Set<String> families = new HashSet<>(EVERY_FAMILY.families());
            families.remove(AdvertisedFamilies.FLAMEGRAPH);

            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts(), new AdvertisedFamilies(families));

            assertFalse(menu.containsKey(InvestigationArea.CPU_HOTSPOTS));
            assertFalse(menu.containsKey(InvestigationArea.WALL_CLOCK));
            assertTrue(menu.containsKey(InvestigationArea.ALLOCATION));
        }

        @Test
        void aHeapDumpHasNoMenu() {
            assertTrue(InvestigationMenu.of(new Facts().source(RecordingEventSource.HEAP_DUMP).build(), EVERY_FAMILY)
                    .isEmpty());
        }
    }

    @Nested
    class Suggestions {

        @Test
        void aWarningRulePointsAtItsTopicsArea() {
            InvestigationOption gc = menu(new Facts().flagged("garbage_collection", McpFinding.Severity.WARNING))
                    .get(InvestigationArea.GC_PAUSES);

            assertTrue(gc.suggested());
            assertTrue(gc.evidence().contains("garbage_collection rule"), gc.evidence());
        }

        @Test
        void anInformationalRuleSuggestsNothing() {
            InvestigationOption gc = menu(new Facts().flagged("garbage_collection", McpFinding.Severity.INFO))
                    .get(InvestigationArea.GC_PAUSES);

            assertFalse(gc.suggested());
            assertNull(gc.evidence());
        }

        /** Wall-clock samples count idle threads, so they are not weighed against CPU samples. */
        @Test
        void theDominantCpuSamplesAreSuggestedAndWallClockIsNotWeighedAgainstThem() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts().samples(EXECUTION, 8_000));

            assertTrue(menu.get(InvestigationArea.CPU_HOTSPOTS).suggested());
            assertTrue(menu.get(InvestigationArea.CPU_HOTSPOTS).evidence().contains("(8,000 of 8,800)"),
                    menu.get(InvestigationArea.CPU_HOTSPOTS).evidence());
            assertFalse(menu.get(InvestigationArea.WALL_CLOCK).suggested());
        }

        @Test
        void wallClockIsWeighedWhenThereAreNoCpuSamples() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts().samples(EXECUTION, 0));

            assertTrue(menu.get(InvestigationArea.WALL_CLOCK).suggested());
        }

        @Test
        void noClearMajoritySuggestsNothing() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts()
                    .samples(EXECUTION, 400).samples(ALLOCATION, 400).samples(MONITOR, 400));

            assertTrue(menu.values().stream().noneMatch(InvestigationOption::suggested));
        }

        @Test
        void atMostTwoAreSuggested() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts()
                    .samples(EXECUTION, 8_000)
                    .flagged("garbage_collection", McpFinding.Severity.CRITICAL)
                    .flagged("compilations", McpFinding.Severity.WARNING)
                    .flagged("threads", McpFinding.Severity.WARNING));

            assertEquals(Set.of(InvestigationArea.GC_PAUSES, InvestigationArea.JIT), menu.values().stream()
                    .filter(InvestigationOption::suggested)
                    .map(InvestigationOption::area)
                    .collect(Collectors.toSet()));
        }

        @Test
        void aRulePointingAtAnUnavailableAreaSuggestsNothingThere() {
            InvestigationOption jit = menu(new Facts().noSection("jit").flagged("compilations", McpFinding.Severity.WARNING))
                    .get(InvestigationArea.JIT);

            assertFalse(jit.suggested());
        }
    }

    @Nested
    class Wire {

        @Test
        void conformsToItsGeneratedSchema() {
            for (InvestigationOption option : InvestigationMenu.of(new Facts().samples(EXECUTION, 0).build(), EVERY_FAMILY)) {
                McpSchemaConformance.assertConforms(Json.toTree(option), McpSchemaGenerator.schemaOf(InvestigationOption.class));
            }
        }

        @Test
        void refusesAnAvailableAreaWithoutCalls() {
            assertThrows(IllegalArgumentException.class,
                    () -> InvestigationOption.available(InvestigationArea.JIT, List.of()));
        }

        @Test
        void refusesASuggestionOfAnUnavailableArea() {
            InvestigationOption gap = InvestigationOption.notRecorded(InvestigationArea.JIT, "no jit", null);

            assertThrows(IllegalArgumentException.class, () -> gap.suggestedBy("a rule"));
        }
    }
}
