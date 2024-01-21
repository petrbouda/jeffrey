package pbouda.jeffrey.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.support.SqlLobValue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

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

    private static final String SELECT_INFO = """
            SELECT id, name, created_at
            FROM main.flamegraphs WHERE id = ? AND profile_id = ?
            """;

    private static final String SELECT_CONTENT = """
            SELECT content
            FROM main.flamegraphs WHERE id = ? AND profile_id = ?
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

    public FlamegraphInfo info(String profileId, String fgId) {
        return jdbcTemplate.queryForObject(SELECT_INFO, FlamegraphRepository::infoMapper, fgId, profileId);
    }

    public byte[] content(String profileId, String fgId) {
        return jdbcTemplate.queryForObject(SELECT_CONTENT, Repos.content(), fgId, profileId);
    }

    public List<FlamegraphInfo> all(String profileId) {
        return jdbcTemplate.query(
                "SELECT * FROM main.flamegraphs WHERE profile_id = ?",
                FlamegraphRepository::infoMapper,
                profileId);
    }

    public void delete(String profileId, String fgId) {
        jdbcTemplate.update(
                "DELETE FROM main.flamegraphs WHERE id = ? AND profile_id = ?", fgId, profileId);
    }

    public void deleteByProfileId(String profileId) {
        jdbcTemplate.update("DELETE FROM main.flamegraphs WHERE profile_id = ?", profileId);
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
