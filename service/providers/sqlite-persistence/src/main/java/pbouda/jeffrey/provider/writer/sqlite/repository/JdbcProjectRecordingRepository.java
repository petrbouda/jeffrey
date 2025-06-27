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

package pbouda.jeffrey.provider.writer.sqlite.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.common.model.RecordingFile;
import pbouda.jeffrey.provider.api.model.recording.RecordingFolder;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;
import pbouda.jeffrey.provider.writer.sqlite.GroupLabel;
import pbouda.jeffrey.provider.writer.sqlite.StatementLabel;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class JdbcProjectRecordingRepository implements ProjectRecordingRepository {

    //language=SQL
    private static final String INSERT_RECORDING = """
            INSERT INTO recordings (project_id, id, recording_name, folder_id, event_source, created_at, recording_started_at, recording_finished_at)
                VALUES (:project_id, :id, :recording_name, :folder_id, :event_source, :created_at, :recording_started_at, :recording_finished_at)""";

    //language=SQL
    private static final String INSERT_RECORDING_FILE = """
            INSERT INTO recording_files (project_id, recording_id, id, filename, supported_type, uploaded_at, size_in_bytes)
                VALUES (:project_id, :recording_id, :id, :filename, :supported_type, :uploaded_at, :size_in_bytes)""";

    //language=sql
    private static final String RECORDING_BY_ID = """
            SELECT *, (EXISTS (SELECT 1 FROM profiles p WHERE p.recording_id = recordings.id)) AS has_profile
                FROM recordings WHERE project_id = :project_id AND id = :recording_id""";

    //language=SQL
    private static final String FOLDER_EXISTS =
            "SELECT count(*) FROM recording_folders WHERE project_id = :project_id AND id = :folder_id";

    //language=sql
    private static final String FIND_RECORDINGS_IN_FOLDER =
            "SELECT id FROM recordings WHERE project_id = :project_id AND folder_id = :folder_id";

    //language=sql
    private static final String ALL_RECORDINGS = """
            SELECT *, (EXISTS (SELECT 1 FROM profiles p WHERE p.recording_id = recordings.id)) AS has_profile
                FROM recordings
                WHERE project_id = :project_id""";

    //language=sql
    private static final String ALL_RECORDING_FILES =
            "SELECT * FROM recording_files WHERE project_id = :project_id";

    //language=sql
    private static final String FIND_RECORDING = """
            SELECT *, (EXISTS (SELECT 1 FROM profiles p WHERE p.recording_id = recordings.id)) AS has_profile
                FROM recordings
                WHERE project_id = :project_id AND id = :recording_id""";

    //language=sql
    private static final String FIND_RECORDING_FILES =
            "SELECT * FROM recordings WHERE project_id = :project_id AND recording_id = :recording_id";

    //language=sql
    private static final String INSERT_FOLDER =
            "INSERT INTO recording_folders (project_id, id, name) VALUES (:project_id, :id, :name)";

    //language=SQL
    private static final String DELETE_RECORDING_WITH_FILES = """
            BEGIN TRANSACTION;
            DELETE FROM recordings WHERE project_id = '%project_id%' AND id = '%recording_id%';
            DELETE FROM recording_files WHERE project_id = '%project_id%' AND recording_id = '%recording_id%';
            COMMIT;""";

    private final String projectId;
    private final DatabaseClient databaseClient;

    public JdbcProjectRecordingRepository(String projectId, DataSource dataSource) {
        this.projectId = projectId;
        this.databaseClient = new DatabaseClient(dataSource, GroupLabel.PROJECT_RECORDINGS);
    }

    @Override
    public Optional<Recording> findRecording(String recordingId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("recording_id", recordingId);

        Optional<Recording> recordingOpt = databaseClient.querySingle(
                StatementLabel.FIND_RECORDING, FIND_RECORDING, paramSource, Mappers.projectRecordingMapper());

        // Load all recordings files to the given recording
        if (recordingOpt.isPresent()) {
            List<RecordingFile> files = databaseClient.query(
                    StatementLabel.FIND_RECORDING_FILES,
                    FIND_RECORDING_FILES,
                    paramSource,
                    Mappers.projectRecordingFileMapper());

            return recordingOpt.map(recording -> recording.withFiles(files));
        } else {
            return recordingOpt;
        }
    }

    @Override
    public void deleteRecordingWithFiles(String recordingId) {
        String sql = DELETE_RECORDING_WITH_FILES
                .replaceAll("%project_id%", projectId)
                .replaceAll("%recording_id%", recordingId);

        databaseClient.delete(StatementLabel.DELETE_RECORDING, sql);
    }

    @Override
    public List<Recording> findAllRecordings() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        List<Recording> recordings = databaseClient.query(
                StatementLabel.FIND_ALL_RECORDINGS,
                ALL_RECORDINGS,
                paramSource,
                Mappers.projectRecordingMapper());

        List<RecordingFile> recordingFiles = databaseClient.query(
                StatementLabel.FIND_ALL_RECORDING_FILES,
                ALL_RECORDING_FILES,
                paramSource,
                Mappers.projectRecordingFileMapper());

        Map<String, List<RecordingFile>> filesPerRecording = recordingFiles.stream()
                .collect(Collectors.groupingBy(RecordingFile::recordingId));

        // Add Recording Files to the particular Recordings
        return recordings.stream()
                .map(recording -> {
                    List<RecordingFile> files = filesPerRecording.get(recording.id());
                    return files == null ? recording : recording.withFiles(files);
                })
                .toList();
    }

    @Override
    public String insertFolder(String folderName) {
        String folderId = IDGenerator.generate();
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("id", folderId)
                .addValue("name", folderName);

        databaseClient.insert(StatementLabel.INSERT_FOLDER, INSERT_FOLDER, paramSource);
        return folderId;
    }

    @Override
    public void deleteFolder(String folderId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("folder_id", folderId);

        List<String> recordingIds = databaseClient.query(
                StatementLabel.FIND_RECORDINGS_IN_FOLDER,
                FIND_RECORDINGS_IN_FOLDER,
                paramSource, (rs, _) -> rs.getString("id"));

        // Delete all recordings in the folder
        recordingIds.forEach(this::deleteRecordingWithFiles);

        //language=sql
        String deleteFolderSql = "DELETE FROM recording_folders WHERE project_id = :project_id AND id = :folder_id";
        databaseClient.delete(StatementLabel.DELETE_FOLDER, deleteFolderSql, paramSource);
    }

    @Override
    public List<RecordingFolder> findAllRecordingFolders() {
        //language=sql
        String sql = "SELECT * FROM recording_folders WHERE project_id = :project_id";

        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_ALL_FOLDERS, sql, paramSource, Mappers.projectRecordingFolderMapper());
    }

    @Override
    public Optional<Recording> findById(String recordingId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("recording_id", recordingId);

        return databaseClient.querySingle(
                StatementLabel.FIND_RECORDING, RECORDING_BY_ID, paramSource, Mappers.projectRecordingMapper());
    }

    @Override
    public void insertRecording(Recording recording, RecordingFile recordingFile) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("id", recording.id())
                .addValue("recording_name", recording.recordingName())
                .addValue("folder_id", recording.folderId())
                .addValue("event_source", recording.eventSource().name())
                .addValue("created_at", recording.createdAt().toEpochMilli())
                .addValue("recording_started_at", recording.recordingStartedAt().toEpochMilli())
                .addValue("recording_finished_at", recording.recordingFinishedAt().toEpochMilli());

        databaseClient.insert(StatementLabel.INSERT_RECORDING, INSERT_RECORDING, paramSource);

        // Insert Main Recording File directly into the database
        insertRecordingFile(recordingFile);
    }

    @Override
    public void insertRecordingFile(RecordingFile recordingFile) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("recording_id", recordingFile.recordingId())
                .addValue("id", recordingFile.id())
                .addValue("filename", recordingFile.filename())
                .addValue("supported_type", recordingFile.recordingFileType().name())
                .addValue("uploaded_at", recordingFile.uploadedAt().toEpochMilli())
                .addValue("size_in_bytes", recordingFile.sizeInBytes());

        databaseClient.insert(StatementLabel.INSERT_RECORDING_FILE, INSERT_RECORDING_FILE, paramSource);
    }

    @Override
    public boolean folderExists(String folderId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("folder_id", folderId);

        return databaseClient.queryExists(StatementLabel.FOLDER_EXISTS, FOLDER_EXISTS, paramSource);
    }
}
