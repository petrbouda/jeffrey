package pbouda.jeffrey.repository;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.support.SqlLobValue;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.manager.GraphType;
import pbouda.jeffrey.repository.model.GraphContent;
import pbouda.jeffrey.repository.model.GraphInfo;

import java.sql.Types;
import java.util.List;
import java.util.Optional;

public class GraphRepository {

    private static final int[] INSERT_TYPES = new int[]{
            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BOOLEAN,
            Types.VARCHAR, Types.INTEGER, Types.BLOB};

    private static final String INSERT = """
            INSERT INTO flamegraphs (
                id,
                profile_id,
                event_type,
                graph_type,
                complete,
                name,
                created_at,
                content
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_CONTENT = """
            SELECT id, name, graph_type, content
            FROM flamegraphs WHERE id = ? AND profile_id = ? AND complete IS NULL
            """;

    private static final String SELECT_CONTENT_BY_EVENT_TYPE = """
            SELECT id, name, graph_type, content
            FROM flamegraphs WHERE profile_id = ? AND event_type = ? AND graph_type = ? AND complete IS NOT NULL
            """;

    private static final String DELETE = """
            DELETE FROM flamegraphs WHERE id = ? AND profile_id = ?
            """;

    private static final String DELETE_BY_PROFILE = """
            DELETE FROM flamegraphs WHERE profile_id = ?
            """;

    private static final String ALL_CUSTOM = """
            SELECT * FROM flamegraphs WHERE profile_id = ? AND complete IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;
    private final GraphType graphType;

    public GraphRepository(JdbcTemplate jdbcTemplate, GraphType graphType) {
        this.jdbcTemplate = jdbcTemplate;
        this.graphType = graphType;
    }

    public void insert(GraphInfo fg, ObjectNode content) {
        jdbcTemplate.update(
                INSERT,
                new Object[]{
                        fg.id(),
                        fg.profileId(),
                        fg.eventType().code(),
                        graphType.name(),
                        fg.complete() ? 1 : null,
                        fg.name(),
                        fg.createdAt().getEpochSecond(),
                        new SqlLobValue(content.toString())
                }, INSERT_TYPES);
    }

    public Optional<GraphContent> content(String profileId, String fgId) {
        try {
            GraphContent content = jdbcTemplate.queryForObject(
                    SELECT_CONTENT, Repos.contentJson(), fgId, profileId);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<GraphContent> content(String profileId, EventType eventType) {
        try {
            GraphContent content = jdbcTemplate.queryForObject(
                    SELECT_CONTENT_BY_EVENT_TYPE, Repos.contentJson(), profileId, eventType.code(), graphType.name());
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<GraphInfo> allCustom(String profileId) {
        return jdbcTemplate.query(ALL_CUSTOM, Repos.infoMapper(), profileId);
    }

    public void delete(String profileId, String fgId) {
        jdbcTemplate.update(DELETE, fgId, profileId);
    }

    public void deleteByProfileId(String profileId) {
        jdbcTemplate.update(DELETE_BY_PROFILE, profileId);
    }
}
