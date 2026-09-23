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

import org.springframework.jdbc.core.RowMapper;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
import cafe.jeffrey.microscope.persistence.api.RecordingGroup;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public abstract class Mappers {

    public static RowMapper<ProfileInfo> profileInfoMapper() {
        return (rs, _) -> {
            return new ProfileInfo(
                    rs.getString("profile_id"),
                    rs.getString("project_id"),
                    rs.getString("workspace_id"),
                    rs.getString("profile_name"),
                    RecordingEventSource.valueOf(rs.getString("event_source")),
                    Mappers.instant(rs, "recording_started_at"),
                    Mappers.instant(rs, "recording_finished_at"),
                    Mappers.instant(rs, "created_at"),
                    safeParseTimestamp(rs.getLong("enabled_at")) != null,
                    rs.getBoolean("modified"),
                    rs.getString("recording_id"));
        };
    }

    public static RowMapper<Recording> projectRecordingMapper() {
        return (rs, _) -> {
            return new Recording(
                    rs.getString("id"),
                    rs.getString("recording_name"),
                    rs.getString("group_id"),
                    RecordingEventSource.valueOf(rs.getString("event_source")),
                    Mappers.instant(rs, "created_at"),
                    Mappers.instant(rs, "recording_started_at"),
                    Mappers.instant(rs, "recording_finished_at"),
                    rs.getBoolean("has_profile"),
                    rs.getString("profile_id"),
                    rs.getString("profile_name"),
                    List.of());
        };
    }

    public static RowMapper<RecordingFile> projectRecordingFileMapper() {
        return (rs, _) -> {
            return new RecordingFile(
                    rs.getString("id"),
                    rs.getString("recording_id"),
                    rs.getString("filename"),
                    ManagedFile.ofType(rs.getString("supported_type")),
                    Mappers.instant(rs, "uploaded_at"),
                    rs.getLong("size_in_bytes"));
        };
    }

    public static RowMapper<RecordingGroup> projectRecordingGroupMapper() {
        return (rs, _) -> {
            return new RecordingGroup(rs.getString("id"), rs.getString("name"), Mappers.instant(rs, "created_at"));
        };
    }

    public static Instant safeParseTimestamp(long timestamp) {
        return timestamp == 0 ? null : Instant.ofEpochMilli(timestamp);
    }

    public static Instant instant(ResultSet rs, String columnName) throws SQLException {
        OffsetDateTime dateTime = rs.getObject(columnName, OffsetDateTime.class);
        if (dateTime != null) {
            return dateTime.toInstant();
        }
        return null;
    }
}
