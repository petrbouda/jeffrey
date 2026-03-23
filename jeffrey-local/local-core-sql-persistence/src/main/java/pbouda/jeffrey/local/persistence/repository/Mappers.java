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

package pbouda.jeffrey.local.persistence.repository;

import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.Recording;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.common.model.RecordingFile;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.local.persistence.model.RecordingFolder;

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
                    rs.getString("recording_id"));
        };
    }

    public static RowMapper<Recording> projectRecordingMapper() {
        return (rs, _) -> {
            return new Recording(
                    rs.getString("id"),
                    rs.getString("recording_name"),
                    rs.getString("project_id"),
                    rs.getString("folder_id"),
                    RecordingEventSource.valueOf(rs.getString("event_source")),
                    Mappers.instant(rs, "created_at"),
                    Mappers.instant(rs, "recording_started_at"),
                    Mappers.instant(rs, "recording_finished_at"),
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
                    Mappers.instant(rs, "uploaded_at"),
                    rs.getLong("size_in_bytes"));
        };
    }

    public static RowMapper<RecordingFolder> projectRecordingFolderMapper() {
        return (rs, _) -> {
            return new RecordingFolder(rs.getString("id"), rs.getString("name"));
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
