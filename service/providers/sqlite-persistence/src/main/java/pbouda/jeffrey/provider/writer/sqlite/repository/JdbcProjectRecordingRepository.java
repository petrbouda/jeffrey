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
import pbouda.jeffrey.provider.api.model.recording.RecordingFolder;
import pbouda.jeffrey.provider.api.model.recording.RecordingWithFolder;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;

import java.util.List;
import java.util.Map;

public class JdbcProjectRecordingRepository implements ProjectRecordingRepository {

    //language=sql
    private static final String RECORDINGS_WITH_FOLDER = """
            SELECT
                rec.id AS recording_id,
                rec.recording_name AS recording_name,
                rec.recording_filename AS recording_filename,
                rec.size_in_bytes AS recording_size,
                rec.event_source AS event_source,
                rec.uploaded_at AS recording_uploaded_at,
                rec.recording_started_at AS recording_started_at,
                rec.recording_finished_at AS recording_finished_at,
                rec.project_id AS project_id,
                folder.id AS folder_id,
                folder.name AS folder_name,
                (EXISTS (SELECT 1 FROM profiles p WHERE p.recording_id = rec.id)) AS hasProfile
                FROM recordings rec
                    LEFT JOIN recording_folders folder ON rec.folder_id = folder.id
                WHERE rec.project_id = :projectId
            """;

    //language=sql
    private static final String INSERT_FOLDER = """
            INSERT INTO recording_folders (project_id, id, name) VALUES (:projectId, :id, :name)
            """;

    private final String projectId;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcProjectRecordingRepository(String projectId, JdbcTemplate jdbcTemplate) {
        this.projectId = projectId;
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public List<RecordingWithFolder> findAllRecordings() {
        var params = new MapSqlParameterSource("projectId", projectId);
        return jdbcTemplate.query(RECORDINGS_WITH_FOLDER, params, Mappers.projectRecordingWithFolderMapper());
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
        String sql = "SELECT * FROM recording_folders WHERE project_id = :projectId";
        var params = new MapSqlParameterSource("projectId", projectId);
        return jdbcTemplate.query(sql, params, Mappers.projectRecordingFolderMapper());
    }
}
