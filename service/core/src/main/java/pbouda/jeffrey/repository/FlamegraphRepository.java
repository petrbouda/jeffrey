package pbouda.jeffrey.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.support.SqlLobValue;
import pbouda.jeffrey.manager.EventType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class FlamegraphRepository {

    private static final String INSERT = """
            INSERT INTO flamegraphs (
                id,
                profile_id,
                name,
                created_at,
                content
            ) VALUES (?, ?, ?, ?, ?)
            """;

    private static final String INSERT_PREDEFINED = """
            INSERT INTO flamegraphs_predefined (
                profile_id,
                event_type,
                content
            ) VALUES (?, ?, ?)
            """;

    private static final String SELECT_INFO = """
            SELECT id, name, created_at
            FROM flamegraphs WHERE id = ? AND profile_id = ?
            """;

    private static final String SELECT_CONTENT = """
            SELECT content
            FROM flamegraphs WHERE id = ? AND profile_id = ?
            """;

    private static final String SELECT_CONTENT_BY_EVENT_TYPE = """
            SELECT content
            FROM flamegraphs_predefined WHERE profile_id = ? AND event_type = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public FlamegraphRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(FlamegraphInfo fg, byte[] content) {
        jdbcTemplate.update(
                INSERT,
                new Object[]{
                        fg.id(),
                        fg.profileId(),
                        fg.name(),
                        fg.createdAt().getEpochSecond(),
                        new SqlLobValue(content)
                },
                new int[]{Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.INTEGER, Types.BLOB});
    }

    public void insert(String profileId, EventType eventType, byte[] content) {
        jdbcTemplate.update(
                INSERT_PREDEFINED,
                new Object[]{
                        profileId,
                        eventType.code(),
                        new SqlLobValue(content)
                },
                new int[]{Types.VARCHAR, Types.VARCHAR, Types.BLOB});
    }

    public FlamegraphInfo info(String profileId, String fgId) {
        return jdbcTemplate.queryForObject(SELECT_INFO, FlamegraphRepository::infoMapper, fgId, profileId);
    }

    public Optional<byte[]> content(String profileId, String fgId) {
        try {
            byte[] content = jdbcTemplate.queryForObject(SELECT_CONTENT, Repos.content(), fgId, profileId);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<byte[]> content(String profileId, EventType eventType) {
        try {
            byte[] content = jdbcTemplate.queryForObject(
                    SELECT_CONTENT_BY_EVENT_TYPE, Repos.content(), profileId, eventType.code());
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<FlamegraphInfo> all(String profileId) {
        return jdbcTemplate.query(
                "SELECT * FROM flamegraphs WHERE profile_id = ?",
                FlamegraphRepository::infoMapper,
                profileId);
    }

    public void delete(String profileId, String fgId) {
        jdbcTemplate.update(
                "DELETE FROM flamegraphs WHERE id = ? AND profile_id = ?", fgId, profileId);
    }

    public void deleteByProfileId(String profileId) {
        jdbcTemplate.update("DELETE FROM flamegraphs WHERE profile_id = ?", profileId);
        jdbcTemplate.update("DELETE FROM flamegraphs_predefined WHERE profile_id = ?", profileId);
    }

    private static FlamegraphInfo infoMapper(ResultSet rs, int ignored) {
        try {
            return new FlamegraphInfo(
                    rs.getString("id"),
                    rs.getString("profile_id"),
                    rs.getString("name"),
                    Instant.ofEpochSecond(rs.getInt("created_at")));
        } catch (SQLException e) {
            throw new RuntimeException("Cannot retrieve a flamegraph info", e);
        }
    }
}
