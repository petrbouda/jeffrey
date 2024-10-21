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

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.support.SqlLobValue;

import java.sql.Types;
import java.util.Optional;

import static pbouda.jeffrey.repository.Repos.jsonMapper;

public class CacheRepository {

    private static final String INSERT = """
            INSERT INTO cache (key, content) VALUES (?, ?)
            """;

    private static final String GET = """
            SELECT content FROM cache WHERE key = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public CacheRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String key, JsonNode content) {
        jdbcTemplate.update(
                INSERT,
                new Object[]{key, new SqlLobValue(content.toString())},
                new int[]{Types.VARCHAR, Types.BLOB});
    }

    public Optional<JsonNode> get(String key) {
        try {
            JsonNode content = jdbcTemplate.queryForObject(GET, jsonMapper("content"), key);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }
}
