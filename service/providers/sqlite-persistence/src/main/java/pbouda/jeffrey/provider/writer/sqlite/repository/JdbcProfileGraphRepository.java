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
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.provider.api.model.graph.GraphMetadata;
import pbouda.jeffrey.provider.api.model.graph.SavedGraphData;
import pbouda.jeffrey.provider.api.repository.ProfileGraphRepository;

import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JdbcProfileGraphRepository implements ProfileGraphRepository {

    private static final int[] INSERT_TYPES = new int[]{
            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BLOB, Types.INTEGER};

    //language=SQL
    private static final String INSERT = """
            INSERT INTO saved_graphs (
                profile_id,
                id,
                name,
                params,
                content,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;

    //language=SQL
    private static final String SELECT_CONTENT = """
            SELECT id, name, params, content, created_at
            FROM saved_graphs WHERE id = ? AND profile_id = ?
            """;

    //language=SQL
    private static final String DELETE = """
            DELETE FROM saved_graphs WHERE id = ? AND profile_id = ?
            """;

    //language=SQL
    private static final String ALL_METADATA = """
            SELECT * FROM saved_graphs WHERE profile_id = ?
            """;

    private final String profileId;
    private final JdbcTemplate jdbcTemplate;

    public JdbcProfileGraphRepository(String profileId, JdbcTemplate jdbcTemplate) {
        this.profileId = profileId;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(GraphMetadata metadata, JsonNode content) {
        jdbcTemplate.update(
                INSERT,
                new Object[]{
                        profileId,
                        metadata.id(),
                        metadata.name(),
                        metadata.params().toString(),
                        new SqlLobValue(content.toString()),
                        metadata.createdAt().toEpochMilli(),
                }, INSERT_TYPES);
    }

    @Override
    public Optional<SavedGraphData> get(String graphId) {
        try {
            SavedGraphData content = jdbcTemplate.queryForObject(
                    SELECT_CONTENT, JdbcProfileGraphRepository.contentJson(), graphId, profileId);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<GraphMetadata> getAllMetadata() {
        return jdbcTemplate.query(ALL_METADATA, JdbcProfileGraphRepository.metadataMapper(), profileId);
    }

    @Override
    public void delete(String graphId) {
        jdbcTemplate.update(DELETE, graphId, profileId);
    }

    private static RowMapper<GraphMetadata> metadataMapper() {
        return (rs, __) -> {
            try {
                return metadata(rs);
            } catch (SQLException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
            }
        };
    }

    private static RowMapper<SavedGraphData> contentJson() {
        return (rs, __) -> {
            try {
                GraphMetadata metadata = metadata(rs);
                InputStream stream = rs.getBinaryStream("content");
                return new SavedGraphData(metadata, Json.readTree(stream));
            } catch (SQLException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
            }
        };
    }

    private static GraphMetadata metadata(ResultSet rs) throws SQLException {
        return new GraphMetadata(
                rs.getString("id"),
                rs.getString("name"),
                Json.readTree(rs.getString("params")),
                Instant.ofEpochMilli(rs.getLong("created_at")));
    }
}
