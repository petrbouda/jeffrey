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

package pbouda.jeffrey.local.persistence.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.local.persistence.model.QuickRecordingInfo;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcQuickRecordingRepository implements QuickRecordingRepository {

    //language=SQL
    private static final String INSERT = """
            INSERT INTO quick_recordings (recording_id, filename, group_id, event_source, file_path, size_in_bytes,
                uploaded_at, profiling_started_at, profiling_finished_at, profile_id)
                VALUES (:recording_id, :filename, :group_id, :event_source, :file_path, :size_in_bytes,
                    :uploaded_at, :profiling_started_at, :profiling_finished_at, :profile_id)""";

    //language=SQL
    private static final String FIND_BY_ID =
            "SELECT * FROM quick_recordings WHERE recording_id = :recording_id";

    //language=SQL
    private static final String FIND_ALL =
            "SELECT * FROM quick_recordings ORDER BY uploaded_at DESC";

    //language=SQL
    private static final String FIND_BY_GROUP_ID =
            "SELECT * FROM quick_recordings WHERE group_id = :group_id ORDER BY uploaded_at DESC";

    //language=SQL
    private static final String UPDATE_PROFILE_ID =
            "UPDATE quick_recordings SET profile_id = :profile_id WHERE recording_id = :recording_id";

    //language=SQL
    private static final String DELETE =
            "DELETE FROM quick_recordings WHERE recording_id = :recording_id";

    //language=SQL
    private static final String DELETE_BY_GROUP_ID =
            "DELETE FROM quick_recordings WHERE group_id = :group_id";

    private static final RowMapper<QuickRecordingInfo> ROW_MAPPER = (rs, _) -> {
        OffsetDateTime profilingStartedAt = rs.getObject("profiling_started_at", OffsetDateTime.class);
        OffsetDateTime profilingFinishedAt = rs.getObject("profiling_finished_at", OffsetDateTime.class);

        return new QuickRecordingInfo(
                rs.getString("recording_id"),
                rs.getString("filename"),
                rs.getString("group_id"),
                RecordingEventSource.valueOf(rs.getString("event_source")),
                rs.getString("file_path"),
                rs.getLong("size_in_bytes"),
                rs.getObject("uploaded_at", OffsetDateTime.class).toInstant(),
                profilingStartedAt != null ? profilingStartedAt.toInstant() : null,
                profilingFinishedAt != null ? profilingFinishedAt.toInstant() : null,
                rs.getString("profile_id")
        );
    };

    private final DatabaseClient databaseClient;

    public JdbcQuickRecordingRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.QUICK_RECORDINGS);
    }

    @Override
    public void insert(QuickRecordingInfo recording) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recording_id", recording.recordingId())
                .addValue("filename", recording.filename())
                .addValue("group_id", recording.groupId())
                .addValue("event_source", recording.eventSource().name())
                .addValue("file_path", recording.filePath())
                .addValue("size_in_bytes", recording.sizeInBytes())
                .addValue("uploaded_at", recording.uploadedAt().atOffset(ZoneOffset.UTC))
                .addValue("profiling_started_at", recording.profilingStartedAt() != null
                        ? recording.profilingStartedAt().atOffset(ZoneOffset.UTC) : null)
                .addValue("profiling_finished_at", recording.profilingFinishedAt() != null
                        ? recording.profilingFinishedAt().atOffset(ZoneOffset.UTC) : null)
                .addValue("profile_id", recording.profileId());

        databaseClient.insert(StatementLabel.INSERT_QUICK_RECORDING, INSERT, params);
    }

    @Override
    public Optional<QuickRecordingInfo> findById(String recordingId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recording_id", recordingId);

        return databaseClient.querySingle(StatementLabel.FIND_QUICK_RECORDING, FIND_BY_ID, params, ROW_MAPPER);
    }

    @Override
    public List<QuickRecordingInfo> findAll() {
        return databaseClient.query(StatementLabel.FIND_ALL_QUICK_RECORDINGS, FIND_ALL, ROW_MAPPER);
    }

    @Override
    public List<QuickRecordingInfo> findByGroupId(String groupId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("group_id", groupId);

        return databaseClient.query(StatementLabel.FIND_QUICK_RECORDINGS_BY_GROUP, FIND_BY_GROUP_ID, params, ROW_MAPPER);
    }

    @Override
    public void updateProfileId(String recordingId, String profileId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recording_id", recordingId)
                .addValue("profile_id", profileId);

        databaseClient.update(StatementLabel.UPDATE_QUICK_RECORDING_PROFILE, UPDATE_PROFILE_ID, params);
    }

    @Override
    public void delete(String recordingId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recording_id", recordingId);

        databaseClient.delete(StatementLabel.DELETE_QUICK_RECORDING, DELETE, params);
    }

    @Override
    public void deleteByGroupId(String groupId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("group_id", groupId);

        databaseClient.delete(StatementLabel.DELETE_QUICK_RECORDINGS_BY_GROUP, DELETE_BY_GROUP_ID, params);
    }
}
