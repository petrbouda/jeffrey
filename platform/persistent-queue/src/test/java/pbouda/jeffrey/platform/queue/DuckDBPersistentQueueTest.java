/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.queue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/platform")
class DuckDBPersistentQueueTest {

    private static final Instant FIXED_TIME = Instant.parse("2025-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    private static class StringSerializer implements EventSerializer<String> {

        private final boolean withDedupKey;

        StringSerializer(boolean withDedupKey) {
            this.withDedupKey = withDedupKey;
        }

        @Override
        public String serialize(String event) {
            return event;
        }

        @Override
        public String deserialize(String payload) {
            return payload;
        }

        @Override
        public String dedupKey(String event) {
            return withDedupKey ? event : null;
        }
    }

    @Nested
    class AppendAndPoll {

        @Test
        void appendSingleEvent_pollReturnsIt(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), FIXED_CLOCK);

            queue.append("scope-1", "event-A");
            List<QueueEntry<String>> entries = queue.poll("scope-1", "consumer-1");

            assertAll(
                    () -> assertEquals(1, entries.size()),
                    () -> assertEquals("event-A", entries.getFirst().payload()),
                    () -> assertEquals(FIXED_TIME, entries.getFirst().createdAt())
            );
        }

        @Test
        void appendMultipleEvents_pollReturnsInOffsetOrder(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), FIXED_CLOCK);

            queue.append("scope-1", "event-A");
            queue.append("scope-1", "event-B");
            queue.append("scope-1", "event-C");

            List<QueueEntry<String>> entries = queue.poll("scope-1", "consumer-1");

            assertAll(
                    () -> assertEquals(3, entries.size()),
                    () -> assertEquals("event-A", entries.get(0).payload()),
                    () -> assertEquals("event-B", entries.get(1).payload()),
                    () -> assertEquals("event-C", entries.get(2).payload()),
                    () -> assertTrue(entries.get(0).offset() < entries.get(1).offset()),
                    () -> assertTrue(entries.get(1).offset() < entries.get(2).offset())
            );
        }

        @Test
        void pollReturnsEmpty_whenNoEvents(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), FIXED_CLOCK);

            List<QueueEntry<String>> entries = queue.poll("scope-1", "consumer-1");

            assertTrue(entries.isEmpty());
        }

        @Test
        void pollAutoCreatesConsumer_whenNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), FIXED_CLOCK);

            queue.append("scope-1", "event-A");

            // First poll with a new consumer should auto-create it and return events
            List<QueueEntry<String>> entries = queue.poll("scope-1", "new-consumer");

            assertEquals(1, entries.size());
            assertEquals("event-A", entries.getFirst().payload());
        }

        @Test
        void pollIsolatesByScope(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), FIXED_CLOCK);

            queue.append("scope-1", "event-from-scope-1");
            queue.append("scope-2", "event-from-scope-2");

            List<QueueEntry<String>> scope1Entries = queue.poll("scope-1", "consumer-1");
            List<QueueEntry<String>> scope2Entries = queue.poll("scope-2", "consumer-1");

            assertAll(
                    () -> assertEquals(1, scope1Entries.size()),
                    () -> assertEquals("event-from-scope-1", scope1Entries.getFirst().payload()),
                    () -> assertEquals(1, scope2Entries.size()),
                    () -> assertEquals("event-from-scope-2", scope2Entries.getFirst().payload())
            );
        }
    }

    @Nested
    class BatchAppend {

        @Test
        void appendBatch_insertsAllEvents(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), FIXED_CLOCK);

            queue.appendBatch("scope-1", List.of("event-A", "event-B", "event-C"));

            List<QueueEntry<String>> entries = queue.poll("scope-1", "consumer-1");

            assertAll(
                    () -> assertEquals(3, entries.size()),
                    () -> assertEquals("event-A", entries.get(0).payload()),
                    () -> assertEquals("event-B", entries.get(1).payload()),
                    () -> assertEquals("event-C", entries.get(2).payload())
            );
        }

        @Test
        void appendBatch_emptyListIsNoOp(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), FIXED_CLOCK);

            queue.appendBatch("scope-1", List.of());

            List<QueueEntry<String>> entries = queue.poll("scope-1", "consumer-1");

            assertTrue(entries.isEmpty());
        }

        @Test
        void appendBatch_skipsDuplicatesByDedupKey(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(true), FIXED_CLOCK);

            queue.appendBatch("scope-1", List.of("event-A", "event-B", "event-A"));

            List<QueueEntry<String>> entries = queue.findAll("scope-1");

            assertEquals(2, entries.size());
        }
    }

    @Nested
    class Acknowledge {

        @Test
        void acknowledge_advancesConsumerOffset(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), FIXED_CLOCK);

            queue.append("scope-1", "event-A");
            queue.append("scope-1", "event-B");

            List<QueueEntry<String>> firstPoll = queue.poll("scope-1", "consumer-1");
            long lastOffset = firstPoll.getLast().offset();
            queue.acknowledge("scope-1", "consumer-1", lastOffset);

            // After acknowledging all events, poll should return empty
            List<QueueEntry<String>> secondPoll = queue.poll("scope-1", "consumer-1");

            assertTrue(secondPoll.isEmpty());
        }

        @Test
        void afterAcknowledge_pollReturnsOnlyNewerEvents(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), FIXED_CLOCK);

            queue.append("scope-1", "event-A");
            queue.append("scope-1", "event-B");

            List<QueueEntry<String>> firstPoll = queue.poll("scope-1", "consumer-1");
            long firstOffset = firstPoll.getFirst().offset();
            queue.acknowledge("scope-1", "consumer-1", firstOffset);

            // Add a new event
            queue.append("scope-1", "event-C");

            List<QueueEntry<String>> secondPoll = queue.poll("scope-1", "consumer-1");

            assertAll(
                    () -> assertEquals(2, secondPoll.size()),
                    () -> assertEquals("event-B", secondPoll.get(0).payload()),
                    () -> assertEquals("event-C", secondPoll.get(1).payload())
            );
        }

        @Test
        void acknowledge_independentPerConsumer(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), FIXED_CLOCK);

            queue.append("scope-1", "event-A");
            queue.append("scope-1", "event-B");

            List<QueueEntry<String>> consumer1Poll = queue.poll("scope-1", "consumer-1");
            long lastOffset = consumer1Poll.getLast().offset();
            queue.acknowledge("scope-1", "consumer-1", lastOffset);

            // consumer-2 has not acknowledged anything, should still see all events
            List<QueueEntry<String>> consumer2Poll = queue.poll("scope-1", "consumer-2");

            assertAll(
                    () -> assertTrue(queue.poll("scope-1", "consumer-1").isEmpty()),
                    () -> assertEquals(2, consumer2Poll.size())
            );
        }
    }

    @Nested
    class MultiConsumer {

        @Test
        void twoConsumers_trackOwnOffsets(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), FIXED_CLOCK);

            queue.append("scope-1", "event-A");
            queue.append("scope-1", "event-B");
            queue.append("scope-1", "event-C");

            // consumer-1 acknowledges first event
            List<QueueEntry<String>> poll1 = queue.poll("scope-1", "consumer-1");
            queue.acknowledge("scope-1", "consumer-1", poll1.getFirst().offset());

            // consumer-2 acknowledges first two events
            List<QueueEntry<String>> poll2 = queue.poll("scope-1", "consumer-2");
            queue.acknowledge("scope-1", "consumer-2", poll2.get(1).offset());

            List<QueueEntry<String>> consumer1Remaining = queue.poll("scope-1", "consumer-1");
            List<QueueEntry<String>> consumer2Remaining = queue.poll("scope-1", "consumer-2");

            assertAll(
                    () -> assertEquals(2, consumer1Remaining.size()),
                    () -> assertEquals("event-B", consumer1Remaining.get(0).payload()),
                    () -> assertEquals("event-C", consumer1Remaining.get(1).payload()),
                    () -> assertEquals(1, consumer2Remaining.size()),
                    () -> assertEquals("event-C", consumer2Remaining.getFirst().payload())
            );
        }

        @Test
        void slowConsumer_stillSeesEventsProcessedByFast(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), FIXED_CLOCK);

            queue.append("scope-1", "event-A");
            queue.append("scope-1", "event-B");

            // Fast consumer processes everything
            List<QueueEntry<String>> fastPoll = queue.poll("scope-1", "fast-consumer");
            queue.acknowledge("scope-1", "fast-consumer", fastPoll.getLast().offset());

            // Slow consumer has not polled yet, should still see all events
            List<QueueEntry<String>> slowPoll = queue.poll("scope-1", "slow-consumer");

            assertAll(
                    () -> assertEquals(2, slowPoll.size()),
                    () -> assertEquals("event-A", slowPoll.get(0).payload()),
                    () -> assertEquals("event-B", slowPoll.get(1).payload())
            );
        }
    }

    @Nested
    class Deduplication {

        @Test
        void duplicateDedupKey_silentlyIgnored(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(true), FIXED_CLOCK);

            queue.append("scope-1", "event-A");
            queue.append("scope-1", "event-A");
            queue.append("scope-1", "event-A");

            List<QueueEntry<String>> entries = queue.findAll("scope-1");

            assertEquals(1, entries.size());
            assertEquals("event-A", entries.getFirst().payload());
        }

        @Test
        void nullDedupKey_allowsDuplicatePayloads(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), FIXED_CLOCK);

            queue.append("scope-1", "event-A");
            queue.append("scope-1", "event-A");
            queue.append("scope-1", "event-A");

            List<QueueEntry<String>> entries = queue.findAll("scope-1");

            assertEquals(3, entries.size());
        }

        @Test
        void dedupScopedByQueueNameAndScopeId(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue1 = new DuckDBPersistentQueue<>(provider, "queue-1", new StringSerializer(true), FIXED_CLOCK);
            var queue2 = new DuckDBPersistentQueue<>(provider, "queue-2", new StringSerializer(true), FIXED_CLOCK);

            // Same dedup key across different queues and scopes should all be stored
            queue1.append("scope-1", "event-A");
            queue1.append("scope-2", "event-A");
            queue2.append("scope-1", "event-A");

            List<QueueEntry<String>> queue1Scope1 = queue1.findAll("scope-1");
            List<QueueEntry<String>> queue1Scope2 = queue1.findAll("scope-2");
            List<QueueEntry<String>> queue2Scope1 = queue2.findAll("scope-1");

            assertAll(
                    () -> assertEquals(1, queue1Scope1.size()),
                    () -> assertEquals(1, queue1Scope2.size()),
                    () -> assertEquals(1, queue2Scope1.size())
            );
        }
    }

    @Nested
    class DeleteEventsOlderThan {

        private static final Instant OLD_TIME = Instant.parse("2025-05-01T10:00:00Z");
        private static final Clock OLD_CLOCK = Clock.fixed(OLD_TIME, ZoneOffset.UTC);
        private static final Instant RECENT_TIME = Instant.parse("2025-06-25T10:00:00Z");
        private static final Clock RECENT_CLOCK = Clock.fixed(RECENT_TIME, ZoneOffset.UTC);
        private static final Instant CUTOFF = Instant.parse("2025-05-30T00:00:00Z");

        private static long countQueueEvents(DataSource dataSource) {
            var jdbc = new NamedParameterJdbcTemplate(dataSource);
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM persistent_queue_events", Map.of(), Long.class);
            return count != null ? count : 0;
        }

        @Test
        void deletesOldEvents_andReturnsCount(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);

            // Insert 2 old events
            var oldQueue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), OLD_CLOCK);
            oldQueue.append("scope-1", "old-event-1");
            oldQueue.append("scope-1", "old-event-2");

            // Insert 1 recent event
            var recentQueue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), RECENT_CLOCK);
            recentQueue.append("scope-1", "recent-event");

            assertEquals(3, countQueueEvents(dataSource));

            int deleted = recentQueue.deleteEventsOlderThan(CUTOFF);

            assertEquals(2, deleted);
            assertEquals(1, countQueueEvents(dataSource));
        }

        @Test
        void returnsZero_whenNoOldEvents(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), RECENT_CLOCK);
            queue.append("scope-1", "recent-event");

            int deleted = queue.deleteEventsOlderThan(CUTOFF);

            assertEquals(0, deleted);
        }

        @Test
        void returnsZero_whenTableEmpty(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var queue = new DuckDBPersistentQueue<>(provider, "test-queue", new StringSerializer(false), FIXED_CLOCK);

            int deleted = queue.deleteEventsOlderThan(CUTOFF);

            assertEquals(0, deleted);
        }
    }
}
