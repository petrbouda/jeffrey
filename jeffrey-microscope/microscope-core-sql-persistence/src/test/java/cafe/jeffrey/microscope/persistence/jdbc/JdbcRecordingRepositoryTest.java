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

package cafe.jeffrey.microscope.persistence.jdbc;

import cafe.jeffrey.microscope.persistence.api.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/microscope/core")
class JdbcRecordingRepositoryTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    private static long countRows(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    @Nested
    class FindAllRecordingsMethod {

        @Test
        void returnsEmptyList_whenNoRecordings(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            List<Recording> result = repository.findAllRecordings();

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsRecordings_whenRecordingsExist(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            List<Recording> result = repository.findAllRecordings();

            assertEquals(2, result.size());
        }

        @Test
        void includesRecordingFiles(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            List<Recording> result = repository.findAllRecordings();

            Recording recording = result.stream()
                    .filter(r -> "rec-001".equals(r.id()))
                    .findFirst()
                    .orElseThrow();
            assertNotNull(recording.files());
            assertEquals(1, recording.files().size());
        }
    }

    /**
     * Rows an older build wrote into a project. Nothing can open them any more, so they must not
     * surface in any listing or lookup beside the recordings this repository writes.
     */
    @Nested
    class LegacyProjectRows {

        @Test
        void areLeftOutOfListings(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-recordings.sql");
            TestUtils.executeSql(dataSource, "sql/recording/insert-legacy-project-recording.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            assertEquals(
                    List.of("rec-001", "rec-002"),
                    repository.findAllRecordings().stream().map(Recording::id).sorted().toList());
            assertTrue(repository.findRecording("legacy-rec").isEmpty());
            assertTrue(repository.findById("legacy-rec").isEmpty());
            assertFalse(repository.groupExists("legacy-group"));
            assertEquals(1, repository.findAllRecordingGroups().size());
        }

        @Test
        void newRecordingsAreStoredWithoutAProject(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            repository.insertRecording(
                    new Recording("new-rec", "New", null, RecordingEventSource.JDK, FIXED_TIME,
                            FIXED_TIME, FIXED_TIME, false, null, null, List.of()),
                    new RecordingFile("new-file", "new-rec", "new.jfr", ManagedFile.JFR, FIXED_TIME, 1));
            String groupId = repository.insertGroup("Group");

            assertEquals(0L, countRows(dataSource,
                    "SELECT count(*) FROM recordings WHERE project_id IS NOT NULL"));
            assertEquals(0L, countRows(dataSource,
                    "SELECT count(*) FROM recording_files WHERE project_id IS NOT NULL"));
            assertTrue(repository.groupExists(groupId));
        }
    }

    @Nested
    class FindRecordingMethod {

        @Test
        void returnsRecording_whenExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            Optional<Recording> result = repository.findRecording("rec-001");

            assertTrue(result.isPresent());
            assertEquals("Recording One", result.get().recordingName());
        }

        @Test
        void returnsEmpty_whenNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            Optional<Recording> result = repository.findRecording("non-existent");

            assertTrue(result.isEmpty());
        }

        @Test
        void includesRecordingFiles(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            Optional<Recording> result = repository.findRecording("rec-001");

            assertTrue(result.isPresent());
            assertNotNull(result.get().files());
            assertEquals(1, result.get().files().size());
            assertEquals("recording1.jfr", result.get().files().get(0).filename());
        }
    }

    @Nested
    class InsertRecordingMethod {

        @Test
        void insertsRecordingAndFile(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            Recording recording = new Recording(
                    "new-rec-001", "New Recording", null,
                    RecordingEventSource.JDK, Instant.parse("2025-01-15T12:00:00Z"),
                    Instant.parse("2025-01-15T11:00:00Z"), Instant.parse("2025-01-15T11:30:00Z"),
                    false, null, null, List.of());
            RecordingFile recordingFile = new RecordingFile(
                    "new-file-001", "new-rec-001", "new-recording.jfr",
                    ManagedFile.JFR, Instant.parse("2025-01-15T12:00:00Z"), 1024);

            repository.insertRecording(recording, recordingFile);

            Optional<Recording> result = repository.findRecording("new-rec-001");
            assertTrue(result.isPresent());
            assertEquals("New Recording", result.get().recordingName());
        }
    }

    @Nested
    class InsertRecordingFileMethod {

        @Test
        void insertsAdditionalFile_toExistingRecording(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            RecordingFile additionalFile = new RecordingFile(
                    "file-003", "rec-001", "recording1-extra.jfr",
                    ManagedFile.JFR, Instant.parse("2025-01-15T14:00:00Z"), 512);

            repository.insertRecordingFile(additionalFile);

            Optional<Recording> result = repository.findRecording("rec-001");
            assertTrue(result.isPresent());
            assertEquals(2, result.get().files().size());
        }
    }

    @Nested
    class FindByIdMethod {

        @Test
        void returnsRecording_whenExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            Optional<Recording> result = repository.findById("rec-001");

            assertTrue(result.isPresent());
            assertEquals("Recording One", result.get().recordingName());
        }

        @Test
        void returnsEmpty_whenNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            Optional<Recording> result = repository.findById("non-existent");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class DeleteRecordingMethod {

        @Test
        void deletesRecordingAndFiles(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            repository.deleteRecordingWithFiles("rec-001");

            Optional<Recording> result = repository.findRecording("rec-001");
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class GroupMethods {

        @Test
        void insertsGroupAndReturnsId(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            String groupId = repository.insertGroup("New Group");

            assertNotNull(groupId);
            assertTrue(repository.groupExists(groupId));
        }

        @Test
        void insertsGroup_withCreatedAtFromClock(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            String groupId = repository.insertGroup("Clocked Group");

            Optional<RecordingGroup> group = repository.findGroupById(groupId);
            assertTrue(group.isPresent());
            assertEquals(FIXED_TIME, group.get().createdAt());
        }

        @Test
        void findsAllGroups(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            List<RecordingGroup> result = repository.findAllRecordingGroups();

            assertEquals(1, result.size());
            assertEquals("Test Group", result.get(0).name());
        }

        @Test
        void groupExists_returnsTrue_whenGroupExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            boolean result = repository.groupExists("group-001");

            assertTrue(result);
        }

        @Test
        void groupExists_returnsFalse_whenGroupNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            boolean result = repository.groupExists("non-existent");

            assertFalse(result);
        }

        @Test
        void deletesGroup_andRecordingsInGroup(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository(provider, CLOCK);

            repository.deleteGroup("group-001");

            assertFalse(repository.groupExists("group-001"));
            // Recording in group should also be deleted
            Optional<Recording> result = repository.findRecording("rec-002");
            assertTrue(result.isEmpty());
        }
    }
}
