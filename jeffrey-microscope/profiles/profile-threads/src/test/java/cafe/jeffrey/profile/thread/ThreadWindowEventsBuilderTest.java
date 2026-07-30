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
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.jfrparser.api.type.JfrThreadImpl;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadWindowEventsBuilderTest {

    private static final Instant RECORDING_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final RelativeTimeRange WINDOW =
            new RelativeTimeRange(Duration.ofMillis(100), Duration.ofMillis(101));

    private static GenericRecord socketRead(long startMillis, String host, long bytesRead) {
        ObjectNode fields = JsonNodeFactory.instance.objectNode();
        fields.put("host", host);
        fields.put("address", "10.23.88.52");
        fields.put("port", 1521);
        fields.put("bytesRead", bytesRead);
        fields.put("endOfStream", false);

        Duration start = Duration.ofMillis(startMillis);
        return new GenericRecord(
                Type.SOCKET_READ,
                null,
                RECORDING_START.plus(start),
                start,
                Duration.ofNanos(1_658_000),
                new JfrThreadImpl(1010, 10, "http-nio-8080-exec-10", false),
                null,
                1,
                0,
                fields);
    }

    private static ThreadWindowEvents build(int limit, List<GenericRecord> records) {
        ThreadWindowEventsBuilder builder = new ThreadWindowEventsBuilder(WINDOW, limit);
        records.forEach(builder::onRecord);
        return builder.build();
    }

    @Nested
    class Counting {

        /**
         * The whole point of the window lookup: a tooltip renders one event but has to say how many
         * there were. Capping the count at the sample size is the bug this replaced.
         */
        @Test
        void countsEveryRecordWhileSamplingOnlyTheLimit() {
            List<GenericRecord> records = List.of(
                    socketRead(100, "db-1.internal", 1024),
                    socketRead(100, "db-2.internal", 2048),
                    socketRead(100, "db-3.internal", 4096));

            ThreadWindowEvents events = build(1, records);

            assertEquals(3, events.eventCount());
            assertEquals(1, events.events().size());
        }

        @Test
        void returnsEverythingWhenTheLimitIsLargerThanTheWindow() {
            ThreadWindowEvents events = build(20, List.of(socketRead(100, "db-1.internal", 1024)));

            assertEquals(1, events.eventCount());
            assertEquals(1, events.events().size());
        }

        @Test
        void reportsAnEmptyWindowAsNoEventsRatherThanFailing() {
            ThreadWindowEvents events = build(1, List.of());

            assertEquals(0, events.eventCount());
            assertTrue(events.events().isEmpty());
        }

        /**
         * The window that comes back is the one that was searched, so a tooltip can label the slice
         * it is describing instead of the one the pointer asked for.
         */
        @Test
        void echoesTheWindowItSearched() {
            ThreadWindowEvents events = build(1, List.of());

            assertEquals(Duration.ofMillis(100).toNanos(), events.fromOffset());
            assertEquals(Duration.ofMillis(101).toNanos(), events.toOffset());
        }
    }

    @Nested
    class Fields {

        /**
         * The values are positional and line up with the metadata the frontend renders them against,
         * so their order is a contract rather than an implementation detail.
         */
        @Test
        void areOrderedAsTheMetadataDeclaresThem() {
            ThreadWindowEvents events = build(1, List.of(socketRead(100, "db-1.internal", 1024)));

            List<Object> values = events.events().getFirst().values();
            assertEquals(1_658_000L, values.get(0));
            assertEquals("db-1.internal", values.get(1));
            assertEquals("10.23.88.52", values.get(2));
            assertEquals(1521L, values.get(3));
            assertNull(values.get(4), "The recording captured no timeout");
            assertEquals(1024L, values.get(5));
            assertEquals(false, values.get(6));
        }

        /**
         * A field the recording did not capture has to stay in place as a hole. Dropping it would
         * shift every later value onto the wrong label.
         */
        @Test
        void keepAGapForAFieldTheRecordingDidNotCapture() {
            ThreadWindowEvents events = build(1, List.of(socketRead(100, "db-1.internal", 1024)));

            assertEquals(7, events.events().getFirst().values().size());
        }
    }
}
