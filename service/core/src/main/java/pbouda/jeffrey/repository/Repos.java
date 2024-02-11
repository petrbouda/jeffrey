package pbouda.jeffrey.repository;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.Json;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.manager.GraphType;

import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

public abstract class Repos {

    public static RowMapper<byte[]> contentByteArray() {
        return (rs, rowNum) -> {
            try {
                InputStream content = rs.getBinaryStream("content");
                return content.readAllBytes();
            } catch (SQLException | IOException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
            }
        };
    }

    public static RowMapper<GraphContent> contentJson() {
        return (rs, rowNum) -> {
            try {
                InputStream stream = rs.getBinaryStream("content");

                return new GraphContent(
                        rs.getString("id"),
                        rs.getString("name"),
                        GraphType.valueOf(rs.getString("graph_type")),
                        (ObjectNode) Json.mapper().readTree(stream.readAllBytes()));
            } catch (SQLException | IOException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
            }
        };
    }

    public static RowMapper<GraphInfo> infoMapper() {
        return (rs, rowNum) -> {
            try {
                return new GraphInfo(
                        rs.getString("id"),
                        rs.getString("profile_id"),
                        new EventType(rs.getString("event_type")),
                        rs.getBoolean("complete"),
                        rs.getString("name"),
                        Instant.ofEpochSecond(rs.getInt("created_at")));
            } catch (SQLException e) {
                throw new RuntimeException("Cannot retrieve a flamegraph info", e);
            }
        };
    }
}
