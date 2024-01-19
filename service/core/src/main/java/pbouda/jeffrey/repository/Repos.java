package pbouda.jeffrey.repository;

import org.springframework.jdbc.core.RowMapper;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

public abstract class Repos {

    public static RowMapper<byte[]> content() {
        return (rs, rowNum) -> {
            try {
                InputStream content = rs.getBinaryStream("content");
                return content.readAllBytes();
            } catch (SQLException | IOException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
            }
        };
    }
}
