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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.common.model.RecordingFile;
import pbouda.jeffrey.provider.api.model.recording.RecordingFolder;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;

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
                        :recording_finished_at)
            """;

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
                        :size_in_bytes)
            """;

    //language=sql
    private static final String RECORDING_BY_ID = """
            SELECT
                *,
                (EXISTS (SELECT 1 FROM profiles p WHERE p.recording_id = recordings.id)) AS has_profile
                FROM recordings
                WHERE project_id = :project_id AND id = :recording_id
            """;

    //language=SQL
    private static final String FOLDER_EXISTS = """
            SELECT count(*) FROM recording_folders WHERE
                 project_id = :project_id AND id = :folder_id
            """;

    //language=sql
    private static final String ALL_RECORDINGS = """
            SELECT
                *,
                (EXISTS (SELECT 1 FROM profiles p WHERE p.recording_id = recordings.id)) AS has_profile
                FROM recordings
                WHERE project_id = :project_id
            """;

    //language=sql
    private static final String ALL_RECORDING_FILES = """
            SELECT * FROM recording_files WHERE project_id = :project_id
            """;

    //language=sql
    private static final String FIND_RECORDING = """
            SELECT
                *,
                (EXISTS (SELECT 1 FROM profiles p WHERE p.recording_id = recordings.id)) AS has_profile
                FROM recordings
                WHERE project_id = :project_id AND id = :recording_id
            """;

    //language=sql
    private static final String FIND_RECORDING_FILES = """
            SELECT * FROM recordings WHERE project_id = :project_id AND recording_id = :recording_id
            """;

    //language=sql
    private static final String INSERT_FOLDER = """
            INSERT INTO recording_folders (project_id, id, name) VALUES (:project_id, :id, :name)
            """;

    //language=SQL
    private static final String DELETE_RECORDING_WITH_FILES = """
            BEGIN TRANSACTION;
            DELETE FROM recordings WHERE project_id = '%project_id%' AND id = '%recording_id%';
            DELETE FROM recording_files WHERE project_id = '%project_id%' AND recording_id = '%recording_id%';
            COMMIT;
            """;

    private final String projectId;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcProjectRecordingRepository(String projectId, JdbcTemplate jdbcTemplate) {
        this.projectId = projectId;
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public Optional<Recording> findRecording(String recordingId) {
        var params = new MapSqlParameterSource(Map.of("project_id", projectId, "recording_id", recordingId));
        return jdbcTemplate.query(FIND_RECORDING, params, Mappers.projectRecordingMapper()).stream().findFirst()
                .map(rec -> {
                    List<RecordingFile> files = jdbcTemplate.query(
                            FIND_RECORDING_FILES, params, Mappers.projectRecordingFileMapper());
                    return rec.withFiles(files);
                });
    }

    @Override
    public void deleteRecordingWithFiles(String recordingId) {
        jdbcTemplate.getJdbcTemplate()
                .update(DELETE_RECORDING_WITH_FILES
                        .replaceAll("%project_id%", projectId)
                        .replaceAll("%recording_id%", recordingId));
    }

    @Override
    public List<Recording> findAllRecordings() {
        var params = new MapSqlParameterSource("project_id", projectId);
        List<Recording> recordings =
                jdbcTemplate.query(ALL_RECORDINGS, params, Mappers.projectRecordingMapper());
        List<RecordingFile> recordingFiles =
                jdbcTemplate.query(ALL_RECORDING_FILES, params, Mappers.projectRecordingFileMapper());

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
    public void insertFolder(String folderName) {
        Map<String, Object> params = Map.of(
                "project_id", projectId,
                "id", IDGenerator.generate(),
                "name", folderName);

        jdbcTemplate.update(INSERT_FOLDER, params);
    }

    @Override
    public List<RecordingFolder> findAllRecordingFolders() {
        //language=sql
        String sql = "SELECT * FROM recording_folders WHERE project_id = :project_id";
        var params = new MapSqlParameterSource("project_id", projectId);
        return jdbcTemplate.query(sql, params, Mappers.projectRecordingFolderMapper());
    }

    @Override
    public Optional<Recording> findById(String recordingId) {
        var params = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("recording_id", recordingId);

        List<Recording> recordings =
                jdbcTemplate.query(RECORDING_BY_ID, params, Mappers.projectRecordingMapper());

        return recordings.isEmpty() ? Optional.empty() : Optional.of(recordings.getFirst());
    }

    @Override
    public void insertRecording(Recording recording, RecordingFile recordingFile) {
        var params = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("id", recording.id())
                .addValue("recording_name", recording.recordingName())
                .addValue("folder_id", recording.folderId())
                .addValue("event_source", recording.eventSource().name())
                .addValue("created_at", recording.createdAt().toEpochMilli())
                .addValue("recording_started_at", recording.recordingStartedAt().toEpochMilli())
                .addValue("recording_finished_at", recording.recordingFinishedAt().toEpochMilli());

        jdbcTemplate.update(INSERT_RECORDING, params);

        // Insert Main Recording File directly into the database
        insertRecordingFile(recordingFile);
    }

    @Override
    public void insertRecordingFile(RecordingFile recordingFile) {
        var params = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("recording_id", recordingFile.recordingId())
                .addValue("id", recordingFile.id())
                .addValue("filename", recordingFile.filename())
                .addValue("supported_type", recordingFile.recordingFileType().name())
                .addValue("uploaded_at", recordingFile.uploadedAt().toEpochMilli())
                .addValue("size_in_bytes", recordingFile.sizeInBytes());

        jdbcTemplate.update(INSERT_RECORDING_FILE, params);
    }

    @Override
    public boolean folderExists(String folderId) {
        var params = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("folder_id", folderId);

        Integer count = jdbcTemplate.queryForObject(FOLDER_EXISTS, params, Integer.class);
        return count != null && count > 0;
    }
}
