package pbouda.jeffrey.repository;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.Json;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

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

    public static RowMapper<ObjectNode> contentJson() {
        return (rs, rowNum) -> {
            try {
                InputStream content = rs.getBinaryStream("content");
                return (ObjectNode) Json.mapper().readTree(content.readAllBytes());
            } catch (SQLException | IOException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
            }
        };
    }
}
