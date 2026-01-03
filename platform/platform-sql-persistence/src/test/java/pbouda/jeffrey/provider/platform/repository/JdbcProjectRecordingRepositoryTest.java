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

package pbouda.jeffrey.provider.platform.repository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.provider.platform.model.RecordingFolder;
import pbouda.jeffrey.shared.common.model.Recording;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.common.model.RecordingFile;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
class JdbcProjectRecordingRepositoryTest {

    @Nested
    class FindAllRecordingsMethod {

        @Test
        void returnsEmptyList_whenNoRecordings(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectRecordingRepository repository = new JdbcProjectRecordingRepository("proj-001", provider);

            List<Recording> result = repository.findAllRecordings();

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsRecordings_whenRecordingsExist(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcProjectRecordingRepository repository = new JdbcProjectRecordingRepository("proj-001", provider);

            List<Recording> result = repository.findAllRecordings();

            assertEquals(2, result.size());
        }

        @Test
        void includesRecordingFiles(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcProjectRecordingRepository repository = new JdbcProjectRecordingRepository("proj-001", provider);

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
        void returnsRecording_whenExists(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcProjectRecordingRepository repository = new JdbcProjectRecordingRepository("proj-001", provider);

            Optional<Recording> result = repository.findRecording("rec-001");

            assertTrue(result.isPresent());
            assertEquals("Recording One", result.get().recordingName());
        }

        @Test
        void returnsEmpty_whenNotExists(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectRecordingRepository repository = new JdbcProjectRecordingRepository("proj-001", provider);

            Optional<Recording> result = repository.findRecording("non-existent");

            assertTrue(result.isEmpty());
        }

        @Test
        void includesRecordingFiles(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcProjectRecordingRepository repository = new JdbcProjectRecordingRepository("proj-001", provider);

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
        void insertsRecordingAndFile(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectRecordingRepository repository = new JdbcProjectRecordingRepository("proj-001", provider);

            Recording recording = new Recording(
                    "new-rec-001", "New Recording", "proj-001", null,
                    RecordingEventSource.JDK, Instant.parse("2025-01-15T12:00:00Z"),
                    Instant.parse("2025-01-15T11:00:00Z"), Instant.parse("2025-01-15T11:30:00Z"),
                    false, List.of());
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
    class DeleteRecordingMethod {

        @Test
        void deletesRecordingAndFiles(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcProjectRecordingRepository repository = new JdbcProjectRecordingRepository("proj-001", provider);

            repository.deleteRecordingWithFiles("rec-001");

            Optional<Recording> result = repository.findRecording("rec-001");
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class FolderMethods {

        @Test
        void insertsFolderAndReturnsId(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectRecordingRepository repository = new JdbcProjectRecordingRepository("proj-001", provider);

            String folderId = repository.insertFolder("New Folder");

            assertNotNull(folderId);
            assertTrue(repository.folderExists(folderId));
        }

        @Test
        void findsAllFolders(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcProjectRecordingRepository repository = new JdbcProjectRecordingRepository("proj-001", provider);

            List<RecordingFolder> result = repository.findAllRecordingFolders();

            assertEquals(1, result.size());
            assertEquals("Test Folder", result.get(0).name());
        }

        @Test
        void folderExists_returnsTrue_whenFolderExists(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcProjectRecordingRepository repository = new JdbcProjectRecordingRepository("proj-001", provider);

            boolean result = repository.folderExists("folder-001");

            assertTrue(result);
        }

        @Test
        void folderExists_returnsFalse_whenFolderNotExists(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectRecordingRepository repository = new JdbcProjectRecordingRepository("proj-001", provider);

            boolean result = repository.folderExists("non-existent");

            assertFalse(result);
        }

        @Test
        void deletesFolder_andRecordingsInFolder(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcProjectRecordingRepository repository = new JdbcProjectRecordingRepository("proj-001", provider);

            repository.deleteFolder("folder-001");

            assertFalse(repository.folderExists("folder-001"));
            // Recording in folder should also be deleted
            Optional<Recording> result = repository.findRecording("rec-002");
            assertTrue(result.isEmpty());
        }
    }
}
