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

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.support.SqlLobValue;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Optional;

public class JdbcProfileCacheRepository implements ProfileCacheRepository {

    //language=SQL
    private static final String INSERT = """
            INSERT INTO cache (profile_id, key, content)
            VALUES (?, ?, ?)
            ON CONFLICT (profile_id, key) DO UPDATE SET content = EXCLUDED.content""";

    //language=SQL
    private static final String GET = "SELECT content FROM cache WHERE profile_id = ? AND key = ?";

    //language=SQL
    private static final String KEY_EXISTS = "SELECT count(*) FROM cache WHERE profile_id = ? AND key = ?";

    private final String profileId;
    private final JdbcClient jdbcClient;

    public JdbcProfileCacheRepository(String profileId, JdbcClient jdbcClient) {
        this.profileId = profileId;
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void put(String key, Object content) {
        jdbcClient.sql(INSERT)
                .params(profileId, key, new SqlLobValue(Json.toByteArray(content)))
                .update();
    }

    @Override
    public boolean contains(String key) {
        return jdbcClient.sql(KEY_EXISTS)
                .params(profileId, key)
                .query(Integer.class)
                .optional()
                .map(count -> count > 0)
                .orElse(false);
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        return jdbcClient.sql(GET)
                .params(profileId, key)
                .query(typedMapper(type))
                .optional()
                .orElse(Optional.empty());
    }

    @Override
    public <T> Optional<T> get(String key, TypeReference<T> type) {
        return jdbcClient.sql(GET)
                .params(profileId, key)
                .query(typedMapper(type))
                .optional()
                .orElse(Optional.empty());
    }

    public static <T> RowMapper<Optional<T>> typedMapper(Class<T> type) {
        return (rs, __) -> {
            try {
                InputStream stream = rs.getBinaryStream("content");
                return Optional.ofNullable(stream)
                        .map(JdbcProfileCacheRepository::streamToString)
                        .map(content -> Json.read(content, type));
            } catch (SQLException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
            }
        };
    }

    public static <T> RowMapper<Optional<T>> typedMapper(TypeReference<T> type) {
        return (rs, __) -> {
            try {
                InputStream stream = rs.getBinaryStream("content");
                return Optional.ofNullable(stream)
                        .map(JdbcProfileCacheRepository::streamToString)
                        .map(content -> Json.read(content, type));
            } catch (SQLException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
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
