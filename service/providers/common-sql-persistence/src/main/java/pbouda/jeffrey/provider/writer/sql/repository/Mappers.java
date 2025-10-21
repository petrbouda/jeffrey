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

package pbouda.jeffrey.provider.writer.sql.repository;

import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.common.model.RecordingEventSource;
import pbouda.jeffrey.common.model.RecordingFile;
import pbouda.jeffrey.common.model.RepositoryType;
import pbouda.jeffrey.common.model.job.JobInfo;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.model.recording.RecordingFolder;

import java.sql.ResultSetMetaData;
import java.time.Instant;
import java.util.List;

public abstract class Mappers {

    static RowMapper<JobInfo> jobInfoMapper() {
        return (rs, _) -> {
            String id = rs.getString("id");
            String projectId = rs.getString("project_id");
            String jobType = rs.getString("job_type");
            String params = rs.getString("params");
            boolean enabled = rs.getBoolean("enabled");
            return new JobInfo(id, projectId, JobType.valueOf(jobType), Json.toMap(params), enabled);
        };
    }

    static RowMapper<DBRepositoryInfo> repositoryInfoMapper() {
        return (rs, _) -> {
            return new DBRepositoryInfo(
                    RepositoryType.valueOf(rs.getString("type")),
                    rs.getString("finished_session_detection_file"));
        };
    }

    static RowMapper<ProfileInfo> profileInfoMapper() {
        return (rs, _) -> {
            return new ProfileInfo(
                    rs.getString("profile_id"),
                    rs.getString("project_id"),
                    rs.getString("profile_name"),
                    RecordingEventSource.valueOf(rs.getString("event_source")),
                    Instant.ofEpochMilli(rs.getLong("recording_started_at")),
                    Instant.ofEpochMilli(rs.getLong("recording_finished_at")),
                    Instant.ofEpochMilli(rs.getLong("created_at")),
                    safeParseTimestamp(rs.getLong("enabled_at")) != null);
        };
    }

    static RowMapper<ProjectInfo> projectInfoMapper() {
        return (rs, _) -> {
            return new ProjectInfo(
                    rs.getString("project_id"),
                    rs.getString("origin_project_id"),
                    rs.getString("project_name"),
                    rs.getString("workspace_id"),
                    WorkspaceType.valueOf(rs.getString("type")),
                    Instant.ofEpochMilli(rs.getLong("created_at")),
                    Instant.ofEpochMilli(rs.getLong("origin_created_at")),
                    Json.toMap(rs.getString("attributes")));
        };
    }

    private static boolean isColumnPresent(ResultSetMetaData metaData, String columnName) {
        try {
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                if (metaData.getColumnLabel(i).equalsIgnoreCase(columnName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public static RowMapper<Recording> projectRecordingMapper() {
        return (rs, _) -> {
            return new Recording(
                    rs.getString("id"),
                    rs.getString("recording_name"),
                    rs.getString("project_id"),
                    rs.getString("folder_id"),
                    RecordingEventSource.valueOf(rs.getString("event_source")),
                    Instant.ofEpochMilli(rs.getLong("created_at")),
                    Instant.ofEpochMilli(rs.getLong("recording_started_at")),
                    Instant.ofEpochMilli(rs.getLong("recording_finished_at")),
                    rs.getBoolean("has_profile"),
                    List.of());
        };
    }

    public static RowMapper<RecordingFile> projectRecordingFileMapper() {
        return (rs, _) -> {
            return new RecordingFile(
                    rs.getString("id"),
                    rs.getString("recording_id"),
                    rs.getString("filename"),
                    SupportedRecordingFile.ofType(rs.getString("supported_type")),
                    Instant.ofEpochMilli(rs.getLong("uploaded_at")),
                    rs.getLong("size_in_bytes"));
        };
    }

    public static RowMapper<RecordingFolder> projectRecordingFolderMapper() {
        return (rs, _) -> {
            return new RecordingFolder(rs.getString("id"), rs.getString("name"));
        };
    }

    static Instant safeParseTimestamp(long timestamp) {
        return timestamp == 0 ? null : Instant.ofEpochMilli(timestamp);
    }
}
