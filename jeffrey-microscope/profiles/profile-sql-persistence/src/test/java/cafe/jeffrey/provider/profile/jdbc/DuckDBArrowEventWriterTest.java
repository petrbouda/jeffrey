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

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes the same representative set of events through the ARROW path and through the
 * APPENDER path into two databases and asserts row-for-row identical content.
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class DuckDBArrowEventWriterTest {

    private static final String PROFILE_MIGRATIONS_LOCATION = "classpath:db/migration/profile";

    /**
     * Small batch size to force multiple flushes plus a final partial flush on close.
     */
    private static final int TEST_BATCH_SIZE = 4;

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

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
    void arrowPathWritesIdenticalContentAsAppenderPath(DataSource arrowDataSource) throws Exception {
        assertTrue(ArrowRuntimeSupport.isAvailable(), "Arrow runtime must be available for this test");

        List<Event> events = representativeEvents();

        try (Connection appenderConnection = DriverManager.getConnection("jdbc:duckdb:")) {
            SingleConnectionDataSource appenderDataSource = new SingleConnectionDataSource(appenderConnection, true);
            migrate(appenderDataSource);

            DuckDBArrowEventWriter arrowWriter =
                    new DuckDBArrowEventWriter(DIRECT_EXECUTOR, arrowDataSource, TEST_BATCH_SIZE);
            for (Event event : events) {
                arrowWriter.insert(event);
            }
            arrowWriter.close();

            DuckDBEventWriter appenderWriter =
                    new DuckDBEventWriter(DIRECT_EXECUTOR, appenderDataSource, TEST_BATCH_SIZE);
            for (Event event : events) {
                appenderWriter.insert(event);
            }
            appenderWriter.close();

            List<EventRow> arrowRows = readEvents(arrowDataSource);
            List<EventRow> appenderRows = readEvents(appenderDataSource);

            assertEquals(events.size(), arrowRows.size(), "Arrow path must persist every inserted event");
            assertEquals(appenderRows.size(), arrowRows.size());

            arrowRows.sort(ROW_ORDER);
            appenderRows.sort(ROW_ORDER);

            for (int i = 0; i < arrowRows.size(); i++) {
                assertRowEquals(appenderRows.get(i), arrowRows.get(i), i);
            }

            appenderDataSource.destroy();
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
        // Compare the JSON round-trip semantically — both paths must produce an equal document.
        assertEquals(Json.readTree(expectedJson), Json.readTree(actualJson), "fields mismatch at row " + index);
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

    private static void migrate(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(PROFILE_MIGRATIONS_LOCATION)
                .load();
        flyway.migrate();
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
