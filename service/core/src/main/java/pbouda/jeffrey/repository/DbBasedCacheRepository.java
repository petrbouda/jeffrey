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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.support.SqlLobValue;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.persistence.CacheRepository;

import java.sql.Types;
import java.util.Optional;

import static pbouda.jeffrey.repository.Repos.*;

public class DbBasedCacheRepository implements CacheRepository {

    private static final String INSERT = """
            INSERT INTO cache (key, content) VALUES (?, ?)
            """;

    private static final String GET = """
            SELECT content FROM cache WHERE key = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public DbBasedCacheRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(String key, Object content) {
        jdbcTemplate.update(
                INSERT,
                new Object[]{key, new SqlLobValue(Json.toBytes(content))},
                new int[]{Types.VARCHAR, Types.BLOB});
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            return jdbcTemplate.queryForObject(GET, typedMapper(type), key);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public <T> Optional<T> get(String key, TypeReference<T> type) {
        try {
            return jdbcTemplate.queryForObject(GET, typedMapper(type), key);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }
}
