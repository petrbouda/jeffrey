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
import pbouda.jeffrey.provider.writer.sqlite.repository.Mappers;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public class InternalRecordingRepository {
    //language=sql
    private static final String RECORDING_BY_ID = """
            SELECT
                *,
                (EXISTS (SELECT 1 FROM profiles p WHERE p.recording_id = recordings.id)) AS has_profile
                FROM recordings
                WHERE project_id = :project_id AND id = :recording_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InternalRecordingRepository(DataSource dataSource) {
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    public Optional<Recording> findById(String projectId, String recordingId) {
        var params = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("recording_id", recordingId);

        List<Recording> recordings =
                jdbcTemplate.query(RECORDING_BY_ID, params, Mappers.projectRecordingMapper());

        return recordings.isEmpty() ? Optional.empty() : Optional.of(recordings.getFirst());
    }
}
