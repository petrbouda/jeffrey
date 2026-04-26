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

package pbouda.jeffrey.local.persistence.sql.repository;

import pbouda.jeffrey.local.persistence.repository.*;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.shared.common.IDGenerator;
import pbouda.jeffrey.shared.common.model.Recording;
import pbouda.jeffrey.shared.common.model.RecordingFile;
import pbouda.jeffrey.local.persistence.model.RecordingGroup;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class JdbcRecordingRepository implements RecordingRepository {

    private final String projectId;
    private final DatabaseClient databaseClient;

    // Dynamic WHERE clause: "project_id = :project_id" or "project_id IS NULL"
    private final String projectIdCondition;

    public JdbcRecordingRepository(String projectId, DatabaseClientProvider databaseClientProvider) {
        this.projectId = projectId;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROJECT_RECORDINGS);
        this.projectIdCondition = projectId != null
                ? "project_id = :project_id"
                : "project_id IS NULL";
    }

    private MapSqlParameterSource projectParams() {
        return new MapSqlParameterSource()
                .addValue("project_id", projectId);
    }

    // --- Recording operations ---

    @Override
    public Optional<Recording> findRecording(String recordingId) {
        //language=sql
        String sql = """
                SELECT r.*, p.profile_id, p.profile_name, (p.profile_id IS NOT NULL) AS has_profile
                FROM recordings r LEFT JOIN profiles p ON p.recording_id = r.id
                WHERE r.""" + projectIdCondition + " AND r.id = :recording_id";

        MapSqlParameterSource params = projectParams()
                .addValue("recording_id", recordingId);

        Optional<Recording> recordingOpt = databaseClient.querySingle(
                StatementLabel.FIND_RECORDING, sql, params, Mappers.projectRecordingMapper());

        if (recordingOpt.isPresent()) {
            //language=sql
            String filesSql = "SELECT * FROM recording_files WHERE " + projectIdCondition + " AND recording_id = :recording_id";
            List<RecordingFile> files = databaseClient.query(
                    StatementLabel.FIND_RECORDING_FILES, filesSql, params, Mappers.projectRecordingFileMapper());
            return recordingOpt.map(recording -> recording.withFiles(files));
        }
        return recordingOpt;
    }

    @Override
    public void deleteRecordingWithFiles(String recordingId) {
        // Avoid using 'project_id IS NULL' in DELETE — triggers a DuckDB ART index bug.
        // Recording IDs are UUIDs (globally unique), so the project_id filter is redundant for deletes.
        //language=sql
        String deleteFilesSql = "DELETE FROM recording_files WHERE recording_id = :recording_id";
        //language=sql
        String deleteRecordingSql = "DELETE FROM recordings WHERE id = :recording_id";

        var params = new MapSqlParameterSource().addValue("recording_id", recordingId);

        databaseClient.delete(StatementLabel.DELETE_RECORDING_FILES, deleteFilesSql, params);
        databaseClient.delete(StatementLabel.DELETE_RECORDING, deleteRecordingSql, params);
    }

    @Override
    public List<Recording> findAllRecordings() {
        //language=sql
        String recordingsSql = """
                SELECT r.*, p.profile_id, p.profile_name, (p.profile_id IS NOT NULL) AS has_profile
                FROM recordings r LEFT JOIN profiles p ON p.recording_id = r.id
                WHERE r.""" + projectIdCondition;

        //language=sql
        String filesSql = "SELECT * FROM recording_files WHERE " + projectIdCondition;

        MapSqlParameterSource params = projectParams();

        List<Recording> recordings = databaseClient.query(
                StatementLabel.FIND_ALL_RECORDINGS, recordingsSql, params, Mappers.projectRecordingMapper());

        List<RecordingFile> recordingFiles = databaseClient.query(
                StatementLabel.FIND_ALL_RECORDING_FILES, filesSql, params, Mappers.projectRecordingFileMapper());

        Map<String, List<RecordingFile>> filesPerRecording = recordingFiles.stream()
                .collect(Collectors.groupingBy(RecordingFile::recordingId));

        return recordings.stream()
                .map(recording -> {
                    List<RecordingFile> files = filesPerRecording.get(recording.id());
                    return files == null ? recording : recording.withFiles(files);
                })
                .toList();
    }

    @Override
    public Optional<Recording> findById(String recordingId) {
        //language=sql
        String sql = """
                SELECT r.*, p.profile_id, p.profile_name, (p.profile_id IS NOT NULL) AS has_profile
                FROM recordings r LEFT JOIN profiles p ON p.recording_id = r.id
                WHERE r.""" + projectIdCondition + " AND r.id = :recording_id";

        MapSqlParameterSource params = projectParams()
                .addValue("recording_id", recordingId);

        return databaseClient.querySingle(
                StatementLabel.FIND_RECORDING, sql, params, Mappers.projectRecordingMapper());
    }

    @Override
    public void insertRecording(Recording recording, RecordingFile recordingFile) {
        //language=SQL
        String sql = """
                INSERT INTO recordings (id, project_id, recording_name, group_id, event_source, created_at, recording_started_at, recording_finished_at)
                    VALUES (:id, :project_id, :recording_name, :group_id, :event_source, :created_at, :recording_started_at, :recording_finished_at)""";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("id", recording.id())
                .addValue("recording_name", recording.recordingName())
                .addValue("group_id", recording.groupId())
                .addValue("event_source", recording.eventSource().name())
                .addValue("created_at", recording.createdAt().atOffset(ZoneOffset.UTC))
                .addValue("recording_started_at", recording.recordingStartedAt() != null
                        ? recording.recordingStartedAt().atOffset(ZoneOffset.UTC) : null)
                .addValue("recording_finished_at", recording.recordingFinishedAt() != null
                        ? recording.recordingFinishedAt().atOffset(ZoneOffset.UTC) : null);

        databaseClient.insert(StatementLabel.INSERT_RECORDING, sql, params);
        insertRecordingFile(recordingFile);
    }

    @Override
    public void insertRecordingFile(RecordingFile recordingFile) {
        //language=SQL
        String sql = """
                INSERT INTO recording_files (id, project_id, recording_id, filename, supported_type, uploaded_at, size_in_bytes)
                    VALUES (:id, :project_id, :recording_id, :filename, :supported_type, :uploaded_at, :size_in_bytes)""";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", recordingFile.id())
                .addValue("project_id", projectId)
                .addValue("recording_id", recordingFile.recordingId())
                .addValue("filename", recordingFile.filename())
                .addValue("supported_type", recordingFile.recordingFileType().name())
                .addValue("uploaded_at", recordingFile.uploadedAt().atOffset(ZoneOffset.UTC))
                .addValue("size_in_bytes", recordingFile.sizeInBytes());

        databaseClient.insert(StatementLabel.INSERT_RECORDING_FILE, sql, params);
    }

    @Override
    public void updateRecordingGroup(String recordingId, String groupId) {
        //language=sql
        String sql = "UPDATE recordings SET group_id = :group_id WHERE " + projectIdCondition + " AND id = :recording_id";

        MapSqlParameterSource params = projectParams()
                .addValue("recording_id", recordingId)
                .addValue("group_id", groupId);

        databaseClient.update(StatementLabel.UPDATE_RECORDING_GROUP, sql, params);
    }

    // --- Group operations ---

    @Override
    public String insertGroup(String groupName) {
        //language=sql
        String sql = "INSERT INTO recording_groups (id, project_id, name, created_at) VALUES (:id, :project_id, :name, :created_at)";

        String groupId = IDGenerator.generate();
        MapSqlParameterSource params = projectParams()
                .addValue("id", groupId)
                .addValue("name", groupName)
                .addValue("created_at", Instant.now().atOffset(ZoneOffset.UTC));

        databaseClient.insert(StatementLabel.INSERT_GROUP, sql, params);
        return groupId;
    }

    @Override
    public boolean groupExists(String groupId) {
        //language=SQL
        String sql = "SELECT count(*) FROM recording_groups WHERE " + projectIdCondition + " AND id = :group_id";

        MapSqlParameterSource params = projectParams()
                .addValue("group_id", groupId);

        return databaseClient.queryExists(StatementLabel.GROUP_EXISTS, sql, params);
    }

    @Override
    public Optional<RecordingGroup> findGroupById(String groupId) {
        //language=sql
        String sql = "SELECT * FROM recording_groups WHERE " + projectIdCondition + " AND id = :group_id";

        MapSqlParameterSource params = projectParams()
                .addValue("group_id", groupId);

        return databaseClient.querySingle(
                StatementLabel.FIND_ALL_GROUPS, sql, params, Mappers.projectRecordingGroupMapper());
    }

    @Override
    public List<RecordingGroup> findAllRecordingGroups() {
        //language=sql
        String sql = "SELECT * FROM recording_groups WHERE " + projectIdCondition;

        return databaseClient.query(
                StatementLabel.FIND_ALL_GROUPS, sql, projectParams(), Mappers.projectRecordingGroupMapper());
    }

    @Override
    public void deleteGroup(String groupId) {
        //language=sql
        String findRecordingsSql = "SELECT id FROM recordings WHERE " + projectIdCondition + " AND group_id = :group_id";

        MapSqlParameterSource params = projectParams()
                .addValue("group_id", groupId);

        List<String> recordingIds = databaseClient.query(
                StatementLabel.FIND_RECORDINGS_IN_GROUP, findRecordingsSql, params,
                (rs, _) -> rs.getString("id"));

        recordingIds.forEach(this::deleteRecordingWithFiles);

        //language=sql
        String deleteGroupSql = "DELETE FROM recording_groups WHERE id = :group_id";
        databaseClient.delete(StatementLabel.DELETE_GROUP, deleteGroupSql, new MapSqlParameterSource().addValue("group_id", groupId));
    }

    @Override
    public List<Recording> findRecordingsByGroupId(String groupId) {
        //language=sql
        String recordingsSql = """
                SELECT r.*, p.profile_id, p.profile_name, (p.profile_id IS NOT NULL) AS has_profile
                FROM recordings r LEFT JOIN profiles p ON p.recording_id = r.id
                WHERE r.""" + projectIdCondition + " AND r.group_id = :group_id";

        //language=sql
        String filesSql = "SELECT rf.* FROM recording_files rf JOIN recordings r ON rf.recording_id = r.id WHERE r."
                + projectIdCondition + " AND r.group_id = :group_id";

        MapSqlParameterSource params = projectParams()
                .addValue("group_id", groupId);

        List<Recording> recordings = databaseClient.query(
                StatementLabel.FIND_ALL_RECORDINGS, recordingsSql, params, Mappers.projectRecordingMapper());

        List<RecordingFile> recordingFiles = databaseClient.query(
                StatementLabel.FIND_ALL_RECORDING_FILES, filesSql, params, Mappers.projectRecordingFileMapper());

        Map<String, List<RecordingFile>> filesPerRecording = recordingFiles.stream()
                .collect(Collectors.groupingBy(RecordingFile::recordingId));

        return recordings.stream()
                .map(recording -> {
                    List<RecordingFile> files = filesPerRecording.get(recording.id());
                    return files == null ? recording : recording.withFiles(files);
                })
                .toList();
    }
}
