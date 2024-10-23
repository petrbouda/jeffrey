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

package pbouda.jeffrey.repository.project;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.support.SqlLobValue;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

import static pbouda.jeffrey.repository.Repos.jsonMapper;

public class ProjectKeyValueRepository {

    private final JdbcTemplate jdbcTemplate;

    public enum Key {
        REPOSITORY_PATH,
    }

    private static final String INSERT_KV_STORE = """
            INSERT INTO kv_store (key, content) VALUES (?, ?)
                ON CONFLICT (key) DO UPDATE SET content = EXCLUDED.content
                         WHERE kv_store.key = EXCLUDED.key
            """;

    private static final String GET_FROM_KV_STORE = """
            SELECT content FROM kv_store WHERE key = ?
            """;

    private static final String DELETE_FROM_KV_STORE = """
            DELETE FROM kv_store WHERE key = ?
            """;

    public ProjectKeyValueRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Key key, JsonNode content) {
        jdbcTemplate.update(
                INSERT_KV_STORE,
                new Object[]{key.name(), new SqlLobValue(content.toString())},
                new int[]{Types.VARCHAR, Types.BLOB});
    }

    public void insert(Key key, String content) {
        jdbcTemplate.update(
                INSERT_KV_STORE,
                new Object[]{key.name(), new SqlLobValue(content)},
                new int[]{Types.VARCHAR, Types.BLOB});
    }

    public void delete(Key key) {
        jdbcTemplate.update(DELETE_FROM_KV_STORE, key.name());
    }

    public Optional<String> getString(Key key) {
        try {
            String content = jdbcTemplate.queryForObject(GET_FROM_KV_STORE, stringMapper(), key);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<JsonNode> getJson(Key key) {
        try {
            JsonNode content = jdbcTemplate.queryForObject(GET_FROM_KV_STORE, jsonMapper("content"), key);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private static RowMapper<String> stringMapper() {
        return (rs, __) -> {
            try {
                InputStream content = rs.getBinaryStream("content");
                return new String(content.readAllBytes());
            } catch (SQLException | IOException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
            }
        };
    }
}
