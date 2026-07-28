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

package cafe.jeffrey.profile.thread;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.jfrparser.api.type.JfrThreadImpl;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.Type;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DbBasedThreadProviderTest {

    private static final Instant RECORDING_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration RECORDING_LENGTH = Duration.ofSeconds(4);

    @Mock
    ProfileEventRepository eventRepository;

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    /**
     * Drives the record builder with the given events, the way the streaming repository drives it
     * with rows from the database.
     */
    private void streams(List<GenericRecord> events) {
        when(eventStreamRepository.genericStreaming(any(EventQueryConfigurer.class), any()))
                .thenAnswer(invocation -> {
                    RecordBuilder<GenericRecord, ?> builder = invocation.getArgument(1);
                    events.forEach(builder::onRecord);
                    return builder.build();
                });
    }

    private static GenericRecord event(Type type, long javaId, Duration start, Duration duration) {
        return new GenericRecord(
                type,
                null,
                RECORDING_START.plus(start),
                start,
                duration,
                new JfrThreadImpl(javaId + 1000, javaId, "worker-" + javaId, false),
                null,
                1,
                0,
                null);
    }

    private ThreadRoot rootOf(List<GenericRecord> events) {
        streams(events);
        ProfileInfo profileInfo = new ProfileInfo(
                "p-1",
                "project-1",
                "workspace-1",
                "recording",
                RecordingEventSource.JDK,
                RECORDING_START,
                RECORDING_START.plus(RECORDING_LENGTH),
                RECORDING_START,
                true,
                false,
                "rec-1");

        return new DbBasedThreadProvider(profileInfo, eventRepository, eventStreamRepository).get();
    }

    @Nested
    class ThreadRows {

        /**
         * The timeline query no longer asks for JSON fields, so the rows have to be reconstructed
         * from the event's own columns alone. An empty timeline is what a regression here looks like.
         */
        @Test
        void areBuiltFromEventsWithoutJsonFields() {
            ThreadRoot root = rootOf(List.of(
                    event(Type.THREAD_START, 1, Duration.ZERO, null),
                    event(Type.FILE_WRITE, 1, Duration.ofMillis(100), Duration.ofMillis(2)),
                    event(Type.FILE_WRITE, 1, Duration.ofMillis(2000), Duration.ofMillis(2)),
                    event(Type.JAVA_MONITOR_ENTER, 2, Duration.ofMillis(500), Duration.ofMillis(5))));

            assertEquals(2, root.rows().size(), "Both threads must produce a row");

            ThreadRow writer = rowOf(root, 1);
            assertEquals(2, writer.eventsCount(), "Event count reports events, not bands");
            assertEquals(2, writer.fileWrite().size(), "Two writes two seconds apart stay two bands");
            assertTrue(writer.lifespan().size() >= 1, "A started thread has a lifespan");

            ThreadRow blocked = rowOf(root, 2);
            assertEquals(1, blocked.blocked().size());
            assertEquals(1, blocked.blocked().getFirst().eventCount());
        }

        /**
         * Instantaneous events carry no duration; reading one as if it had a length would fail the
         * whole request, and the timeline would come back empty.
         */
        @Test
        void surviveEventsWithoutADuration() {
            ThreadRoot root = rootOf(List.of(
                    event(Type.THREAD_START, 1, Duration.ZERO, null),
                    event(Type.THREAD_END, 1, Duration.ofMillis(50), null)));

            ThreadRow row = rowOf(root, 1);
            assertEquals(1, row.lifespan().size());
            assertEquals(0, row.eventsCount());
        }

        @Test
        void mergeABurstIntoASingleBand() {
            List<GenericRecord> burst = java.util.stream.IntStream.range(0, 500)
                    .mapToObj(i -> event(Type.FILE_WRITE, 1, Duration.ofNanos(i * 1_000L), Duration.ofNanos(500)))
                    .toList();

            ThreadRow row = rowOf(rootOf(burst), 1);

            assertEquals(1, row.fileWrite().size(), "A burst is a single rectangle on screen");
            assertEquals(500, row.fileWrite().getFirst().eventCount());
            assertEquals(500, row.eventsCount());
        }

        private ThreadRow rowOf(ThreadRoot root, long javaId) {
            return root.rows().stream()
                    .filter(row -> row.threadInfo().javaId() == javaId)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No row for thread: " + javaId));
        }
    }
}
