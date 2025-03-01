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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.support.SqlLobValue;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

public class JdbcProfileCacheRepository implements ProfileCacheRepository {

    private static final String INSERT = """
            INSERT INTO cache (profile_id, key, content)
            VALUES (?, ?, ?)
            ON CONFLICT (key) DO UPDATE SET content = EXCLUDED.content
            """;

    private static final String GET = """
            SELECT content FROM cache WHERE profile_id = ? AND key = ?
            """;

    private final String profileId;
    private final JdbcTemplate jdbcTemplate;

    public JdbcProfileCacheRepository(String profileId, JdbcTemplate jdbcTemplate) {
        this.profileId = profileId;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(String key, Object content) {
        jdbcTemplate.update(
                INSERT,
                new Object[]{profileId, key, new SqlLobValue(Json.toByteArray(content))},
                new int[]{Types.VARCHAR, Types.VARCHAR, Types.BLOB});
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            return jdbcTemplate.queryForObject(GET, typedMapper(type), profileId, key);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public <T> Optional<T> get(String key, TypeReference<T> type) {
        try {
            return jdbcTemplate.queryForObject(GET, typedMapper(type), profileId, key);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
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
