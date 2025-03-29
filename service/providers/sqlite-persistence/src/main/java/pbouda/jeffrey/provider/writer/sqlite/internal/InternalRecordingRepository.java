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

package pbouda.jeffrey.provider.writer.sqlite.internal;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.provider.api.model.NewRecording;

import javax.sql.DataSource;
import java.util.Map;

public class InternalRecordingRepository {

    //language=SQL
    private static final String INSERT_RECORDING = """
            INSERT INTO recordings (
                 project_id,
                 id,
                 name,
                 folder_id,
                 size,
                 created_at)
                VALUES (:project_id,
                        :id,
                        :name,
                        :folder_id,
                        :size,
                        :created_at)
            """;

    //language=SQL
    private static final String FOLDER_EXISTS = """
            SELECT count(*) FROM recording_folders WHERE
                 project_id = :project_id AND folder_id = :folder_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InternalRecordingRepository(DataSource dataSource) {
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    public void insertRecording(Recording recording) {
        var params = new MapSqlParameterSource()
                .addValue("project_id", recording.projectId())
                .addValue("id", recording.id())
                .addValue("name", recording.name())
                .addValue("folder_id", recording.folderId())
                .addValue("size", recording.sizeInBytes())
                .addValue("created_at", recording.createdAt().toEpochMilli());

        jdbcTemplate.update(INSERT_RECORDING, params);
    }

    public boolean folderExists(NewRecording recording) {
        var params = new MapSqlParameterSource()
                .addValue("project_id", recording.projectId())
                .addValue("folder_id", recording.folderId());

        Integer count = jdbcTemplate.queryForObject(FOLDER_EXISTS, params, Integer.class);
        return count != null && count > 0;
    }
}
