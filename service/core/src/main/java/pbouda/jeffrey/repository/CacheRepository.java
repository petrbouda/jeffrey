package pbouda.jeffrey.repository;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.support.SqlLobValue;
import pbouda.jeffrey.common.Json;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

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
            JsonNode content = jdbcTemplate.queryForObject(GET, get(), key);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public static RowMapper<JsonNode> get() {
        return (rs, _) -> {
            try {
                InputStream content = rs.getBinaryStream("content");
                return Json.mapper().readTree(content.readAllBytes());
            } catch (SQLException | IOException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
            }
        };
    }
}
