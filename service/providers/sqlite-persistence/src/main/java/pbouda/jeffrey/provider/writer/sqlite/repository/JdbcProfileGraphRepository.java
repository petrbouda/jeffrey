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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.support.SqlLobValue;
import org.springframework.jdbc.datasource.DataSourceUtils;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.provider.api.model.graph.GraphContent;
import pbouda.jeffrey.provider.api.model.graph.GraphInfo;
import pbouda.jeffrey.provider.api.repository.ProfileGraphRepository;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JdbcProfileGraphRepository implements ProfileGraphRepository {

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

    public JdbcProfileGraphRepository(JdbcTemplate jdbcTemplate, GraphType graphType) {
        this.jdbcTemplate = jdbcTemplate;
        this.graphType = graphType;
    }

    public void insert(GraphInfo fg, JsonNode content) {
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
                        new SqlLobValue(content.toString())
                }, INSERT_TYPES);
    }

    public Optional<GraphContent> content(String profileId, String fgId) {
        try {
            GraphContent content = jdbcTemplate.queryForObject(
                    SELECT_CONTENT, JdbcProfileGraphRepository.contentJson(), fgId, profileId);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<GraphInfo> allCustom(String profileId) {
        return jdbcTemplate.query(ALL_CUSTOM, JdbcProfileGraphRepository.infoMapper(), profileId);
    }

    public void delete(String profileId, String fgId) {
        jdbcTemplate.update(DELETE, fgId, profileId);
    }

    private static RowMapper<GraphInfo> infoMapper() {
        return (rs, __) -> {
            try {
                return new GraphInfo(
                        rs.getString("id"),
                        rs.getString("profile_id"),
                        new Type(rs.getString("event_type")),
                        rs.getBoolean("use_thread_mode"),
                        rs.getBoolean("use_weight"),
                        rs.getString("name"),
                        Instant.ofEpochSecond(rs.getInt("created_at")));
            } catch (SQLException e) {
                throw new RuntimeException("Cannot retrieve a flamegraph info", e);
            }
        };
    }

    private static RowMapper<JsonNode> jsonMapper(String field) {
        return (rs, __) -> {
            try {
                InputStream content = rs.getBinaryStream(field);
                return Json.mapper().readTree(content.readAllBytes());
            } catch (SQLException | IOException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
            }
        };
    }

    private static RowMapper<GraphContent> contentJson() {
        return (rs, __) -> {
            try {
                InputStream stream = rs.getBinaryStream("content");

                return new GraphContent(
                        rs.getString("id"),
                        rs.getString("name"),
                        Type.fromCode(rs.getString("event_type")),
                        GraphType.valueOf(rs.getString("graph_type")),
                        rs.getBoolean("use_thread_mode"),
                        rs.getBoolean("use_weight"),
                        Json.mapper().readTree(stream.readAllBytes()));
            } catch (SQLException | IOException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
            }
        };
    }
}
