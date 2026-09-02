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
package cafe.jeffrey.profile.heapdump.parser;

import cafe.jeffrey.jfr.events.test.JfrRecordings;
import cafe.jeffrey.jfr.events.test.SpansAssert;
import cafe.jeffrey.jfr.events.trace.Tracer;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexDb;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpStatement;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what {@link HprofNonPkIndexes} reports about itself.
 * <p>
 * The index build's longest sub-phase used to be a single opaque bar: {@code createAll}'s parallel
 * path issued its DDL on raw {@link java.sql.Statement}s over worker connections, so nothing was
 * emitted and a reader could not tell which of the eight indexes the time went to. These tests pin
 * both halves of the fix — that a statement event is emitted at all, and that it lands under the
 * phase's span rather than floating loose on a worker thread, which is the part a plain executor
 * gets wrong because a span lives in a {@code ScopedValue}.
 */
class HprofNonPkIndexesTest {

    private static final String PHASE_SPAN = "create_indexes";
    private static final String SPAN_EVENT = "jeffrey.TraceSpan";
    private static final String STATEMENT_EVENT = "jeffrey.JdbcExecute";

    /** Every index the class manages, which is what both paths have to end up having created. */
    private static final Set<String> EXPECTED_INDEXES = Set.of(
            "idx_outbound_source", "idx_outbound_target", "idx_instance_id", "idx_instance_class",
            "idx_string_content_instance", "idx_gc_root_instance", "idx_class_name",
            "idx_class_super", "idx_class_is_array", "idx_stack_trace_frame_thread");

    /**
     * Runs {@code createAll} inside a span named like the real phase, with a recording running.
     *
     * @param workers passed straight through, so a test can choose the sequential or the parallel
     *                path — the whole point being that the two must be indistinguishable from the
     *                outside
     */
    private static List<RecordedEvent> recordCreateAll(Path dbPath, int workers)
            throws IOException, SQLException {

        try (HeapDumpIndexDb db = HeapDumpIndexDb.openAndInitialize(dbPath)) {
            return JfrRecordings.all(
                    Set.of(SPAN_EVENT, STATEMENT_EVENT),
                    () -> Tracer.run(PHASE_SPAN, () ->
                            HprofNonPkIndexes.createAll(db.databaseClient(), dbPath, workers)));
        }
    }

    private static Set<String> indexNamesIn(Path dbPath) throws SQLException {
        try (HeapDumpIndexDb db = HeapDumpIndexDb.openAndInitialize(dbPath)) {
            return Set.copyOf(db.databaseClient().queryList(
                    HeapDumpStatement.CREATE_INDEXES,
                    "SELECT index_name FROM duckdb_indexes()",
                    rs -> rs.getString(1)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    class ParallelPath {

        @Test
        void emitsOneStatementEventPerIndex(@TempDir Path dir) throws Exception {
            List<RecordedEvent> events = recordCreateAll(dir.resolve("index.duckdb"), 8);

            long statements = events.stream()
                    .filter(event -> STATEMENT_EVENT.equals(event.getEventType().getName()))
                    .count();

            assertEquals(EXPECTED_INDEXES.size(), statements,
                    "one JdbcExecute per CREATE INDEX -- this is what the phase could not say before");
        }

        @Test
        void nestsEveryWorkerUnderThePhaseSpan(@TempDir Path dir) throws Exception {
            List<RecordedEvent> events = recordCreateAll(dir.resolve("index.duckdb"), 8);

            // The assertion that matters, and the failure it guards is quieter than "no events":
            // without Tracer.fork every statement is still emitted, but with traceId=0 and spanId=0,
            // because a ScopedValue does not cross a plain executor. The derivation drops an
            // untraced event, so the phase stays a single bar however many events it committed.
            SpansAssert.assertThat(events)
                    .hasNoUntracedSpans()
                    .hasSpan("create_indexes_outbound_ref").nestedUnder(PHASE_SPAN).and()
                    .hasSpan("create_indexes_instance").nestedUnder(PHASE_SPAN).and()
                    .hasSpan("create_indexes_string_content").nestedUnder(PHASE_SPAN).and()
                    .hasSpan("create_indexes_gc_root").nestedUnder(PHASE_SPAN).and()
                    .hasSpan("create_indexes_class").nestedUnder(PHASE_SPAN).and()
                    .hasSpan("create_indexes_stack_trace_frame").nestedUnder(PHASE_SPAN);
        }

        @Test
        void createsEveryIndex(@TempDir Path dir) throws Exception {
            Path dbPath = dir.resolve("index.duckdb");
            recordCreateAll(dbPath, 8);

            assertTrue(indexNamesIn(dbPath).containsAll(EXPECTED_INDEXES),
                    "instrumenting the phase must not change what it builds");
        }
    }

    @Nested
    class SequentialPath {

        /**
         * One worker takes a different branch entirely — the caller's own client on the caller's own
         * thread. It has always emitted its statements; the test is here so the two paths cannot
         * drift into reporting differently.
         */
        @Test
        void emitsTheSameStatementEvents(@TempDir Path dir) throws Exception {
            List<RecordedEvent> events = recordCreateAll(dir.resolve("index.duckdb"), 1);

            long statements = events.stream()
                    .filter(event -> STATEMENT_EVENT.equals(event.getEventType().getName()))
                    .count();

            assertEquals(EXPECTED_INDEXES.size(), statements);
        }

        @Test
        void createsEveryIndex(@TempDir Path dir) throws Exception {
            Path dbPath = dir.resolve("index.duckdb");
            recordCreateAll(dbPath, 1);

            assertTrue(indexNamesIn(dbPath).containsAll(EXPECTED_INDEXES));
        }
    }
}
