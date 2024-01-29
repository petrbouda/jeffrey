package pbouda.jeffrey.repository;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.support.SqlLobValue;

import java.sql.Types;
import java.util.Optional;

public class CommonRepository {

    private static final String INSERT_INFO = """
            INSERT INTO profile_information (
                profile_id,
                content
            ) VALUES (?, ?)
            """;

    private static final String SELECT_INFO = """
            SELECT content FROM profile_information WHERE  profile_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public CommonRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertInformation(String profileId, ObjectNode content) {
        jdbcTemplate.update(
                INSERT_INFO,
                new Object[]{profileId, new SqlLobValue(content.toString())},
                new int[]{Types.VARCHAR, Types.BLOB});
    }

    public Optional<byte[]> selectInformation(String profileId) {
        try {
            byte[] content = jdbcTemplate.queryForObject(SELECT_INFO, Repos.content(), profileId);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }
}
