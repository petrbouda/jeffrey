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

package pbouda.jeffrey.provider.profile.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.support.SqlLobValue;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.io.IOException;
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

    private final DatabaseClient databaseClient;

    public JdbcProfileCacheRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROFILE_CACHE);
    }

    @Override
    public void put(String key, Object content) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("key", key)
                .addValue("content", new SqlLobValue(Json.toByteArray(content)), Types.BLOB);

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
        return (rs, __) -> {
            Blob blob = null;
            try {
                blob = rs.getBlob("content");
                if (blob != null) {
                    return Json.read(streamToString(blob.getBinaryStream()), type);
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

    public static <T> RowMapper<T> typedMapper(TypeReference<T> type) {
        return (rs, __) -> {
            Blob blob = null;
            try {
                blob = rs.getBlob("content");
                if (blob != null) {
                    return Json.read(streamToString(blob.getBinaryStream()), type);
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

    private static String streamToString(InputStream stream) {
        try {
            return new String(stream.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("Cannot retrieve a binary content", e);
        }
    }
}
