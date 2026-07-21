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

package cafe.jeffrey.otlpparser;

import io.opentelemetry.proto.profiles.v1development.ProfilesData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.otlpparser.mapping.OtelSemconv;
import cafe.jeffrey.provider.profile.jdbc.DuckDBEventWriters;
import cafe.jeffrey.provider.profile.jdbc.SQLEventWriter;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.test.DuckDBTest;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end round trip of an OTLP recording through the real DuckDB event-writing pipeline:
 * {@code .otlp} file → {@link OtlpRecordingEventParser} → {@code SQLEventWriter} → profile schema
 * (events / event_types / stacktraces / frames / threads tables).
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class OtlpRecordingRoundTripTest {

    private static final long BASE_TIME_NANOS = 1_752_000_000_000_000_000L;
    private static final int BATCH_SIZE = 100;

    @TempDir
    Path tempDir;

    private ProfilesData cpuAndAllocFrame() {
        OtlpTestFixtures fixtures = new OtlpTestFixtures();
        fixtures.resourceAttribute(OtelSemconv.SERVICE_NAME, "checkout-service");

        int jvmFrameType = fixtures.stringAttribute(OtelSemconv.PROFILE_FRAME_TYPE, "jvm");
        int nativeFrameType = fixtures.stringAttribute(OtelSemconv.PROFILE_FRAME_TYPE, "native");

        int libc = fixtures.mapping("/usr/lib/libc.so.6");
        int jvmFunction = fixtures.function("com.example.Foo.doWork");
        int nativeFunction = fixtures.function("__libc_start_main");

        int jvmLocation = fixtures.location(0, jvmFunction, 42, jvmFrameType);
        int nativeLocation = fixtures.location(libc, nativeFunction, 0, nativeFrameType);
        int stack = fixtures.stack(List.of(jvmLocation, nativeLocation));

        int threadName = fixtures.stringAttribute(OtelSemconv.THREAD_NAME, "worker-1");
        int classAttr = fixtures.stringAttribute("class", "byte[]");

        fixtures.profile(fixtures.profileBuilder("cpu", "nanoseconds", BASE_TIME_NANOS)
                .addSamples(fixtures.sampleBuilder(stack)
                        .addAttributeIndices(threadName)
                        .addValues(10_000_000)
                        .addValues(10_000_000)
                        .addTimestampsUnixNano(BASE_TIME_NANOS)
                        .addTimestampsUnixNano(BASE_TIME_NANOS + 10_000_000))
                .build());

        fixtures.profile(fixtures.profileBuilder("alloc", "bytes", BASE_TIME_NANOS)
                .addSamples(fixtures.sampleBuilder(stack)
                        .addAttributeIndices(threadName)
                        .addAttributeIndices(classAttr)
                        .addValues(4096)
                        .addTimestampsUnixNano(BASE_TIME_NANOS + 5_000_000))
                .build());

        return fixtures.build();
    }

    @Test
    void writesOtlpRecordingIntoProfileDatabase(DataSource dataSource) throws SQLException {
        Path recording = tempDir.resolve("recording.otlp");
        OtlpTestFiles.writeFramed(recording, List.of(cpuAndAllocFrame()));

        Instant profilingStartedAt = Instant.ofEpochSecond(0, BASE_TIME_NANOS);
        EventWriter eventWriter = new SQLEventWriter(
                () -> new DuckDBEventWriters(Schedulers.sharedDbWriter(), dataSource, BATCH_SIZE, profilingStartedAt));

        new OtlpRecordingEventParser().start(eventWriter, recording);
        eventWriter.onComplete();

        assertEquals(2, count(dataSource, "SELECT COUNT(*) FROM events WHERE event_type = 'cpu'"));
        assertEquals(1, count(dataSource, "SELECT COUNT(*) FROM events WHERE event_type = 'alloc'"));

        // relative timeline starts at zero for the first cpu event
        assertEquals(0, count(dataSource,
                "SELECT MIN(start_timestamp_from_beginning) FROM events WHERE event_type = 'cpu'"));

        // both event types resolve their source to OpenTelemetry (persisted as the source id)
        String openTelemetrySourceId = String.valueOf(RecordingEventSource.OPEN_TELEMETRY.getId());
        assertEquals(2, count(dataSource,
                "SELECT COUNT(*) FROM event_types WHERE source = '" + openTelemetrySourceId + "'"));

        // one deduplicated stacktrace with two frames, root-first native -> jvm
        assertEquals(1, count(dataSource, "SELECT COUNT(*) FROM stacktraces"));
        assertEquals(2, count(dataSource, "SELECT COUNT(*) FROM frames"));
        assertEquals(1, count(dataSource, "SELECT COUNT(*) FROM threads WHERE name = 'worker-1'"));

        assertEquals(1, count(dataSource,
                "SELECT COUNT(*) FROM frames WHERE class_name = 'com.example.Foo' "
                        + "AND method_name = 'doWork' AND frame_type = 'JIT compiled'"));
        assertEquals(1, count(dataSource,
                "SELECT COUNT(*) FROM frames WHERE class_name = 'libc.so.6' AND frame_type = 'Native'"));

        // allocation weight and entity survived the round trip
        assertEquals(4096, count(dataSource,
                "SELECT weight FROM events WHERE event_type = 'alloc'"));
        assertTrue(queryString(dataSource,
                "SELECT weight_entity FROM events WHERE event_type = 'alloc'").contains("byte[]"));
    }

    private static long count(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static String queryString(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
