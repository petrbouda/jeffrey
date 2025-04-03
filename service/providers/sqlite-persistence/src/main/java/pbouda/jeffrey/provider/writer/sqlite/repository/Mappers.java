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

import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.*;
import pbouda.jeffrey.provider.api.model.recording.RecordingFolder;

import java.time.Instant;

public abstract class Mappers {

    static RowMapper<ProfileInfo> profileInfoMapper() {
        return (rs, _) -> {
            return new ProfileInfo(
                    rs.getString("profile_id"),
                    rs.getString("project_id"),
                    rs.getString("profile_name"),
                    EventSource.valueOf(rs.getString("event_source")),
                    Instant.ofEpochMilli(rs.getLong("recording_started_at")),
                    Instant.ofEpochMilli(rs.getLong("recording_finished_at")),
                    Instant.ofEpochMilli(rs.getLong("created_at")),
                    safeParseTimestamp(rs.getLong("enabled_at")) != null);
        };
    }

    static RowMapper<GraphVisualization> graphVisualizationMapper() {
        return (rs, _) -> {
            String graphVisualization = rs.getString("graph_visualization");
            return Json.read(graphVisualization, GraphVisualization.class);
        };
    }

    static RowMapper<ProjectInfo> projectInfoMapper() {
        return (rs, _) -> {
            return new ProjectInfo(
                    rs.getString("project_id"),
                    rs.getString("project_name"),
                    Instant.ofEpochMilli(rs.getLong("created_at")));
        };
    }

    public static RowMapper<Recording> projectRecordingWithFolderMapper() {
        return (rs, _) -> {
            return new Recording(
                    rs.getString("id"),
                    rs.getString("recording_name"),
                    rs.getString("recording_filename"),
                    rs.getString("project_id"),
                    rs.getString("folder_id"),
                    EventSource.valueOf(rs.getString("event_source")),
                    rs.getLong("size_in_bytes"),
                    Instant.ofEpochMilli(rs.getLong("uploaded_at")),
                    Instant.ofEpochMilli(rs.getLong("recording_started_at")),
                    Instant.ofEpochMilli(rs.getLong("recording_finished_at")),
                    rs.getBoolean("has_profile"));
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
