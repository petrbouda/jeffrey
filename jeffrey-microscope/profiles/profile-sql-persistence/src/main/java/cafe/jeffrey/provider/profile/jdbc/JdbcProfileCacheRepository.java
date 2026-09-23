/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import tools.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.support.SqlBinaryValue;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.io.IOException;
import java.util.function.Function;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

public class JdbcProfileCacheRepository implements ProfileCacheRepository {

    //language=SQL
    private static final String INSERT = """
            INSERT INTO cache (key, content)
            VALUES (:key, :content)
            ON CONFLICT (key) DO UPDATE SET content = EXCLUDED.content""";

    //language=SQL
    private static final String GET = "SELECT content FROM cache WHERE key = :key";

    //language=SQL
    private static final String KEY_EXISTS = "SELECT count(*) FROM cache WHERE key = :key";

    //language=SQL
    private static final String DELETE_ALL = "DELETE FROM cache";

    private final DatabaseClient databaseClient;

    public JdbcProfileCacheRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROFILE_CACHE);
    }

    @Override
    public void put(String key, Object content) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("key", key)
                // VARBINARY, not BLOB, even though the column is a BLOB: SqlBinaryValue binds
                // Types.BLOB through PreparedStatement.setBlob, which the DuckDB driver does not
                // implement. Every other type goes through setBytes — the same call the deprecated
                // SqlLobValue made via DefaultLobHandler.
                .addValue("content", new SqlBinaryValue(Json.toByteArray(content)), Types.VARBINARY);

        databaseClient.insertWithLob(StatementLabel.INSERT_CACHE_ENTRY, INSERT, paramSource);
    }

    @Override
    public boolean contains(String key) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("key", key);

        return databaseClient.queryExists(StatementLabel.KEY_EXISTS, KEY_EXISTS, paramSource);
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("key", key);

        return databaseClient.querySingle(StatementLabel.FIND_CACHE_ENTRY, GET, paramSource, typedMapper(type));
    }

    @Override
    public <T> Optional<T> get(String key, TypeReference<T> type) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("key", key);

        return databaseClient.querySingle(StatementLabel.FIND_CACHE_ENTRY, GET, paramSource, typedMapper(type));
    }

    public static <T> RowMapper<T> typedMapper(Class<T> type) {
        return blobMapper(content -> Json.read(content, type));
    }

    public static <T> RowMapper<T> typedMapper(TypeReference<T> type) {
        return blobMapper(content -> Json.read(content, type));
    }

    private static <T> RowMapper<T> blobMapper(Function<String, T> deserializer) {
        return (rs, _) -> {
            Blob blob = null;
            try {
                blob = rs.getBlob("content");
                if (blob != null) {
                    return deserializer.apply(streamToString(blob.getBinaryStream()));
                }
                return null;
            } catch (SQLException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
            } finally {
                if (blob != null) {
                    blob.free();
                }
            }
        };
    }

    @Override
    public void clearAll() {
        databaseClient.delete(StatementLabel.DELETE_ALL_CACHE, DELETE_ALL);
    }

    private static String streamToString(InputStream stream) {
        try {
            return new String(stream.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("Cannot retrieve a binary content", e);
        }
    }
}
