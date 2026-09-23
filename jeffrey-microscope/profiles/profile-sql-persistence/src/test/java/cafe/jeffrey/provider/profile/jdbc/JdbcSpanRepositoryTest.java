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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.ThreadWindowEventRecord;
import cafe.jeffrey.provider.profile.api.SpanRecord;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/profile")
class JdbcSpanRepositoryTest {

    private static final long MS = 1_000_000L;

    @Test
    void returnsOnlySpanEventsOrderedByStart(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-spans.sql");
        JdbcSpanRepository repository = new JdbcSpanRepository(new DatabaseClientProvider(dataSource));

        List<SpanRecord> spans = repository.listSpans();

        // The jdk.ExecutionSample row must be filtered out → exactly 3 spans.
        assertEquals(3, spans.size());

        SpanRecord first = spans.get(0);
        assertEquals(0, first.startMillisFromBeginning());
        assertEquals(300 * MS, first.durationNanos());
        assertEquals(2001, first.threadHash());
        assertEquals(41, first.osThreadId());
        assertEquals(12, first.javaThreadId());
        assertEquals("http-nio-exec-3", first.threadName());
        assertFalse(first.isVirtual());
        assertEquals("profile.initialize", first.tag());

        assertEquals("jfr.parse_and_ingest", spans.get(1).tag());
        assertEquals(100, spans.get(1).startMillisFromBeginning());

        SpanRecord third = spans.get(2);
        assertEquals("hprof.index.build", third.tag());
        assertEquals(2002, third.threadHash());
        assertEquals(71, third.osThreadId());
        assertEquals(2000, third.startMillisFromBeginning());
    }

    @Test
    void returnsEmptyWhenNoSpans(DataSource dataSource) {
        JdbcSpanRepository repository = new JdbcSpanRepository(new DatabaseClientProvider(dataSource));

        assertTrue(repository.listSpans().isEmpty());
    }

    @Test
    void eventsForThreadReturnsInWindowSameThreadExcludingSpans(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-thread-events.sql");
        JdbcSpanRepository repository = new JdbcSpanRepository(new DatabaseClientProvider(dataSource));

        long from = Instant.parse("2026-01-01T00:00:00.500Z").toEpochMilli();
        long to = Instant.parse("2026-01-01T00:00:05.000Z").toEpochMilli();

        // thread_hash 3001 = the 'worker' platform thread.
        List<ThreadWindowEventRecord> events = repository.eventsForThread(3001, from, to);

        // 2 samples + 1 monitor; excludes the span, the out-of-window sample, the other thread,
        // and the virtual thread.
        assertEquals(3, events.size());
        assertEquals("jdk.ExecutionSample", events.get(0).eventType());
        assertEquals("jdk.JavaMonitorEnter", events.get(1).eventType());
        assertEquals(3_000_000L, events.get(1).durationNanos());
        assertTrue(events.get(1).fields().contains("monitorClass"));
        assertEquals("jdk.ExecutionSample", events.get(2).eventType());
    }

    @Test
    void eventsForThreadResolvesVirtualThreadByHash(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-thread-events.sql");
        JdbcSpanRepository repository = new JdbcSpanRepository(new DatabaseClientProvider(dataSource));

        long from = Instant.parse("2026-01-01T00:00:00.500Z").toEpochMilli();
        long to = Instant.parse("2026-01-01T00:00:05.000Z").toEpochMilli();

        // thread_hash 3003 = the 'vt-worker' VIRTUAL thread (os_id NULL). Matching by thread_hash
        // resolves it; an os_id-based query never could. This is the regression guard for the fix.
        List<ThreadWindowEventRecord> events = repository.eventsForThread(3003, from, to);

        assertEquals(1, events.size());
        assertEquals("jdk.ExecutionSample", events.get(0).eventType());
    }

    @Test
    void eventsForThreadEmptyWhenNothingInWindow(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-thread-events.sql");
        JdbcSpanRepository repository = new JdbcSpanRepository(new DatabaseClientProvider(dataSource));

        long from = Instant.parse("2026-01-01T00:00:20.000Z").toEpochMilli();
        long to = Instant.parse("2026-01-01T00:00:30.000Z").toEpochMilli();

        assertTrue(repository.eventsForThread(3001, from, to).isEmpty());
    }
}
