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

import cafe.jeffrey.shared.persistence.DuckDBMemoryMonitor;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A profile database opts into the DuckDB driver's {@code duckdb.MemoryUsage} JFR event, labelled with
 * its profile, so a recording of Microscope shows how much native memory each profile holds.
 */
class ProfileDatabaseMemoryMonitorTest {

    private static final String MEMORY_EVENT = "duckdb.MemoryUsage";
    private static final String COMPONENT_FIELD = "component";
    private static final String PROFILE_ID = "p-monitored";
    private static final Duration SAMPLING_PERIOD = Duration.ofMillis(50);

    @TempDir
    Path baseDir;

    private DataSource dataSource;

    @AfterEach
    void closePool() throws Exception {
        if (dataSource instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    @Test
    void reportsItsMemoryUnderItsProfileWhileARecordingAsks() throws SQLException {
        Set<String> components = ConcurrentHashMap.newKeySet();
        try (RecordingStream stream = new RecordingStream()) {
            stream.enable(MEMORY_EVENT).withPeriod(SAMPLING_PERIOD);
            stream.onEvent(MEMORY_EVENT, event -> components.add(event.getString(COMPONENT_FIELD)));
            stream.startAsync();

            dataSource = new DuckDBProfileDatabaseManager(baseDir).open(PROFILE_ID);
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT count(*) FROM events");
            }

            await().atMost(10, SECONDS).untilAsserted(() -> assertTrue(
                    components.contains(DuckDBMemoryMonitor.PROFILE_DATABASE_PREFIX + PROFILE_ID),
                    "components seen: " + components));
        }
    }
}
