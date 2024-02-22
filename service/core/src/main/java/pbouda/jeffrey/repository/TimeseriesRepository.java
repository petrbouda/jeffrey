package pbouda.jeffrey.repository;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.support.SqlLobValue;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.repository.model.HeatmapInfo;
import pbouda.jeffrey.repository.model.TimeseriesInfo;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class TimeseriesRepository {

    private static final String INSERT = """
            INSERT INTO main.timeseries (
                id,
                profile_id,
                event_type,
                created_at,
                content
            ) VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_CONTENT = """
            SELECT content
            FROM main.timeseries WHERE id = ? AND profile_id = ?
            """;

    private static final String SELECT_CONTENT_BY_EVENT_TYPE = """
            SELECT content
            FROM main.timeseries WHERE event_type = ? AND profile_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public TimeseriesRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(TimeseriesInfo timeseries, ArrayNode content) {
        jdbcTemplate.update(
                INSERT,
                new Object[]{
                        timeseries.id(),
                        timeseries.profileId(),
                        timeseries.eventType().code(),
                        timeseries.createdAt().getEpochSecond(),
                        new SqlLobValue(content.toString().getBytes())
                },
                new int[]{Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.INTEGER, Types.BLOB});
    }

    public Optional<byte[]> content(String profileId, String timeseriesId) {
        try {
            byte[] content = jdbcTemplate.queryForObject(SELECT_CONTENT, Repos.contentByteArray(), timeseriesId, profileId);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<byte[]> contentByEventType(String profileId, EventType eventType) {
        try {
            byte[] content = jdbcTemplate.queryForObject(
                    SELECT_CONTENT_BY_EVENT_TYPE, Repos.contentByteArray(), eventType.code(), profileId);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public void delete(String profileId, String timeseriesId) {
        jdbcTemplate.update("DELETE FROM main.timeseries WHERE id = ? AND profile_id = ?", timeseriesId, profileId);
    }

    public void deleteByProfileId(String profileId) {
        jdbcTemplate.update("DELETE FROM main.heatmaps WHERE profile_id = ?", profileId);
    }


    private static TimeseriesInfo infoMapper(ResultSet rs, int ignored) {
        try {
            return new TimeseriesInfo(
                    rs.getString("id"),
                    rs.getString("profile_id"),
                    new EventType(rs.getString("event_type")),
                    Instant.ofEpochSecond(rs.getInt("created_at")));
        } catch (SQLException e) {
            throw new RuntimeException("Cannot retrieve a timeseries info", e);
        }
    }
}
