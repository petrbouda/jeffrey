/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.support.SqlLobValue;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.repository.model.GraphContent;
import pbouda.jeffrey.repository.model.GraphInfo;

import java.sql.Types;
import java.util.List;
import java.util.Optional;

public class GraphRepository {

    private static final int[] INSERT_TYPES = new int[]{
            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BOOLEAN,
            Types.BOOLEAN, Types.VARCHAR, Types.INTEGER, Types.BLOB};

    private static final String INSERT = """
            INSERT INTO flamegraphs (
                id,
                profile_id,
                event_type,
                graph_type,
                use_thread_mode,
                use_weight,
                name,
                created_at,
                content
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_CONTENT = """
            SELECT id, name, event_type, graph_type, use_thread_mode, use_weight, content
            FROM flamegraphs WHERE id = ? AND profile_id = ?
            """;

    private static final String DELETE = """
            DELETE FROM flamegraphs WHERE id = ? AND profile_id = ?
            """;

    private static final String ALL_CUSTOM = """
            SELECT * FROM flamegraphs WHERE profile_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final GraphType graphType;

    public GraphRepository(JdbcTemplate jdbcTemplate, GraphType graphType) {
        this.jdbcTemplate = jdbcTemplate;
        this.graphType = graphType;
    }

    public void insert(GraphInfo fg, GraphData content) {
        jdbcTemplate.update(
                INSERT,
                new Object[]{
                        fg.id(),
                        fg.profileId(),
                        fg.eventType().code(),
                        graphType.name(),
                        fg.useThreadMode() ? 1 : null,
                        fg.useWeight() ? 1 : null,
                        fg.name(),
                        fg.createdAt().getEpochSecond(),
                        new SqlLobValue(Json.toString(content))
                }, INSERT_TYPES);
    }

    public Optional<GraphContent> content(String profileId, String fgId) {
        try {
            GraphContent content = jdbcTemplate.queryForObject(
                    SELECT_CONTENT, Repos.contentJson(), fgId, profileId);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<GraphInfo> allCustom(String profileId) {
        return jdbcTemplate.query(ALL_CUSTOM, Repos.infoMapper(), profileId);
    }

    public void delete(String profileId, String fgId) {
        jdbcTemplate.update(DELETE, fgId, profileId);
    }
}
