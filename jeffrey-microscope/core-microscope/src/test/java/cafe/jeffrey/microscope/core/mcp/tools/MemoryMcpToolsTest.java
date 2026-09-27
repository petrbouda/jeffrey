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
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.memory.AllocationManager;
import cafe.jeffrey.profile.manager.memory.LeakCandidatesManager;
import cafe.jeffrey.profile.manager.model.allocation.AllocatedType;
import cafe.jeffrey.profile.manager.model.allocation.AllocationOverview;
import cafe.jeffrey.profile.manager.model.leak.LeakCandidate;
import cafe.jeffrey.profile.manager.model.leak.LeakOverview;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemoryMcpToolsTest {

    private static final String PROFILE_ID = "p-1";
    private static final String BYTE_ARRAY = "byte[]";
    private static final String SESSION_CLASS = "com.acme.Session";

    /** The tools' own caps, which the rendered lists must not exceed however long the manager's are. */
    private static final int MAX_TYPES = 40;
    private static final int MAX_CANDIDATES = 40;

    @Mock
    ProfileManager profileManager;

    @Mock
    AllocationManager allocationManager;

    @Mock
    LeakCandidatesManager leakCandidatesManager;

    /**
     * Both answers carry a link into the matching view, which UiLinks builds off the request being
     * served.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        when(profileManager.allocationManager()).thenReturn(allocationManager);
        when(profileManager.leakCandidatesManager()).thenReturn(leakCandidatesManager);
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private MemoryMcpTools tools() {
        return new MemoryMcpTools(profileManager, EVERY_FAMILY);
    }

    private static JsonNode answer(String method, McpToolResult result) {
        return StructuredAnswers.json(MemoryMcpTools.class, method, result);
    }

    private void allocationsRecorded(boolean sampled) {
        when(allocationManager.overview())
                .thenReturn(new AllocationOverview(9_000_000, 7_000_000, 2_000_000, 12, BYTE_ARRAY, sampled));
        when(allocationManager.topTypes()).thenReturn(List.of(new AllocatedType(BYTE_ARRAY, 6_000_000, 1200)));
    }

    @Test
    void everyToolDeclaresAnOutputSchema() {
        assertEquals(List.of(), StructuredAnswers.unschematised(MemoryMcpTools.class));
    }

    @Nested
    class Allocations {

        @Test
        void ranksTheAllocatedTypesBesideTheTlabSplit() {
            when(allocationManager.overview())
                    .thenReturn(new AllocationOverview(9_000_000, 7_000_000, 2_000_000, 2, BYTE_ARRAY, false));
            when(allocationManager.topTypes()).thenReturn(List.of(
                    new AllocatedType(BYTE_ARRAY, 6_000_000, 1200),
                    new AllocatedType(SESSION_CLASS, 3_000_000, 400)));

            JsonNode out = answer("allocations", tools().allocations());

            assertEquals("OK", out.get("status").asString());
            assertEquals(9_000_000, out.get("overview").get("totalBytes").asLong());
            assertEquals(2_000_000, out.get("overview").get("outsideTlabBytes").asLong());
            assertEquals(BYTE_ARRAY, out.get("topTypes").get(0).get("className").asString());
            assertEquals(SESSION_CLASS, out.get("topTypes").get(1).get("className").asString());
            assertEquals(0, out.get("omittedTypes").asInt());
            assertTrue(out.get("uiLink").asString().endsWith("/profiles/" + PROFILE_ID + "/allocations"));
        }

        @Test
        void routesToEveryFamilyWhenAllAreAdvertised() {
            allocationsRecorded(true);

            JsonNode out = answer("allocations", tools().allocations());

            assertEquals(List.of("flamegraph_export", "timeline_hotWindows", "jvm_gc"), StructuredAnswers.nextTools(out));
            assertEquals("jdk.ObjectAllocationSample",
                    StructuredAnswers.call(out, "flamegraph_export").get("eventType").asString());
            assertTrue(StructuredAnswers.call(out, "flamegraph_export").get("useWeight").asBoolean());
        }

        /** The calls name the event type this recording allocated with, which is not always the sampled one. */
        @Test
        void routesATlabRecordingToTheTlabEventType() {
            allocationsRecorded(false);

            JsonNode out = answer("allocations", tools().allocations());

            assertEquals("jdk.ObjectAllocationInNewTLAB",
                    StructuredAnswers.call(out, "timeline_hotWindows").get("eventType").asString());
        }

        /** Each call goes with its family: a trimmed list keeps only the ones still served. */
        @Test
        void leavesOutTheCallsToFamiliesThatAreNotAdvertised() {
            allocationsRecorded(true);

            JsonNode out = answer("allocations", new MemoryMcpTools(profileManager,
                    new AdvertisedFamilies(Set.of("memory", "jvm"))).allocations());

            assertEquals(List.of("jvm_gc"), StructuredAnswers.nextTools(out));
        }

        /**
         * These managers answer an absent event type with a well-formed zero, which reads as "this
         * application allocated nothing" rather than as "nothing was measured".
         */
        @Test
        void saysNothingWasRecordedAsAStatusRatherThanRenderingZeroBytes() {
            when(allocationManager.overview())
                    .thenReturn(new AllocationOverview(0, 0, 0, 0, null, false));
            when(allocationManager.topTypes()).thenReturn(List.of());

            JsonNode out = answer("allocations", tools().allocations());

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("recorded no allocation events"), out.toString());
            assertTrue(out.get("reason").asString().contains("jdk.ObjectAllocationSample"), out.toString());
            assertTrue(out.get("overview").isNull());
            assertTrue(out.get("omittedTypes").isNull());
            assertEquals(List.of("flamegraph_list"), StructuredAnswers.nextTools(out));
        }

        /**
         * The type list has no bound of its own - a recording can hold one row per class the
         * application ever allocated - so the tool keeps the head and counts the rest.
         */
        @Test
        void capsTheTypeListAndCountsWhatItLeftOut() {
            when(allocationManager.overview())
                    .thenReturn(new AllocationOverview(9_000_000, 7_000_000, 2_000_000, 120, BYTE_ARRAY, false));
            when(allocationManager.topTypes()).thenReturn(IntStream.range(0, 100)
                    .mapToObj(index -> new AllocatedType("com.acme.Type" + index, 1_000 - index, 1))
                    .toList());

            JsonNode out = answer("allocations", tools().allocations());

            assertEquals(MAX_TYPES, out.get("topTypes").size());
            assertEquals(120 - MAX_TYPES, out.get("omittedTypes").asInt(), "counted from every type allocated");
        }

        /**
         * Events that named no class are summed under the manager's unknown bucket, which the overview's
         * distinctTypes does not count: the omitted count is of named types, and exact either way.
         */
        @Test
        void countsOmittedNamedTypesExactlyBesideTheUnknownBucket() {
            when(allocationManager.overview())
                    .thenReturn(new AllocationOverview(9_000_000, 7_000_000, 2_000_000, 2, BYTE_ARRAY, false));
            when(allocationManager.topTypes()).thenReturn(List.of(
                    new AllocatedType(BYTE_ARRAY, 6_000_000, 1200),
                    new AllocatedType(AllocatedType.UNKNOWN_CLASS, 2_000_000, 300),
                    new AllocatedType(SESSION_CLASS, 1_000_000, 40)));

            JsonNode out = answer("allocations", tools().allocations());

            assertEquals(3, out.get("topTypes").size());
            assertEquals(0, out.get("omittedTypes").asInt());
        }

        @Test
        void countsOmittedNamedTypesWhenTheUnknownBucketIsAmongThoseShown() {
            when(allocationManager.overview())
                    .thenReturn(new AllocationOverview(9_000_000, 7_000_000, 2_000_000, 120, BYTE_ARRAY, false));
            List<AllocatedType> types = new ArrayList<>(IntStream.range(0, 99)
                    .mapToObj(index -> new AllocatedType("com.acme.Type" + index, 1_000 - index, 1))
                    .toList());
            types.add(5, new AllocatedType(AllocatedType.UNKNOWN_CLASS, 999, 1));
            when(allocationManager.topTypes()).thenReturn(types);

            JsonNode out = answer("allocations", tools().allocations());

            assertEquals(MAX_TYPES, out.get("topTypes").size());
            assertEquals(120 - (MAX_TYPES - 1), out.get("omittedTypes").asInt());
        }

        @Test
        void anOverviewWithNoDominantTypeConformsAsNull() {
            when(allocationManager.overview())
                    .thenReturn(new AllocationOverview(9_000_000, 7_000_000, 2_000_000, 0, null, false));
            when(allocationManager.topTypes()).thenReturn(List.of());

            JsonNode out = answer("allocations", tools().allocations());

            assertTrue(out.get("overview").get("dominantType").isNull());
        }
    }

    @Nested
    class LeakCandidates {

        @Test
        void namesTheSurvivingObjectsWithTheirSizeAndAge() {
            when(leakCandidatesManager.overview()).thenReturn(new LeakOverview(2, 4_096, 6_144, 42_000_000_000L));
            when(leakCandidatesManager.candidates()).thenReturn(List.of(
                    new LeakCandidate(SESSION_CLASS, 4_096, 42_000_000_000L, 0, 512_000_000),
                    new LeakCandidate(BYTE_ARRAY, 2_048, 11_000_000_000L, 2_048, 512_000_000)));

            JsonNode out = answer("leakCandidates", tools().leakCandidates());

            assertEquals(SESSION_CLASS, out.get("candidates").get(0).get("className").asString());
            assertEquals(42_000_000_000L, out.get("candidates").get(0).get("objectAgeNanos").asLong());
            assertEquals(2, out.get("overview").get("candidateCount").asInt());
            assertTrue(out.get("uiLink").asString().endsWith("memory-issues/leak-candidates"));
            assertTrue(StructuredAnswers.guidance(out).contains("Age is the discriminator"), out.toString());
        }

        /**
         * The sampler is off in most profiles, so its absence has to read as "not measured" rather than
         * as "this application does not leak" - a verdict the tool has no evidence for.
         */
        @Test
        void saysTheSamplerWasOffRatherThanClearingTheApplication() {
            when(leakCandidatesManager.overview()).thenReturn(new LeakOverview(0, 0, 0, 0));
            when(leakCandidatesManager.candidates()).thenReturn(List.of());

            JsonNode out = answer("leakCandidates", tools().leakCandidates());

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("jdk.OldObjectSample"), out.toString());
            assertTrue(out.get("reason").asString().contains("says nothing about whether the application leaks"),
                    out.toString());
            assertTrue(out.get("overview").isNull());
            assertEquals(List.of("profiles_features"), StructuredAnswers.nextTools(out));
        }

        @Test
        void capsTheCandidateListAndCountsWhatItLeftOut() {
            when(leakCandidatesManager.overview()).thenReturn(new LeakOverview(90, 4_096, 6_144, 42L));
            when(leakCandidatesManager.candidates()).thenReturn(IntStream.range(0, 90)
                    .mapToObj(index -> new LeakCandidate("com.acme.Type" + index, 4_096, 42L, 0, 1))
                    .toList());

            JsonNode out = answer("leakCandidates", tools().leakCandidates());

            assertEquals(MAX_CANDIDATES, out.get("candidates").size());
            assertEquals(90 - MAX_CANDIDATES, out.get("omittedCandidates").asInt());
        }

        @Test
        void aCandidateWhoseClassWasNotNamedConformsAsNull() {
            when(leakCandidatesManager.overview()).thenReturn(new LeakOverview(1, 4_096, 4_096, 42L));
            when(leakCandidatesManager.candidates()).thenReturn(List.of(new LeakCandidate(null, 4_096, 42L, 0, 1)));

            JsonNode out = answer("leakCandidates", tools().leakCandidates());

            assertTrue(out.get("candidates").get(0).get("className").isNull());
        }
    }
}
