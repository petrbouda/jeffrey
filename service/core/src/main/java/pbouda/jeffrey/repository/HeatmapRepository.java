package pbouda.jeffrey.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.support.SqlLobValue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class HeatmapRepository {

    private static final String INSERT = """
            INSERT INTO main.heatmaps (
                id,
                profile_id,
                name,
                created_at,
                content
            ) VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_INFO = """
            SELECT id, name, created_at
            FROM main.heatmaps WHERE id = ? AND profile_id = ?
            """;

    private static final String SELECT_CONTENT = """
            SELECT content
            FROM main.heatmaps WHERE id = ? AND profile_id = ?
            """;

    private static final String SELECT_CONTENT_BY_NAME = """
            SELECT content
            FROM main.heatmaps WHERE name = ? AND profile_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public HeatmapRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(HeatmapInfo heatmap, byte[] content) {
        jdbcTemplate.update(
                INSERT,
                new Object[]{
                        heatmap.id(),
                        heatmap.profileId(),
                        heatmap.name(),
                        heatmap.createdAt().getEpochSecond(),
                        new SqlLobValue(content)
                },
                new int[]{Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.INTEGER, Types.BLOB});
    }

    public HeatmapInfo info(String profileId, String heatmapId) {
        return jdbcTemplate.queryForObject(SELECT_INFO, HeatmapRepository::infoMapper, heatmapId, profileId);
    }

    public Optional<byte[]> content(String profileId, String heatmapId) {
        try {
            byte[] content = jdbcTemplate.queryForObject(SELECT_CONTENT, Repos.content(), heatmapId, profileId);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<byte[]> contentByName(String profileId, String heatmapName) {
        try {
            byte[] content = jdbcTemplate.queryForObject(
                    SELECT_CONTENT_BY_NAME, Repos.content(), heatmapName, profileId);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<HeatmapInfo> all(String profileId) {
        return jdbcTemplate.query(
                "SELECT * FROM main.heatmaps WHERE profile_id = ?", HeatmapRepository::infoMapper, profileId);
    }

    public void delete(String profileId, String heatmapId) {
        jdbcTemplate.update("DELETE FROM main.heatmaps WHERE id = ? AND profile_id = ?", profileId, heatmapId);
    }

    public void deleteByProfileId(String profileId) {
        jdbcTemplate.update("DELETE FROM main.heatmaps WHERE profile_id = ?", profileId);
    }


    private static HeatmapInfo infoMapper(ResultSet rs, int ignored) {
        try {
            return new HeatmapInfo(
                    rs.getString("id"),
                    rs.getString("profile_id"),
                    rs.getString("name"),
                    Instant.ofEpochSecond(rs.getInt("created_at")));
        } catch (SQLException e) {
            throw new RuntimeException("Cannot retrieve a flamegraph info", e);
        }
    }
}
