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
import cafe.jeffrey.profile.manager.model.io.IoEndpoint;
import cafe.jeffrey.profile.manager.model.io.IoKind;
import cafe.jeffrey.profile.manager.model.io.IoOperation;
import cafe.jeffrey.profile.manager.model.io.IoOverview;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IoManagerImpl")
class IoManagerImplTest {

    @Mock
    ProfileEventRepository eventRepository;

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-01-01T00:01:00Z");

    private static final ProfileInfo PROFILE_INFO = new ProfileInfo(
            "test-id", "project-1", "workspace-1", "test-profile",
            null, START, END, START, true, false, null);

    private IoManagerImpl manager() {
        return new IoManagerImpl(PROFILE_INFO, eventRepository, eventStreamRepository);
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

    private static ObjectNode socketFields(String host, long port, long bytes, boolean read) {
        ObjectNode node = Json.createObject();
        node.put("host", host);
        node.put("port", port);
        node.put(read ? "bytesRead" : "bytesWritten", bytes);
        node.put("eventThread", "worker");
        return node;
    }

    private static ObjectNode fileFields(String path, long bytes, boolean read) {
        ObjectNode node = Json.createObject();
        node.put("path", path);
        node.put(read ? "bytesRead" : "bytesWritten", bytes);
        node.put("eventThread", "worker");
        return node;
    }

    @Nested
    @DisplayName("overview(kind)")
    class Overview {

        @Test
        @DisplayName("Sums read/written bytes, op count and slowest operation for the kind")
        void aggregates() {
            stubStreaming(
                    record(Type.SOCKET_READ, 1, Duration.ofMillis(10), socketFields("h1", 80, 1000, true)),
                    record(Type.SOCKET_WRITE, 2, Duration.ofMillis(50), socketFields("h1", 80, 200, false)));

            IoOverview overview = manager().overview(IoKind.SOCKET);

            assertEquals(1000, overview.bytesRead());
            assertEquals(200, overview.bytesWritten());
            assertEquals(2, overview.opCount());
            assertEquals(Duration.ofMillis(50).toNanos(), overview.slowestNanos());
            assertEquals("h1:80", overview.slowestTarget());
            assertTrue(overview.hasEvents());
        }
    }

    @Nested
    @DisplayName("throughputTimeline(kind)")
    class Throughput {

        @Test
        @DisplayName("Separates read and write bytes per second")
        void splitsReadWrite() {
            stubStreaming(
                    record(Type.SOCKET_READ, 2, Duration.ofMillis(1), socketFields("h", 1, 1000, true)),
                    record(Type.SOCKET_WRITE, 2, Duration.ofMillis(1), socketFields("h", 1, 300, false)));

            TimeseriesData timeline = manager().throughputTimeline(IoKind.SOCKET);

            SingleSerie read = timeline.series().get(0);
            SingleSerie write = timeline.series().get(1);
            assertEquals("Bytes Read / sec", read.name());
            assertEquals(1000, valueAt(read, 2));
            assertEquals(300, valueAt(write, 2));
        }
    }

    @Nested
    @DisplayName("slowestOperations(kind)")
    class Slowest {

        @Test
        @DisplayName("Orders by duration desc with kind/target/bytes")
        void ordersByDuration() {
            stubStreaming(
                    record(Type.FILE_READ, 1, Duration.ofMillis(5), fileFields("/small", 10, true)),
                    record(Type.FILE_WRITE, 2, Duration.ofMillis(40), fileFields("/big", 99, false)));

            List<IoOperation> slowest = manager().slowestOperations(IoKind.FILE);

            assertEquals(2, slowest.size());
            assertEquals("File Write", slowest.getFirst().kind());
            assertEquals("/big", slowest.getFirst().target());
            assertEquals(Duration.ofMillis(40).toNanos(), slowest.getFirst().durationNanos());
        }
    }

    @Nested
    @DisplayName("endpoints(kind)")
    class Endpoints {

        @Test
        @DisplayName("Groups sockets by host:port, ordered by bytes")
        void groupsPeers() {
            when(eventRepository.containsEventType(Type.SOCKET_READ)).thenReturn(true);
            stubStreaming(
                    record(Type.SOCKET_READ, 1, Duration.ofMillis(1), socketFields("a", 80, 100, true)),
                    record(Type.SOCKET_READ, 2, Duration.ofMillis(1), socketFields("a", 80, 400, true)),
                    record(Type.SOCKET_WRITE, 3, Duration.ofMillis(1), socketFields("b", 90, 50, false)));

            List<IoEndpoint> peers = manager().endpoints(IoKind.SOCKET);

            assertEquals(2, peers.size());
            assertEquals("a:80", peers.getFirst().target());
            assertEquals(500, peers.getFirst().bytes());
            assertEquals(2, peers.getFirst().opCount());
        }

        @Test
        @DisplayName("Returns empty without streaming when no events of the kind exist")
        void emptyWhenAbsent() {
            when(eventRepository.containsEventType(Type.FILE_READ)).thenReturn(false);
            when(eventRepository.containsEventType(Type.FILE_WRITE)).thenReturn(false);

            assertTrue(manager().endpoints(IoKind.FILE).isEmpty());
            verify(eventStreamRepository, never()).genericStreaming(any(), any());
        }
    }

    @Nested
    @DisplayName("directories()")
    class Directories {

        @Test
        @DisplayName("Groups file ops by parent directory, ordered by bytes")
        void groupsByDirectory() {
            when(eventRepository.containsEventType(Type.FILE_READ)).thenReturn(true);
            stubStreaming(
                    record(Type.FILE_READ, 1, Duration.ofMillis(1), fileFields("/var/log/a.log", 100, true)),
                    record(Type.FILE_WRITE, 2, Duration.ofMillis(1), fileFields("/var/log/b.log", 400, false)),
                    record(Type.FILE_READ, 3, Duration.ofMillis(1), fileFields("/tmp/c", 50, true)));

            List<IoEndpoint> directories = manager().directories();

            assertEquals(2, directories.size());
            assertEquals("/var/log", directories.getFirst().target());
            assertEquals(500, directories.getFirst().bytes());
            assertEquals(2, directories.getFirst().opCount());
        }

        @Test
        @DisplayName("Returns empty without streaming when no file events exist")
        void emptyWhenAbsent() {
            when(eventRepository.containsEventType(Type.FILE_READ)).thenReturn(false);
            when(eventRepository.containsEventType(Type.FILE_WRITE)).thenReturn(false);

            assertTrue(manager().directories().isEmpty());
            verify(eventStreamRepository, never()).genericStreaming(any(), any());
        }
    }

    private static long valueAt(SingleSerie serie, long second) {
        return serie.data().stream()
                .filter(point -> point.get(0) == second)
                .map(point -> point.get(1))
                .findFirst()
                .orElseThrow();
    }
}
