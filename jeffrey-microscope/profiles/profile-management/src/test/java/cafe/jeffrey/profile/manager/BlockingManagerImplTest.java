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
import cafe.jeffrey.profile.manager.model.blocking.ContentionStat;
import cafe.jeffrey.profile.manager.model.blocking.MonitorWaitStat;
import cafe.jeffrey.profile.manager.model.blocking.SleepStat;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BlockingManagerImpl")
class BlockingManagerImplTest {

    @Mock
    ProfileEventRepository eventRepository;

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-01-01T00:01:00Z");

    private static final ProfileInfo PROFILE_INFO = new ProfileInfo(
            "test-id", "project-1", "workspace-1", "test-profile",
            null, START, END, START, true, false, null);

    private BlockingManagerImpl manager() {
        return new BlockingManagerImpl(PROFILE_INFO, eventRepository, eventStreamRepository);
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

    private static GenericRecord record(Type type, long secondsFromStart, Duration duration, ObjectNode fields) {
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), duration,
                null, null, 0L, 0L, fields);
    }

    private static ObjectNode monitorFields(String monitorClass, String thread) {
        ObjectNode node = Json.createObject();
        node.put("monitorClass", monitorClass);
        node.put("eventThread", thread);
        return node;
    }

    private static ObjectNode monitorWaitFields(String monitorClass, String thread, boolean timedOut) {
        ObjectNode node = Json.createObject();
        node.put("monitorClass", monitorClass);
        node.put("eventThread", thread);
        node.put("timedOut", timedOut);
        return node;
    }

    private static ObjectNode sleepFields(String thread, long requestedNanos) {
        ObjectNode node = Json.createObject();
        node.put("eventThread", thread);
        node.put("time", requestedNanos);
        return node;
    }

    private static long valueAt(SingleSerie serie, long second) {
        return serie.data().stream()
                .filter(point -> point.get(0) == second)
                .map(point -> point.get(1))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("blockingTimeline()")
    class BlockingTimeline {

        @Test
        @DisplayName("Counts occurrences per second, one series per blocking type")
        void countsPerType() {
            stubStreaming(
                    record(Type.JAVA_MONITOR_ENTER, 2, Duration.ofNanos(1), monitorFields("c", "t1")),
                    record(Type.THREAD_PARK, 2, Duration.ofNanos(1), monitorFields("c", "t1")),
                    record(Type.THREAD_PARK, 2, Duration.ofNanos(1), monitorFields("c", "t2")));

            TimeseriesData timeline = manager().blockingTimeline();

            assertEquals(5, timeline.series().size());
            SingleSerie monitors = timeline.series().get(0);
            SingleSerie parks = timeline.series().get(2);
            assertEquals("Lock Contention", monitors.name());
            assertEquals("Thread Parks", parks.name());
            assertEquals(1, valueAt(monitors, 2));
            assertEquals(2, valueAt(parks, 2));
        }
    }

    @Nested
    @DisplayName("monitorContention()")
    class MonitorContention {

        @Test
        @DisplayName("Groups by monitorClass with blocked-time totals and distinct threads")
        void groupsByMonitorClass() {
            when(eventRepository.containsEventType(Type.JAVA_MONITOR_ENTER)).thenReturn(true);
            stubStreaming(
                    record(Type.JAVA_MONITOR_ENTER, 1, Duration.ofNanos(224_420), monitorFields("java.lang.Thread", "t1")),
                    record(Type.JAVA_MONITOR_ENTER, 2, Duration.ofNanos(100_000), monitorFields("java.lang.Thread", "t2")),
                    record(Type.JAVA_MONITOR_ENTER, 3, Duration.ofNanos(50_000), monitorFields("com.example.Cache", "t1")));

            List<ContentionStat> stats = manager().monitorContention();

            assertEquals(2, stats.size());
            ContentionStat top = stats.getFirst();
            assertEquals("java.lang.Thread", top.className());
            assertEquals(2, top.count());
            assertEquals(324_420, top.totalNanos());
            assertEquals(224_420, top.maxNanos());
            assertEquals(2, top.threadCount());
        }
    }

    @Nested
    @DisplayName("monitorWaits()")
    class MonitorWaits {

        @Test
        @DisplayName("Returns empty without streaming when the event is absent")
        void emptyWhenAbsent() {
            when(eventRepository.containsEventType(Type.JAVA_MONITOR_WAIT)).thenReturn(false);

            assertTrue(manager().monitorWaits().isEmpty());
            verify(eventStreamRepository, never()).genericStreaming(any(), any());
        }

        @Test
        @DisplayName("Groups by monitorClass and counts timed-out waits")
        void groupsAndCountsTimedOut() {
            when(eventRepository.containsEventType(Type.JAVA_MONITOR_WAIT)).thenReturn(true);
            stubStreaming(
                    record(Type.JAVA_MONITOR_WAIT, 1, Duration.ofMillis(200),
                            monitorWaitFields("java.lang.Object", "t1", true)),
                    record(Type.JAVA_MONITOR_WAIT, 2, Duration.ofMillis(50),
                            monitorWaitFields("java.lang.Object", "t2", false)),
                    record(Type.JAVA_MONITOR_WAIT, 3, Duration.ofMillis(10),
                            monitorWaitFields("com.example.Queue", "t1", false)));

            List<MonitorWaitStat> waits = manager().monitorWaits();

            assertEquals(2, waits.size());
            MonitorWaitStat top = waits.getFirst();
            assertEquals("java.lang.Object", top.className());
            assertEquals(2, top.count());
            assertEquals(Duration.ofMillis(250).toNanos(), top.totalNanos());
            assertEquals(Duration.ofMillis(200).toNanos(), top.maxNanos());
            assertEquals(2, top.threadCount());
            assertEquals(1, top.timedOutCount());
        }
    }

    @Nested
    @DisplayName("sleeps()")
    class Sleeps {

        @Test
        @DisplayName("Returns empty without streaming when the event is absent")
        void emptyWhenAbsent() {
            when(eventRepository.containsEventType(Type.THREAD_SLEEP)).thenReturn(false);

            assertTrue(manager().sleeps().isEmpty());
            verify(eventStreamRepository, never()).genericStreaming(any(), any());
        }

        @Test
        @DisplayName("Groups by thread, summing slept and requested time, ordered by total slept")
        void groupsByThread() {
            when(eventRepository.containsEventType(Type.THREAD_SLEEP)).thenReturn(true);
            stubStreaming(
                    record(Type.THREAD_SLEEP, 1, Duration.ofMillis(40), sleepFields("main", Duration.ofMillis(37).toNanos())),
                    record(Type.THREAD_SLEEP, 2, Duration.ofMillis(60), sleepFields("main", Duration.ofMillis(50).toNanos())),
                    record(Type.THREAD_SLEEP, 3, Duration.ofMillis(10), sleepFields("worker", Duration.ofMillis(10).toNanos())));

            List<SleepStat> sleeps = manager().sleeps();

            assertEquals(2, sleeps.size());
            SleepStat top = sleeps.getFirst();
            assertEquals("main", top.thread());
            assertEquals(2, top.count());
            assertEquals(Duration.ofMillis(100).toNanos(), top.totalSleptNanos());
            assertEquals(Duration.ofMillis(60).toNanos(), top.maxSleptNanos());
            assertEquals(Duration.ofMillis(87).toNanos(), top.requestedNanos());
        }
    }

    @Nested
    @DisplayName("overview()")
    class Overview {

        @Test
        @DisplayName("All has-flags false when no blocking events exist")
        void allFlagsFalseWhenEmpty() {
            when(eventRepository.containsEventType(any())).thenReturn(false);

            var blocking = manager().overview();
            assertEquals(0, blocking.waitCount());
            assertEquals(0, blocking.sleepCount());
            assertFalse(blocking.hasMonitorEnter());
            assertFalse(blocking.hasParks());
            assertFalse(blocking.hasPinned());
            assertFalse(blocking.hasMonitorWaits());
        }
    }
}
