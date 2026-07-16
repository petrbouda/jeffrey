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

package cafe.jeffrey.pprofparser;

import com.google.perftools.profiles.ProfileProto.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.provider.profile.jdbc.DuckDBEventWriters;
import cafe.jeffrey.provider.profile.jdbc.DuckDBSQLFormatter;
import cafe.jeffrey.provider.profile.jdbc.JdbcProfileEventTypeRepository;
import cafe.jeffrey.provider.profile.jdbc.SQLEventWriter;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;

import javax.sql.DataSource;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end round trip of a pprof recording through the real DuckDB event-writing pipeline:
 * gzip'd {@code .pb.gz} file → {@link PprofRecordingEventParser} → {@code SQLEventWriter} → profile
 * schema (events / event_types / stacktraces / frames / threads tables).
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class PprofRecordingRoundTripTest {

    private static final long BASE_TIME_NANOS = 1_752_000_000_000_000_000L;
    private static final int BATCH_SIZE = 100;

    @TempDir
    Path tempDir;

    private Profile cpuProfile() {
        PprofTestFixtures fixtures = new PprofTestFixtures()
                .sampleType("samples", "count")
                .sampleType("cpu", "nanoseconds")
                .time(BASE_TIME_NANOS, 1_000_000_000L);

        long main = fixtures.location("main.main", 30);
        long doWork = fixtures.location("main.doWork", 12);
        // leaf-first: doWork is the leaf, main the root
        fixtures.sample(
                List.of(doWork, main),
                List.of(4L, 8_000_000L),
                List.of(fixtures.numberLabel("tid", 7), fixtures.stringLabel("thread name", "worker-1")));
        return fixtures.build();
    }

    @Test
    void writesPprofRecordingIntoProfileDatabase(DataSource dataSource) throws SQLException {
        Path recording = writeGzipped(cpuProfile());

        Instant profilingStartedAt = Instant.ofEpochSecond(0, BASE_TIME_NANOS);
        EventWriter eventWriter = new SQLEventWriter(
                () -> new DuckDBEventWriters(Schedulers.sharedDbWriter(), dataSource, BATCH_SIZE, profilingStartedAt));

        new PprofRecordingEventParser().start(eventWriter, recording);
        eventWriter.onComplete();

        // one event per non-zero sample dimension
        assertCount(dataSource, "SELECT COUNT(*) FROM events WHERE event_type = 'pprof.samples'", 1);
        assertCount(dataSource, "SELECT COUNT(*) FROM events WHERE event_type = 'pprof.cpu'", 1);

        // samples dimension carries the count; cpu dimension carries the nanosecond weight
        assertCount(dataSource, "SELECT samples FROM events WHERE event_type = 'pprof.samples'", 4);
        assertCount(dataSource, "SELECT weight FROM events WHERE event_type = 'pprof.cpu'", 8_000_000);

        // relative timeline starts at zero (all events at the profile collection time)
        assertCount(dataSource,
                "SELECT MIN(start_timestamp_from_beginning) FROM events WHERE event_type = 'pprof.cpu'", 0);

        // both event types resolve their source to pprof (persisted as the source id)
        String pprofSourceId = String.valueOf(RecordingEventSource.PPROF.getId());
        assertCount(dataSource, "SELECT COUNT(*) FROM event_types WHERE source = '" + pprofSourceId + "'", 2);

        // one deduplicated stacktrace shared across the two dimensions, two native frames root-first
        assertCount(dataSource, "SELECT COUNT(*) FROM stacktraces", 1);
        assertCount(dataSource, "SELECT COUNT(*) FROM frames", 2);
        assertCount(dataSource,
                "SELECT COUNT(*) FROM frames WHERE class_name = 'main' AND method_name = 'main' "
                        + "AND frame_type = 'Native'", 1);
        assertCount(dataSource,
                "SELECT COUNT(*) FROM frames WHERE class_name = 'main' AND method_name = 'doWork' "
                        + "AND frame_type = 'Native'", 1);

        // the thread recovered from the sample labels survived the round trip
        assertCount(dataSource, "SELECT COUNT(*) FROM threads WHERE name = 'worker-1'", 1);

        // The flamegraph event-type listing must surface pprof types. The JFR-curated
        // eventSummaries(SUPPORTED_EVENTS) path filters them out (they are not JFR Type enum values),
        // so the all-types query — which allEventSummaries() wraps — is what makes them visible.
        JdbcProfileEventTypeRepository eventTypeRepository =
                new JdbcProfileEventTypeRepository(new DuckDBSQLFormatter(), new DatabaseClientProvider(dataSource));
        List<String> listedTypes = eventTypeRepository.eventSummaries().stream()
                .filter(summary -> summary.samples() > 0)
                .map(EventSummary::name)
                .toList();
        assertTrue(listedTypes.contains("pprof.cpu"), listedTypes.toString());
        assertTrue(listedTypes.contains("pprof.samples"), listedTypes.toString());
    }

    private Path writeGzipped(Profile profile) {
        Path recording = tempDir.resolve("recording.pb.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(recording))) {
            profile.writeTo(out);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write pprof fixture", e);
        }
        return recording;
    }

    private static void assertCount(DataSource dataSource, String sql, long expected) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            long actual = resultSet.getLong(1);
            if (actual != expected) {
                throw new AssertionError("Expected " + expected + " but got " + actual + " for: " + sql);
            }
        }
    }
}
