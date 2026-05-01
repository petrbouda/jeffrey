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

package cafe.jeffrey.microscope.persistence.jdbc;

import cafe.jeffrey.microscope.persistence.api.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.RecordingFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/microscope/core")
class JdbcRecordingRepositoryTest {

    @Nested
    class FindAllRecordingsMethod {

        @Test
        void returnsEmptyList_whenNoRecordings(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

            List<Recording> result = repository.findAllRecordings();

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsRecordings_whenRecordingsExist(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

            List<Recording> result = repository.findAllRecordings();

            assertEquals(2, result.size());
        }

        @Test
        void includesRecordingFiles(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

            List<Recording> result = repository.findAllRecordings();

            Recording recording = result.stream()
                    .filter(r -> "rec-001".equals(r.id()))
                    .findFirst()
                    .orElseThrow();
            assertNotNull(recording.files());
            assertEquals(1, recording.files().size());
        }
    }

    @Nested
    class FindRecordingMethod {

        @Test
        void returnsRecording_whenExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

            Optional<Recording> result = repository.findRecording("rec-001");

            assertTrue(result.isPresent());
            assertEquals("Recording One", result.get().recordingName());
        }

        @Test
        void returnsEmpty_whenNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

            Optional<Recording> result = repository.findRecording("non-existent");

            assertTrue(result.isEmpty());
        }

        @Test
        void includesRecordingFiles(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

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
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

            Recording recording = new Recording(
                    "new-rec-001", "New Recording", "proj-001", null,
                    RecordingEventSource.JDK, Instant.parse("2025-01-15T12:00:00Z"),
                    Instant.parse("2025-01-15T11:00:00Z"), Instant.parse("2025-01-15T11:30:00Z"),
                    false, null, null, List.of());
            RecordingFile recordingFile = new RecordingFile(
                    "new-file-001", "new-rec-001", "new-recording.jfr",
                    SupportedRecordingFile.JFR, Instant.parse("2025-01-15T12:00:00Z"), 1024);

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
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

            RecordingFile additionalFile = new RecordingFile(
                    "file-003", "rec-001", "recording1-extra.jfr",
                    SupportedRecordingFile.JFR, Instant.parse("2025-01-15T14:00:00Z"), 512);

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
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

            Optional<Recording> result = repository.findById("rec-001");

            assertTrue(result.isPresent());
            assertEquals("Recording One", result.get().recordingName());
        }

        @Test
        void returnsEmpty_whenNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

            Optional<Recording> result = repository.findById("non-existent");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class DeleteRecordingMethod {

        @Test
        void deletesRecordingAndFiles(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

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
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

            String groupId = repository.insertGroup("New Group");

            assertNotNull(groupId);
            assertTrue(repository.groupExists(groupId));
        }

        @Test
        void findsAllGroups(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

            List<RecordingGroup> result = repository.findAllRecordingGroups();

            assertEquals(1, result.size());
            assertEquals("Test Group", result.get(0).name());
        }

        @Test
        void groupExists_returnsTrue_whenGroupExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

            boolean result = repository.groupExists("group-001");

            assertTrue(result);
        }

        @Test
        void groupExists_returnsFalse_whenGroupNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

            boolean result = repository.groupExists("non-existent");

            assertFalse(result);
        }

        @Test
        void deletesGroup_andRecordingsInGroup(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcRecordingRepository repository = new JdbcRecordingRepository("proj-001", provider);

            repository.deleteGroup("group-001");

            assertFalse(repository.groupExists("group-001"));
            // Recording in group should also be deleted
            Optional<Recording> result = repository.findRecording("rec-002");
            assertTrue(result.isEmpty());
        }
    }
}
