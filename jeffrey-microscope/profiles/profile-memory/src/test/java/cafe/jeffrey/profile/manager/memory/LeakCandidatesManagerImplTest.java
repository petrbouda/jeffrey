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

package cafe.jeffrey.profile.manager.memory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.profile.manager.model.leak.LeakCandidate;
import cafe.jeffrey.profile.manager.model.leak.LeakOverview;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeakCandidatesManagerImpl")
class LeakCandidatesManagerImplTest {

    @Mock
    ProfileEventRepository eventRepository;

    private LeakCandidatesManagerImpl manager() {
        return new LeakCandidatesManagerImpl(eventRepository);
    }

    private static ObjectNode oldObjectFields(String type, long size, long ageNanos, int arrayElements, long heap) {
        ObjectNode node = Json.createObject();
        node.put("object", type);
        node.put("objectSize", size);
        node.put("objectAge", ageNanos);
        node.put("arrayElements", arrayElements);
        node.put("lastKnownHeapUsage", heap);
        return node;
    }

    @Nested
    @DisplayName("candidates()")
    class Candidates {

        @Test
        @DisplayName("Maps OldObjectSample fields and orders by size desc")
        void mapsAndSorts() {
            when(eventRepository.eventsByTypeWithFields(Type.OLD_OBJECT_SAMPLE)).thenReturn(List.of(
                    (JsonNode) oldObjectFields("java.util.HashMap$Node[]", 1024, 5_000, 0, 60_000_000),
                    (JsonNode) oldObjectFields("java.lang.Object[]", 84336, 261_000_000, 21079, 63_000_000)));

            List<LeakCandidate> candidates = manager().candidates();

            assertEquals(2, candidates.size());
            LeakCandidate largest = candidates.getFirst();
            assertEquals("java.lang.Object[]", largest.className());
            assertEquals(84336, largest.objectSizeBytes());
            assertEquals(21079, largest.arrayElements());
            assertEquals(261_000_000, largest.objectAgeNanos());
        }
    }

    @Nested
    @DisplayName("overview()")
    class Overview {

        @Test
        @DisplayName("Aggregates count, largest, total and oldest")
        void aggregates() {
            when(eventRepository.eventsByTypeWithFields(Type.OLD_OBJECT_SAMPLE)).thenReturn(List.of(
                    (JsonNode) oldObjectFields("A", 100, 10, 0, 1),
                    (JsonNode) oldObjectFields("B", 900, 99, 0, 1)));

            LeakOverview overview = manager().overview();

            assertEquals(2, overview.candidateCount());
            assertEquals(900, overview.largestBytes());
            assertEquals(1000, overview.totalBytes());
            assertEquals(99, overview.oldestAgeNanos());
        }

        @Test
        @DisplayName("Empty when no OldObjectSample events")
        void emptyWhenAbsent() {
            when(eventRepository.eventsByTypeWithFields(Type.OLD_OBJECT_SAMPLE)).thenReturn(List.of());

            assertTrue(manager().candidates().isEmpty());
            assertEquals(0, manager().overview().candidateCount());
        }
    }
}
