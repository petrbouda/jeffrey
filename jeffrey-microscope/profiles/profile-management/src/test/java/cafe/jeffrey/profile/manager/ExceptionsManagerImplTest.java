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
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionMessageCount;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionTypeStat;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionsOverview;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExceptionsManagerImpl")
class ExceptionsManagerImplTest {

    @Mock
    ProfileEventRepository eventRepository;

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-01-01T00:01:00Z");

    private static final ProfileInfo PROFILE_INFO = new ProfileInfo(
            "test-id", "project-1", "workspace-1", "test-profile",
            null, START, END, START, true, false, null);

    private ExceptionsManagerImpl manager() {
        return new ExceptionsManagerImpl(PROFILE_INFO, eventRepository, eventStreamRepository);
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

    private static GenericRecord record(Type type, long secondsFromStart, ObjectNode fields) {
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), null,
                null, null, 0L, 0L, fields);
    }

    private static ObjectNode statisticsFields(long throwables) {
        ObjectNode node = Json.createObject();
        node.put("throwables", throwables);
        return node;
    }

    private static ObjectNode throwFields(String thrownClass, String message, String thread) {
        ObjectNode node = Json.createObject();
        node.put("thrownClass", thrownClass);
        if (message == null) {
            node.putNull("message");
        } else {
            node.put("message", message);
        }
        node.put("eventThread", thread);
        return node;
    }

    @Nested
    @DisplayName("timeline()")
    class Timeline {

        @Test
        @DisplayName("Emits deltas between consecutive cumulative samples")
        void emitsDeltas() {
            stubStreaming(
                    record(Type.EXCEPTION_STATISTICS, 1, statisticsFields(100)),
                    record(Type.EXCEPTION_STATISTICS, 2, statisticsFields(150)),
                    record(Type.EXCEPTION_STATISTICS, 3, statisticsFields(150)),
                    record(Type.EXCEPTION_STATISTICS, 4, statisticsFields(170)));

            TimeseriesData timeline = manager().timeline();

            assertEquals(1, timeline.series().size());
            SingleSerie serie = timeline.series().getFirst();
            assertEquals("Exceptions / sec", serie.name());
            assertEquals(0, valueAt(serie, 1));   // baseline sample contributes nothing
            assertEquals(50, valueAt(serie, 2));
            assertEquals(0, valueAt(serie, 3));
            assertEquals(20, valueAt(serie, 4));
        }

        @Test
        @DisplayName("Clamps negative deltas to zero (JVM restart in one recording)")
        void clampsNegativeDeltas() {
            stubStreaming(
                    record(Type.EXCEPTION_STATISTICS, 1, statisticsFields(500)),
                    record(Type.EXCEPTION_STATISTICS, 2, statisticsFields(10)));

            SingleSerie serie = manager().timeline().series().getFirst();
            assertEquals(0, valueAt(serie, 2));
        }

        private long valueAt(SingleSerie serie, long second) {
            return serie.data().stream()
                    .filter(point -> point.get(0) == second)
                    .map(point -> point.get(1))
                    .findFirst()
                    .orElseThrow();
        }
    }

    @Nested
    @DisplayName("topTypes()")
    class TopTypes {

        @Test
        @DisplayName("Returns empty without streaming when neither throw event is present")
        void emptyWhenAbsent() {
            when(eventRepository.containsEventType(Type.JAVA_EXCEPTION_THROW)).thenReturn(false);
            when(eventRepository.containsEventType(Type.JAVA_ERROR_THROW)).thenReturn(false);

            assertTrue(manager().topTypes().isEmpty());
            verify(eventStreamRepository, never()).genericStreaming(any(), any());
        }

        @Test
        @DisplayName("Groups by thrown class, marks errors, counts distinct messages and threads")
        void groupsByClass() {
            when(eventRepository.containsEventType(Type.JAVA_EXCEPTION_THROW)).thenReturn(true);
            when(eventRepository.containsEventType(Type.JAVA_ERROR_THROW)).thenReturn(true);

            stubStreaming(
                    record(Type.JAVA_EXCEPTION_THROW, 1, throwFields("java.lang.ClassNotFoundException", "com.Foo", "t1")),
                    record(Type.JAVA_EXCEPTION_THROW, 2, throwFields("java.lang.ClassNotFoundException", "com.Bar", "t2")),
                    record(Type.JAVA_EXCEPTION_THROW, 3, throwFields("java.lang.ClassNotFoundException", "com.Foo", "t1")),
                    record(Type.JAVA_ERROR_THROW, 4, throwFields("java.lang.NoSuchMethodError", "invokeStatic", "main")));

            List<ExceptionTypeStat> types = manager().topTypes();

            assertEquals(2, types.size());
            ExceptionTypeStat cnfe = types.getFirst();
            assertEquals("java.lang.ClassNotFoundException", cnfe.thrownClass());
            assertEquals(3, cnfe.count());
            assertFalse(cnfe.error());
            assertEquals(
                    List.of(new ExceptionMessageCount("com.Foo", 2), new ExceptionMessageCount("com.Bar", 1)),
                    cnfe.messages());
            assertEquals(2, cnfe.threadCount());

            ExceptionTypeStat error = types.get(1);
            assertEquals("java.lang.NoSuchMethodError", error.thrownClass());
            assertTrue(error.error());
            assertEquals(1, error.count());
        }
    }

    @Nested
    @DisplayName("overview()")
    class Overview {

        @Test
        @DisplayName("Combines cumulative gauge with sampled-type aggregates")
        void aggregates() {
            when(eventRepository.latestJsonFields(Type.EXCEPTION_STATISTICS))
                    .thenReturn(Optional.of(statisticsFields(9177)));
            when(eventRepository.containsEventType(Type.JAVA_EXCEPTION_THROW)).thenReturn(true);
            when(eventRepository.containsEventType(Type.JAVA_ERROR_THROW)).thenReturn(true);

            stubStreaming(
                    record(Type.JAVA_EXCEPTION_THROW, 1, throwFields("java.io.IOException", "x", "t1")),
                    record(Type.JAVA_EXCEPTION_THROW, 2, throwFields("java.io.IOException", "y", "t1")),
                    record(Type.JAVA_ERROR_THROW, 3, throwFields("java.lang.OutOfMemoryError", null, "t2")));

            ExceptionsOverview overview = manager().overview();

            assertEquals(9177, overview.totalThrowables());
            assertEquals(2, overview.sampledThrowCount());
            assertEquals(1, overview.errorCount());
            assertEquals(2, overview.distinctTypes());
            assertTrue(overview.hasExceptionThrowEvents());
            assertTrue(overview.hasErrorThrowEvents());
        }

        @Test
        @DisplayName("Falls back to zeros when no exception events exist")
        void zerosWhenAbsent() {
            when(eventRepository.latestJsonFields(Type.EXCEPTION_STATISTICS)).thenReturn(Optional.empty());
            when(eventRepository.containsEventType(Type.JAVA_EXCEPTION_THROW)).thenReturn(false);
            when(eventRepository.containsEventType(Type.JAVA_ERROR_THROW)).thenReturn(false);

            ExceptionsOverview overview = manager().overview();

            assertEquals(0, overview.totalThrowables());
            assertEquals(0, overview.sampledThrowCount());
            assertEquals(0, overview.distinctTypes());
            assertFalse(overview.hasExceptionThrowEvents());
        }
    }
}
