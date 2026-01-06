/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.jmh.flamegraph.utils;

import org.springframework.jdbc.core.JdbcTemplate;
import pbouda.jeffrey.jfrparser.db.type.DbJfrStackFrame;
import pbouda.jeffrey.provider.profile.query.DuckDBFlamegraphQueries;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cache for frame data to enable fast Java-side frame resolution.
 * Loading all frames once is much faster than resolving them in SQL for each query.
 */
public class FramesCache {

    private final Map<Long, DbJfrStackFrame> framesMap;

    private FramesCache(Map<Long, DbJfrStackFrame> framesMap) {
        this.framesMap = framesMap;
    }

    /**
     * Loads all frames from the database into memory.
     */
    public static FramesCache load(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String sql = DuckDBFlamegraphQueries.ALL_FRAMES;

        Map<Long, DbJfrStackFrame> framesMap = new HashMap<>();

        jdbcTemplate.query(sql, rs -> {
            long frameHash = rs.getLong("frame_hash");
            DbJfrStackFrame frame = new DbJfrStackFrame(
                    rs.getString("class_name"),
                    rs.getString("method_name"),
                    rs.getString("frame_type"),
                    rs.getInt("line_number"),
                    rs.getInt("bytecode_index")
            );
            framesMap.put(frameHash, frame);
        });

        return new FramesCache(framesMap);
    }

    /**
     * Resolves frame hashes to frame data.
     */
    public List<DbJfrStackFrame> resolveFrames(Long[] frameHashes) {
        if (frameHashes == null) {
            return null;
        }

        List<DbJfrStackFrame> frames = new ArrayList<>(frameHashes.length);
        for (Long hash : frameHashes) {
            DbJfrStackFrame frame = framesMap.get(hash);
            if (frame != null) {
                frames.add(frame);
            }
        }
        return frames;
    }

    /**
     * Returns the number of cached frames.
     */
    public int size() {
        return framesMap.size();
    }
}
