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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.test.DuckDBTest;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Writes a representative set of events (null optionals, quoted/unicode JSON, zero/negative
 * hashes, duplicates, multiple full batches plus a partial final flush) through the Arrow
 * columnar path and asserts that the rows read back from DuckDB match the expected values,
 * including the JSON round-trip of the {@code fields} column.
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class DuckDBArrowEventWriterTest {

    /**
     * Small batch size to force multiple flushes plus a final partial flush on close.
     */
    private static final int TEST_BATCH_SIZE = 4;

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private static final long MICROS_PER_SECOND = 1_000_000L;
    private static final long NANOS_PER_MICRO = 1_000L;

    private static final String SELECT_EVENTS_SQL = """
            SELECT event_type, epoch_us(start_timestamp), duration, samples, weight,
                   weight_entity, stacktrace_hash, thread_hash, CAST(fields AS VARCHAR)
            FROM events""";

    private record EventRow(
            String eventType,
            long startTimestampMicros,
            Long duration,
            long samples,
            Long weight,
            String weightEntity,
            Long stacktraceHash,
            Long threadHash,
            String fieldsJson) {
    }

    private static final Comparator<EventRow> ROW_ORDER = Comparator
            .comparing(EventRow::eventType)
            .thenComparing(EventRow::startTimestampMicros)
            .thenComparing(EventRow::samples)
            .thenComparing(EventRow::duration, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(EventRow::weight, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(EventRow::weightEntity, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(EventRow::stacktraceHash, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(EventRow::threadHash, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(EventRow::fieldsJson, Comparator.nullsFirst(Comparator.naturalOrder()));

    @Test
    void arrowPathPersistsRepresentativeEventsExactly(DataSource dataSource) throws Exception {
        List<Event> events = representativeEvents();

        DuckDBArrowEventWriter arrowWriter =
                new DuckDBArrowEventWriter(DIRECT_EXECUTOR, dataSource, TEST_BATCH_SIZE);
        for (Event event : events) {
            arrowWriter.insert(event);
        }
        arrowWriter.close();

        List<EventRow> expectedRows = new ArrayList<>(events.stream()
                .map(DuckDBArrowEventWriterTest::expectedRow)
                .toList());
        List<EventRow> actualRows = readEvents(dataSource);

        assertEquals(expectedRows.size(), actualRows.size(), "Arrow path must persist every inserted event");

        expectedRows.sort(ROW_ORDER);
        actualRows.sort(ROW_ORDER);

        for (int i = 0; i < actualRows.size(); i++) {
            assertRowEquals(expectedRows.get(i), actualRows.get(i), i);
        }
    }

    private static void assertRowEquals(EventRow expected, EventRow actual, int index) {
        assertEquals(expected.eventType(), actual.eventType(), "event_type mismatch at row " + index);
        assertEquals(expected.startTimestampMicros(), actual.startTimestampMicros(), "start_timestamp mismatch at row " + index);
        assertEquals(expected.duration(), actual.duration(), "duration mismatch at row " + index);
        assertEquals(expected.samples(), actual.samples(), "samples mismatch at row " + index);
        assertEquals(expected.weight(), actual.weight(), "weight mismatch at row " + index);
        assertEquals(expected.weightEntity(), actual.weightEntity(), "weight_entity mismatch at row " + index);
        assertEquals(expected.stacktraceHash(), actual.stacktraceHash(), "stacktrace_hash mismatch at row " + index);
        assertEquals(expected.threadHash(), actual.threadHash(), "thread_hash mismatch at row " + index);
        assertJsonEquals(expected.fieldsJson(), actual.fieldsJson(), index);
    }

    private static void assertJsonEquals(String expectedJson, String actualJson, int index) {
        if (expectedJson == null) {
            assertNull(actualJson, "fields mismatch at row " + index);
            return;
        }
        // The DuckDB JSON column may normalize whitespace — compare the documents semantically.
        assertEquals(Json.readTree(expectedJson), Json.readTree(actualJson), "fields mismatch at row " + index);
    }

    /**
     * The expected database row for an inserted event: timestamps are stored with microsecond
     * precision, the {@code fields} document round-trips through the JSON column.
     */
    private static EventRow expectedRow(Event event) {
        return new EventRow(
                event.eventType(),
                toEpochMicros(event.startTimestamp()),
                event.duration(),
                event.samples(),
                event.weight(),
                event.weightEntity(),
                event.stacktraceId(),
                event.threadId(),
                event.fields() != null ? event.fields().toString() : null);
    }

    private static long toEpochMicros(Instant instant) {
        return instant.getEpochSecond() * MICROS_PER_SECOND + instant.getNano() / NANOS_PER_MICRO;
    }

    private static List<Event> representativeEvents() {
        ObjectNode quotedUnicodeFields = Json.createObject();
        quotedUnicodeFields.put("message", "he said \"hello\" — žluťoučký kůň 🦆");
        quotedUnicodeFields.put("path", "C:\\temp\\file.jfr");
        quotedUnicodeFields.put("value", 42L);

        ObjectNode nestedFields = Json.createObject();
        nestedFields.put("state", "RUNNABLE");
        nestedFields.putObject("nested").put("inner", "va'lue");

        Instant base = Instant.parse("2026-01-15T10:30:00.123456Z");

        List<Event> events = new ArrayList<>();
        // All-null optionals
        events.add(new Event("jdk.ExecutionSample", base, null, 1L, null, null, null, null, null));
        // All optionals present
        events.add(new Event("jdk.ObjectAllocationSample", base.plusMillis(1), 250_000L, 3L,
                4096L, "java.lang.String", 123456789L, 42L, nestedFields));
        // JSON fields with quotes and unicode
        events.add(new Event("jdk.ThreadPark", base.plusMillis(2), 1_000L, 1L,
                1L, "entity-\"quoted\"", 1L, 1L, quotedUnicodeFields));
        // Zero hashes
        events.add(new Event("jdk.JavaMonitorEnter", base.plusMillis(3), 0L, 1L,
                0L, "", 0L, 0L, Json.createObject()));
        // Negative hashes
        events.add(new Event("jdk.SocketRead", base.plusMillis(4), 99L, 2L,
                -8192L, "socket", -987654321987L, -1L, null));
        // Duplicate rows (inserted twice, both copies must be preserved)
        Event duplicate = new Event("jdk.GCPhasePause", base.plusMillis(5), 77L, 1L,
                null, null, 555L, 666L, null);
        events.add(duplicate);
        events.add(duplicate);
        // A couple more rows to cross the batch-size boundary and leave a partial final batch
        events.add(new Event("jdk.ExecutionSample", base.plusMillis(6), null, 1L, null, null, 777L, 888L, null));
        events.add(new Event("jdk.ExecutionSample", base.plusMillis(7), null, 1L, null, null, 777L, 888L, null));
        return events;
    }

    private static List<EventRow> readEvents(DataSource dataSource) throws SQLException {
        List<EventRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SELECT_EVENTS_SQL)) {

            while (resultSet.next()) {
                rows.add(new EventRow(
                        resultSet.getString(1),
                        resultSet.getLong(2),
                        readNullableLong(resultSet, 3),
                        resultSet.getLong(4),
                        readNullableLong(resultSet, 5),
                        resultSet.getString(6),
                        readNullableLong(resultSet, 7),
                        readNullableLong(resultSet, 8),
                        resultSet.getString(9)));
            }
        }
        return rows;
    }

    private static Long readNullableLong(ResultSet resultSet, int columnIndex) throws SQLException {
        long value = resultSet.getLong(columnIndex);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }
}
