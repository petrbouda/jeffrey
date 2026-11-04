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

package cafe.jeffrey.profile.parser;

import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.RecordingSources;
import cafe.jeffrey.provider.profile.jdbc.BatchFlushLimit;
import cafe.jeffrey.provider.profile.jdbc.DuckDBEventWriters;
import cafe.jeffrey.provider.profile.jdbc.JdbcTraceAttributeRepository;
import cafe.jeffrey.provider.profile.jdbc.JdbcTraceRepository;
import cafe.jeffrey.provider.profile.jdbc.SQLEventWriter;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.recording.TestappRecording;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static cafe.jeffrey.test.recording.TestappRecording.DIRECT;
import static cafe.jeffrey.test.recording.TestappRecording.STARTUP_QUERY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The traces of a real recording, derived below the boot: {@link TestappRecording#DIRECT} parsed by the
 * real parser into an isolated profile database through the real writer, then the trace derivation run
 * over it — the contract between the two, with no Spring, no volume and no tool on top. A failure here
 * names the layer the booted tests cannot: what the parser stored, or what the derivation made of it.
 */
@DuckDBTest(migration = "classpath:db/migration/profile")
class TraceDerivationRealisticTest {

    /** High enough that the file is always split into its chunks. */
    private static final int ALWAYS_SPLIT = Integer.MAX_VALUE;
    private static final int BATCH_SIZE = 10_000;

    /** The JDK's blocking events promoted into leaf spans of the requests they happened in. */
    private static final int PROMOTED = 19;

    private static final List<String> DERIVED = List.of(
            "traces", "trace_spans", "trace_exceptions", "trace_span_payloads", "trace_span_attributes",
            "trace_attribute_values", "trace_attribute_keys");

    @TempDir
    Path work;

    @Test
    void theParsedRunDerivesOneTracePerRequestAndTheSameAgain(DataSource dataSource) throws IOException, SQLException {
        parse(dataSource);
        JdbcTraceRepository traces = new JdbcTraceRepository(new DatabaseClientProvider(dataSource));
        JdbcTraceAttributeRepository attributes = new JdbcTraceAttributeRepository(new DatabaseClientProvider(dataSource));

        traces.derive();
        attributes.derive();
        List<List<String>> first = digests(dataSource);

        TestappRecording.Traces expected = DIRECT.traces();
        assertEquals(String.valueOf(expected.exchanges()), cell(dataSource, "SELECT count(*) FROM traces"));
        assertEquals(spansByName(expected), spansByName(dataSource));
        assertEquals(String.valueOf(PROMOTED), cell(dataSource, "SELECT count(*) FROM trace_spans WHERE synthesized"));
        assertEquals("0", cell(dataSource, "SELECT count(*) FROM trace_spans c WHERE c.parent_span_id IS NOT NULL"
                + " AND NOT EXISTS (SELECT 1 FROM trace_spans p WHERE p.trace_id = c.trace_id AND p.span_id = c.parent_span_id)"));

        traces.derive();
        attributes.derive();
        assertEquals(first, digests(dataSource), "a second derivation of the same rows changes nothing");
    }

    private void parse(DataSource dataSource) throws IOException {
        Path file = Files.write(work.resolve(TestappRecording.RECORDING_FILE), DIRECT.bytes());
        TempDirFactory scratch = TempDirFactory.of(work.resolve("scratch"));
        EventWriter writer = new SQLEventWriter(() -> new DuckDBEventWriters(
                Schedulers.sharedDbWriter(), dataSource, BATCH_SIZE, DIRECT.recordingStartedAt(),
                BatchFlushLimit.ofSlots(Schedulers.DB_WRITER_THREADS)));
        new JfrRecordingEventParser(scratch, new Lz4Compressor(scratch), ALWAYS_SPLIT).start(writer, RecordingSources.of(file));
        writer.onComplete();
    }

    /** What the recording's events say the spans are: every request, step and statement but the startup query. */
    private static Map<String, String> spansByName(TestappRecording.Traces traces) {
        Map<String, String> byName = new TreeMap<>();
        traces.requestsByOperation().forEach((name, count) -> byName.put(name, String.valueOf(count)));
        traces.stepsByName().forEach((name, count) -> byName.put(name, String.valueOf(count)));
        traces.statementsByName().forEach((name, count) -> {
            if (!STARTUP_QUERY.equals(name)) {
                byName.put(name, String.valueOf(count));
            }
        });
        return byName;
    }

    private static Map<String, String> spansByName(DataSource dataSource) throws SQLException {
        Map<String, String> byName = new TreeMap<>();
        for (List<String> row : rows(dataSource, "SELECT name, count(*) FROM trace_spans WHERE NOT synthesized GROUP BY name")) {
            byName.put(row.get(0), row.get(1));
        }
        return byName;
    }

    private static List<List<String>> digests(DataSource dataSource) throws SQLException {
        List<List<String>> digests = new ArrayList<>();
        for (String table : DERIVED) {
            digests.addAll(rows(dataSource, "SELECT '" + table + "', count(*),"
                    + " md5(string_agg(CAST(t AS VARCHAR), '|' ORDER BY CAST(t AS VARCHAR))) FROM " + table + " t"));
        }
        return digests;
    }

    private static String cell(DataSource dataSource, String sql) throws SQLException {
        return rows(dataSource, sql).getFirst().getFirst();
    }

    private static List<List<String>> rows(DataSource dataSource, String sql) throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            int columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= columns; i++) {
                    row.add(result.getString(i));
                }
                rows.add(row);
            }
        }
        return rows;
    }
}
