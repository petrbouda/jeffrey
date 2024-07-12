/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.repository;

import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.repository.model.GraphContent;
import pbouda.jeffrey.repository.model.GraphInfo;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.Instant;

public abstract class Repos {

    public static RowMapper<byte[]> contentByteArray() {
        return (rs, __) -> {
            try {
                InputStream content = rs.getBinaryStream("content");
                return content.readAllBytes();
            } catch (SQLException | IOException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
            }
        };
    }

    public static RowMapper<GraphContent> contentJson() {
        return (rs, __) -> {
            try {
                InputStream stream = rs.getBinaryStream("content");

                return new GraphContent(
                        rs.getString("id"),
                        rs.getString("name"),
                        Type.fromCode(rs.getString("event_type")),
                        GraphType.valueOf(rs.getString("graph_type")),
                        rs.getBoolean("use_thread_mode"),
                        rs.getBoolean("use_weight"),
                        Json.mapper().readTree(stream.readAllBytes()));
            } catch (SQLException | IOException e) {
                throw new RuntimeException("Cannot retrieve a binary content", e);
            }
        };
    }

    public static RowMapper<GraphInfo> infoMapper() {
        return (rs, __) -> {
            try {
                return new GraphInfo(
                        rs.getString("id"),
                        rs.getString("profile_id"),
                        new Type(rs.getString("event_type")),
                        rs.getBoolean("use_thread_mode"),
                        rs.getBoolean("use_weight"),
                        rs.getBoolean("complete"),
                        rs.getString("name"),
                        Instant.ofEpochSecond(rs.getInt("created_at")));
            } catch (SQLException e) {
                throw new RuntimeException("Cannot retrieve a flamegraph info", e);
            }
        };
    }
}
