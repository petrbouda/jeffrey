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

package cafe.jeffrey.performance.analyst.persistence;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.microscope.persistence.api.RecordingRepository;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.RecordingFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SQLite-backed {@link RecordingRepository} for the performance-analyst store. Mirrors microscope's
 * DuckDB {@code JdbcRecordingRepository} with two deployment differences:
 * <ul>
 *   <li>timestamps are stored as INTEGER epoch millis (SQLite has no native timestamp type);</li>
 *   <li>there is no {@code profiles} table, so the {@code LEFT JOIN profiles} is dropped and every
 *       recording is reported with {@code hasProfile=false} and null profile fields.</li>
 * </ul>
 * The {@code projectId} is always {@code null} for the analyst (the unscoped recording store).
 */
public class JdbcRecordingRepository implements RecordingRepository {

    private final String projectId;
    private final Clock clock;
    private final DatabaseClient databaseClient;

    // Dynamic WHERE clause: "project_id = :project_id" or "project_id IS NULL"
    private final String projectIdCondition;

    public JdbcRecordingRepository(DatabaseClientProvider databaseClientProvider, String projectId, Clock clock) {
        this.projectId = projectId;
        this.clock = clock;
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
        String sql = "SELECT * FROM recordings WHERE " + projectIdCondition + " AND id = :recording_id";

        MapSqlParameterSource params = projectParams()
                .addValue("recording_id", recordingId);

        Optional<Recording> recordingOpt = databaseClient.querySingle(
                StatementLabel.FIND_RECORDING, sql, params, recordingMapper());

        if (recordingOpt.isPresent()) {
            //language=sql
            String filesSql = "SELECT * FROM recording_files WHERE " + projectIdCondition + " AND recording_id = :recording_id";
            List<RecordingFile> files = databaseClient.query(
                    StatementLabel.FIND_RECORDING_FILES, filesSql, params, recordingFileMapper());
            return recordingOpt.map(recording -> recording.withFiles(files));
        }
        return recordingOpt;
    }

    @Override
    public void deleteRecordingWithFiles(String recordingId) {
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
        String recordingsSql = "SELECT * FROM recordings WHERE " + projectIdCondition;

        //language=sql
        String filesSql = "SELECT * FROM recording_files WHERE " + projectIdCondition;

        MapSqlParameterSource params = projectParams();

        List<Recording> recordings = databaseClient.query(
                StatementLabel.FIND_ALL_RECORDINGS, recordingsSql, params, recordingMapper());

        List<RecordingFile> recordingFiles = databaseClient.query(
                StatementLabel.FIND_ALL_RECORDING_FILES, filesSql, params, recordingFileMapper());

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
        String sql = "SELECT * FROM recordings WHERE " + projectIdCondition + " AND id = :recording_id";

        MapSqlParameterSource params = projectParams()
                .addValue("recording_id", recordingId);

        return databaseClient.querySingle(
                StatementLabel.FIND_RECORDING, sql, params, recordingMapper());
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
                .addValue("created_at", recording.createdAt().toEpochMilli())
                .addValue("recording_started_at", epochMillis(recording.recordingStartedAt()))
                .addValue("recording_finished_at", epochMillis(recording.recordingFinishedAt()));

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
                .addValue("uploaded_at", recordingFile.uploadedAt().toEpochMilli())
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
                .addValue("created_at", clock.instant().toEpochMilli());

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
                StatementLabel.FIND_ALL_GROUPS, sql, params, recordingGroupMapper());
    }

    @Override
    public List<RecordingGroup> findAllRecordingGroups() {
        //language=sql
        String sql = "SELECT * FROM recording_groups WHERE " + projectIdCondition;

        return databaseClient.query(
                StatementLabel.FIND_ALL_GROUPS, sql, projectParams(), recordingGroupMapper());
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
        String recordingsSql = "SELECT * FROM recordings WHERE " + projectIdCondition + " AND group_id = :group_id";

        //language=sql
        String filesSql = "SELECT rf.* FROM recording_files rf JOIN recordings r ON rf.recording_id = r.id WHERE r."
                + projectIdCondition + " AND r.group_id = :group_id";

        MapSqlParameterSource params = projectParams()
                .addValue("group_id", groupId);

        List<Recording> recordings = databaseClient.query(
                StatementLabel.FIND_ALL_RECORDINGS, recordingsSql, params, recordingMapper());

        List<RecordingFile> recordingFiles = databaseClient.query(
                StatementLabel.FIND_ALL_RECORDING_FILES, filesSql, params, recordingFileMapper());

        Map<String, List<RecordingFile>> filesPerRecording = recordingFiles.stream()
                .collect(Collectors.groupingBy(RecordingFile::recordingId));

        return recordings.stream()
                .map(recording -> {
                    List<RecordingFile> files = filesPerRecording.get(recording.id());
                    return files == null ? recording : recording.withFiles(files);
                })
                .toList();
    }

    // --- Mappers / helpers ---

    private static RowMapper<Recording> recordingMapper() {
        return (rs, _) -> new Recording(
                rs.getString("id"),
                rs.getString("recording_name"),
                rs.getString("project_id"),
                rs.getString("group_id"),
                RecordingEventSource.valueOf(rs.getString("event_source")),
                instant(rs, "created_at"),
                instant(rs, "recording_started_at"),
                instant(rs, "recording_finished_at"),
                false,
                null,
                null,
                List.of());
    }

    private static RowMapper<RecordingFile> recordingFileMapper() {
        return (rs, _) -> new RecordingFile(
                rs.getString("id"),
                rs.getString("recording_id"),
                rs.getString("filename"),
                SupportedRecordingFile.ofType(rs.getString("supported_type")),
                instant(rs, "uploaded_at"),
                rs.getLong("size_in_bytes"));
    }

    private static RowMapper<RecordingGroup> recordingGroupMapper() {
        return (rs, _) -> new RecordingGroup(rs.getString("id"), rs.getString("name"), instant(rs, "created_at"));
    }

    private static Long epochMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private static Instant instant(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : Instant.ofEpochMilli(value);
    }
}
