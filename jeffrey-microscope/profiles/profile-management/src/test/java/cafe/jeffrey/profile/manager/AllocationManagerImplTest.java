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

package cafe.jeffrey.profile.manager;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.profile.manager.model.allocation.AllocatedType;
import cafe.jeffrey.profile.manager.model.allocation.AllocationOverview;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationManagerImpl")
class AllocationManagerImplTest {

    @Mock
    ProfileEventRepository eventRepository;

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-01-01T00:01:00Z");

    private static final ProfileInfo PROFILE_INFO = new ProfileInfo(
            "test-id", "project-1", "workspace-1", "test-profile",
            null, START, END, START, true, false, null);

    private AllocationManagerImpl manager() {
        return new AllocationManagerImpl(PROFILE_INFO, eventRepository, eventStreamRepository);
    }

    @SuppressWarnings("unchecked")
    private void stubStreaming(GenericRecord... records) {
        when(eventStreamRepository.genericStreaming(any(), any())).thenAnswer(invocation -> {
            RecordBuilder<GenericRecord, Object> builder = invocation.getArgument(1);
            for (GenericRecord record : records) {
                builder.onRecord(record);
            }
            return builder.build();
        });
    }

    private static GenericRecord allocation(Type type, long secondsFromStart, String objectClass, long bytes) {
        ObjectNode fields = Json.createObject();
        fields.put("objectClass", objectClass);
        fields.put("allocationSize", bytes);
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), null,
                null, null, 1L, bytes, fields);
    }

    @Nested
    @DisplayName("overview()")
    class Overview {

        @Test
        @DisplayName("Splits in-TLAB vs outside-TLAB and finds the dominant type")
        void splitsTlab() {
            when(eventRepository.containsEventType(Type.OBJECT_ALLOCATION_IN_NEW_TLAB)).thenReturn(true);
            lenient().when(eventRepository.containsEventType(Type.OBJECT_ALLOCATION_OUTSIDE_TLAB)).thenReturn(false);
            stubStreaming(
                    allocation(Type.OBJECT_ALLOCATION_IN_NEW_TLAB, 1, "byte[]", 1000),
                    allocation(Type.OBJECT_ALLOCATION_IN_NEW_TLAB, 2, "byte[]", 3000),
                    allocation(Type.OBJECT_ALLOCATION_OUTSIDE_TLAB, 3, "java.lang.Object", 500));

            AllocationOverview overview = manager().overview();

            assertEquals(4500, overview.totalBytes());
            assertEquals(4000, overview.inTlabBytes());
            assertEquals(500, overview.outsideTlabBytes());
            assertEquals(2, overview.distinctTypes());
            assertEquals("byte[]", overview.dominantType());
            assertFalse(overview.sampled());
        }
    }

    @Nested
    @DisplayName("topTypes()")
    class TopTypes {

        @Test
        @DisplayName("Groups by class summing bytes, ordered desc")
        void groupsByClass() {
            when(eventRepository.containsEventType(Type.OBJECT_ALLOCATION_IN_NEW_TLAB)).thenReturn(true);
            lenient().when(eventRepository.containsEventType(Type.OBJECT_ALLOCATION_OUTSIDE_TLAB)).thenReturn(false);
            stubStreaming(
                    allocation(Type.OBJECT_ALLOCATION_IN_NEW_TLAB, 1, "char[]", 100),
                    allocation(Type.OBJECT_ALLOCATION_IN_NEW_TLAB, 2, "byte[]", 5000),
                    allocation(Type.OBJECT_ALLOCATION_IN_NEW_TLAB, 3, "char[]", 200));

            List<AllocatedType> types = manager().topTypes();

            assertEquals(2, types.size());
            assertEquals("byte[]", types.getFirst().className());
            assertEquals(5000, types.getFirst().bytes());
            assertEquals("char[]", types.get(1).className());
            assertEquals(300, types.get(1).bytes());
            assertEquals(2, types.get(1).count());
        }
    }
}
