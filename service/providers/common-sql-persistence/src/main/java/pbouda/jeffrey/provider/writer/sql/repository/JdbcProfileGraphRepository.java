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

package pbouda.jeffrey.provider.writer.sql.repository;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.support.SqlLobValue;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.provider.api.model.graph.GraphMetadata;
import pbouda.jeffrey.provider.api.model.graph.SavedGraphData;
import pbouda.jeffrey.provider.api.repository.ProfileGraphRepository;
import pbouda.jeffrey.provider.writer.sql.GroupLabel;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;

import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcProfileGraphRepository implements ProfileGraphRepository {

    //language=SQL
    private static final String INSERT = """
            INSERT INTO saved_graphs (profile_id, id, name, params, content, created_at)
            VALUES (:profile_id, :id, :name, :params, :content, :created_at)""";

    //language=SQL
    private static final String SELECT_CONTENT = """
            SELECT id, name, params, content, created_at
            FROM saved_graphs WHERE id = :id AND profile_id = :profile_id""";

    //language=SQL
    private static final String DELETE =
            "DELETE FROM saved_graphs WHERE id = :id AND profile_id = :profile_id";

    //language=SQL
    private static final String ALL_METADATA =
            "SELECT * FROM saved_graphs WHERE profile_id = :profile_id";

    private final String profileId;
    private final DatabaseClient databaseClient;

    public JdbcProfileGraphRepository(String profileId, DatabaseClientProvider databaseClientProvider) {
        this.profileId = profileId;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROFILE_GRAPHS);
    }

    public void insert(GraphMetadata metadata, JsonNode content) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("id", metadata.id())
                .addValue("name", metadata.name())
                .addValue("params", metadata.params().toString())
                .addValue("content", new SqlLobValue(content.toString()), Types.BLOB)
                .addValue("created_at", metadata.createdAt().atOffset(ZoneOffset.UTC));

        databaseClient.insertWithLob(StatementLabel.INSERT_GRAPH, INSERT, paramSource);
    }

    @Override
    public Optional<SavedGraphData> get(String graphId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("id", graphId);

        return databaseClient.querySingle(
                StatementLabel.FIND_GRAPH_CONTENT,
                SELECT_CONTENT,
                paramSource,
                JdbcProfileGraphRepository.contentJson());
    }

    @Override
    public List<GraphMetadata> getAllMetadata() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId);

        return databaseClient.query(
                StatementLabel.FIND_ALL_METADATA,
                ALL_METADATA,
                paramSource,
                JdbcProfileGraphRepository.metadataMapper());
    }

    @Override
    public void delete(String graphId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("id", graphId);

        databaseClient.update(StatementLabel.DELETE_GRAPH, DELETE, paramSource);
    }

    private static RowMapper<GraphMetadata> metadataMapper() {
        return (rs, _) -> {
            try {
                return metadata(rs);
            } catch (SQLException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
            }
        };
    }

    private static RowMapper<SavedGraphData> contentJson() {
        return (rs, _) -> {
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
                Mappers.instant(rs, "created_at"));
    }
}
