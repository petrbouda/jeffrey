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

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.record.SimpleRecord;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.RecordQuery;

import java.util.List;
import java.util.stream.Stream;

public class JdbcProfileEventRepository implements ProfileEventRepository {

    private final String profileId;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    //language=SQL
    private static final String FIELDS_BY_EVENT = """
            SELECT event_name, fields FROM events
            WHERE profile_id = (:profile_id) AND event_name IN (:code)
            """;

    public JdbcProfileEventRepository(String profileId, JdbcTemplate jdbcTemplate) {
        this.profileId = profileId;
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public List<JsonNode> eventsByTypeWithFields(Type type) {
        return jdbcTemplate.query(
                FIELDS_BY_EVENT,
                params().addValue("code", type.code()),
                (rs, rowNum) -> Json.readTree(rs.getString("fields")));
    }

    @Override
    public Stream<SimpleRecord> streamRecords(RecordQuery recordQuery) {
        return jdbcTemplate
                .getJdbcTemplate()
                .queryForStream(recordQuery.query(), new SimpleRecordRowMapper(recordQuery));
    }

    private MapSqlParameterSource params() {
        return new MapSqlParameterSource("profile_id", profileId);
    }
}
