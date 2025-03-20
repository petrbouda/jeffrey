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
import pbouda.jeffrey.provider.api.repository.ProjectKeyValueRepository;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

public class JdbcProjectKeyValueRepository implements ProjectKeyValueRepository {

    private final String projectId;
    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_KV_STORE = """
            INSERT OR IGNORE INTO kv_store (project_id, key, content) VALUES (?, ?, ?)
            """;

    private static final String GET_FROM_KV_STORE = """
            SELECT content FROM kv_store WHERE project_id = ? AND key = ?
            """;

    private static final String DELETE_FROM_KV_STORE = """
            DELETE FROM kv_store WHERE project_id = ? AND key = ?
            """;

    public JdbcProjectKeyValueRepository(String projectId, JdbcTemplate jdbcTemplate) {
        this.projectId = projectId;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(Key key, JsonNode content) {
        insert(key, content.toString());
    }

    @Override
    public void insert(Key key, String content) {
        jdbcTemplate.update(
                INSERT_KV_STORE,
                new Object[]{projectId, key.name(), new SqlLobValue(content)},
                new int[]{Types.VARCHAR, Types.VARCHAR, Types.BLOB});
    }

    @Override
    public void delete(Key key) {
        jdbcTemplate.update(DELETE_FROM_KV_STORE, projectId, key.name());
    }

    @Override
    public Optional<String> getString(Key key) {
        try {
            String content = jdbcTemplate.queryForObject(
                    GET_FROM_KV_STORE, stringMapper(), projectId, key);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<JsonNode> getJson(Key key) {
        try {
            JsonNode content = jdbcTemplate.queryForObject(
                    GET_FROM_KV_STORE, jsonMapper("content"), projectId, key);
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
}
