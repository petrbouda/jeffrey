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

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.memory.AllocationManager;
import cafe.jeffrey.profile.manager.memory.LeakCandidatesManager;
import cafe.jeffrey.profile.manager.model.allocation.AllocatedType;
import cafe.jeffrey.profile.manager.model.allocation.AllocationOverview;
import cafe.jeffrey.profile.manager.model.leak.LeakCandidate;
import cafe.jeffrey.profile.manager.model.leak.LeakOverview;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
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

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final String TOP_TYPES_FIELD = "topTypes";
    private static final String CANDIDATES_FIELD = "candidates";

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
        return new MemoryMcpTools(profileManager);
    }

    private static int arraySize(String json, String field) {
        return Json.mapper().readTree(json).get(field).size();
    }

    @Nested
    class Allocations {

        @Test
        void ranksTheAllocatedTypesBesideTheTlabSplit() {
            when(allocationManager.overview())
                    .thenReturn(new AllocationOverview(9_000_000, 7_000_000, 2_000_000, 12, BYTE_ARRAY, false));
            when(allocationManager.topTypes()).thenReturn(List.of(
                    new AllocatedType(BYTE_ARRAY, 6_000_000, 1200),
                    new AllocatedType(SESSION_CLASS, 3_000_000, 400)));

            String out = tools().allocations();

            assertTrue(out.contains("\"totalBytes\":9000000"), out);
            assertTrue(out.contains("\"outsideTlabBytes\":2000000"), out);
            assertTrue(out.contains(BYTE_ARRAY), out);
            assertTrue(out.contains(SESSION_CLASS), out);
            assertTrue(out.contains("/profiles/" + PROFILE_ID + "/allocations"), out);
        }

        /**
         * These managers answer an absent event type with a well-formed zero, which reads as "this
         * application allocated nothing" rather than as "nothing was measured".
         */
        @Test
        void saysNothingWasRecordedRatherThanRenderingZeroBytes() {
            when(allocationManager.overview())
                    .thenReturn(new AllocationOverview(0, 0, 0, 0, null, false));
            when(allocationManager.topTypes()).thenReturn(List.of());

            String out = tools().allocations();

            assertTrue(out.contains("recorded no allocation events"), out);
            assertTrue(out.contains("jdk.ObjectAllocationSample"), out);
            assertFalse(out.contains("\"totalBytes\""), out);
        }

        /**
         * The type list has no bound of its own - a recording can hold one row per class the
         * application ever allocated - so the tool caps it before it is rendered.
         */
        @Test
        void capsTheTypeListHoweverManyTheManagerReturns() {
            when(allocationManager.overview())
                    .thenReturn(new AllocationOverview(9_000_000, 7_000_000, 2_000_000, 120, BYTE_ARRAY, false));
            when(allocationManager.topTypes()).thenReturn(IntStream.range(0, 120)
                    .mapToObj(index -> new AllocatedType("com.acme.Type" + index, 1_000 - index, 1))
                    .toList());

            assertEquals(MAX_TYPES, arraySize(tools().allocations(), TOP_TYPES_FIELD));
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

            String out = tools().leakCandidates();

            assertTrue(out.contains(SESSION_CLASS), out);
            assertTrue(out.contains("\"objectAgeNanos\":42000000000"), out);
            assertTrue(out.contains("\"candidateCount\":2"), out);
            assertTrue(out.contains("memory-issues/leak-candidates"), out);
        }

        /**
         * The sampler is off in most profiles, so its absence has to read as "not measured" rather than
         * as "this application does not leak" - a verdict the tool has no evidence for.
         */
        @Test
        void saysTheSamplerWasOffRatherThanClearingTheApplication() {
            when(leakCandidatesManager.overview()).thenReturn(new LeakOverview(0, 0, 0, 0));
            when(leakCandidatesManager.candidates()).thenReturn(List.of());

            String out = tools().leakCandidates();

            assertTrue(out.contains("jdk.OldObjectSample"), out);
            assertTrue(out.contains("says nothing about whether the application leaks"), out);
            assertFalse(out.contains("\"candidateCount\""), out);
        }

        @Test
        void capsTheCandidateListHoweverManyTheManagerReturns() {
            when(leakCandidatesManager.overview()).thenReturn(new LeakOverview(90, 4_096, 6_144, 42L));
            when(leakCandidatesManager.candidates()).thenReturn(IntStream.range(0, 90)
                    .mapToObj(index -> new LeakCandidate("com.acme.Type" + index, 4_096, 42L, 0, 1))
                    .toList());

            assertEquals(MAX_CANDIDATES, arraySize(tools().leakCandidates(), CANDIDATES_FIELD));
        }
    }
}
