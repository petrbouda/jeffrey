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

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.common.model.RecordingFile;
import pbouda.jeffrey.provider.api.model.recording.RecordingFolder;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class JdbcProjectRecordingRepository implements ProjectRecordingRepository {

    //language=SQL
    private static final String INSERT_RECORDING = """
            INSERT INTO recordings (
                 project_id,
                 id,
                 recording_name,
                 folder_id,
                 event_source,
                 created_at,
                 recording_started_at,
                 recording_finished_at)
                VALUES (:project_id,
                        :id,
                        :recording_name,
                        :folder_id,
                        :event_source,
                        :created_at,
                        :recording_started_at,
                        :recording_finished_at)""";

    //language=SQL
    private static final String INSERT_RECORDING_FILE = """
            INSERT INTO recording_files (
                 project_id,
                 recording_id,
                 id,
                 filename,
                 supported_type,
                 uploaded_at,
                 size_in_bytes)
                VALUES (:project_id,
                        :recording_id,
                        :id,
                        :filename,
                        :supported_type,
                        :uploaded_at,
                        :size_in_bytes)""";

    //language=sql
    private static final String RECORDING_BY_ID = """
            SELECT
                *,
                (EXISTS (SELECT 1 FROM profiles p WHERE p.recording_id = recordings.id)) AS has_profile
                FROM recordings
                WHERE project_id = :project_id AND id = :recording_id""";

    //language=SQL
    private static final String FOLDER_EXISTS =
            "SELECT count(*) FROM recording_folders WHERE project_id = :project_id AND id = :folder_id";

    //language=sql
    private static final String FIND_RECORDINGS_IN_FOLDER =
            "SELECT id FROM recordings WHERE project_id = :project_id AND folder_id = :folder_id";

    //language=sql
    private static final String ALL_RECORDINGS = """
            SELECT
                *,
                (EXISTS (SELECT 1 FROM profiles p WHERE p.recording_id = recordings.id)) AS has_profile
                FROM recordings
                WHERE project_id = :project_id""";

    //language=sql
    private static final String ALL_RECORDING_FILES =
            "SELECT * FROM recording_files WHERE project_id = :project_id";

    //language=sql
    private static final String FIND_RECORDING = """
            SELECT
                *,
                (EXISTS (SELECT 1 FROM profiles p WHERE p.recording_id = recordings.id)) AS has_profile
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
    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public JdbcProjectRecordingRepository(String projectId, JdbcClient jdbcClient, DataSource dataSource) {
        this.projectId = projectId;
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public Optional<Recording> findRecording(String recordingId) {
        return jdbcClient.sql(FIND_RECORDING)
                .param("project_id", projectId)
                .param("recording_id", recordingId)
                .query(Mappers.projectRecordingMapper())
                .optional()
                .map(rec -> {
                    List<RecordingFile> files = jdbcClient.sql(FIND_RECORDING_FILES)
                            .param("project_id", projectId)
                            .param("recording_id", recordingId)
                            .query(Mappers.projectRecordingFileMapper())
                            .list();
                    return rec.withFiles(files);
                });
    }

    @Override
    public void deleteRecordingWithFiles(String recordingId) {
        jdbcTemplate.update(DELETE_RECORDING_WITH_FILES
                .replaceAll("%project_id%", projectId)
                .replaceAll("%recording_id%", recordingId));
    }

    @Override
    public List<Recording> findAllRecordings() {
        List<Recording> recordings = jdbcClient.sql(ALL_RECORDINGS)
                .param("project_id", projectId)
                .query(Mappers.projectRecordingMapper())
                .list();

        List<RecordingFile> recordingFiles = jdbcClient.sql(ALL_RECORDING_FILES)
                .param("project_id", projectId)
                .query(Mappers.projectRecordingFileMapper())
                .list();

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
        jdbcClient.sql(INSERT_FOLDER)
                .param("project_id", projectId)
                .param("id", folderId)
                .param("name", folderName)
                .update();
        return folderId;
    }

    @Override
    public void deleteFolder(String folderId) {
        List<String> recordingIds = jdbcClient.sql(FIND_RECORDINGS_IN_FOLDER)
                .param("project_id", projectId)
                .param("folder_id", folderId)
                .query((rs, _) -> rs.getString("id"))
                .list();

        // Delete all recordings in the folder
        recordingIds.forEach(this::deleteRecordingWithFiles);

        // Delete the folder itself
        jdbcClient.sql("DELETE FROM recording_folders WHERE project_id = :project_id AND id = :folder_id")
                .param("project_id", projectId)
                .param("folder_id", folderId)
                .update();
    }

    @Override
    public List<RecordingFolder> findAllRecordingFolders() {
        return jdbcClient.sql("SELECT * FROM recording_folders WHERE project_id = :project_id")
                .param("project_id", projectId)
                .query(Mappers.projectRecordingFolderMapper())
                .list();
    }

    @Override
    public Optional<Recording> findById(String recordingId) {
        return jdbcClient.sql(RECORDING_BY_ID)
                .param("project_id", projectId)
                .param("recording_id", recordingId)
                .query(Mappers.projectRecordingMapper())
                .optional();
    }

    @Override
    public void insertRecording(Recording recording, RecordingFile recordingFile) {
        jdbcClient.sql(INSERT_RECORDING)
                .param("project_id", projectId)
                .param("id", recording.id())
                .param("recording_name", recording.recordingName())
                .param("folder_id", recording.folderId())
                .param("event_source", recording.eventSource().name())
                .param("created_at", recording.createdAt().toEpochMilli())
                .param("recording_started_at", recording.recordingStartedAt().toEpochMilli())
                .param("recording_finished_at", recording.recordingFinishedAt().toEpochMilli())
                .update();

        // Insert Main Recording File directly into the database
        insertRecordingFile(recordingFile);
    }

    @Override
    public void insertRecordingFile(RecordingFile recordingFile) {
        jdbcClient.sql(INSERT_RECORDING_FILE)
                .param("project_id", projectId)
                .param("recording_id", recordingFile.recordingId())
                .param("id", recordingFile.id())
                .param("filename", recordingFile.filename())
                .param("supported_type", recordingFile.recordingFileType().name())
                .param("uploaded_at", recordingFile.uploadedAt().toEpochMilli())
                .param("size_in_bytes", recordingFile.sizeInBytes())
                .update();
    }

    @Override
    public boolean folderExists(String folderId) {
        return jdbcClient.sql(FOLDER_EXISTS)
                .param("project_id", projectId)
                .param("folder_id", folderId)
                .query(Integer.class)
                .optional()
                .map(count -> count > 0)
                .orElse(false);
    }
}
