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

package pbouda.jeffrey.provider.profile.query;

import org.eclipse.collections.api.map.primitive.LongObjectMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import pbouda.jeffrey.jfrparser.db.type.DbJfrStackFrame;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Cache for frame data to enable fast Java-side frame resolution.
 * Uses Eclipse Collections {@link LongObjectHashMap} for primitive long keys to avoid boxing overhead.
 * Loading all frames once is much faster than resolving them in SQL for each query.
 */
public class FramesCache {

    private final LongObjectMap<DbJfrStackFrame> framesMap;

    private FramesCache(LongObjectMap<DbJfrStackFrame> framesMap) {
        this.framesMap = framesMap;
    }

    /**
     * Loads all frames from the database into memory.
     *
     * @param databaseClient the database client for querying
     * @return a new FramesCache with all frames loaded
     */
    public static FramesCache load(DatabaseClient databaseClient) {
        LongObjectHashMap<DbJfrStackFrame> framesMap = new LongObjectHashMap<>();

        List<FrameEntry> entries = databaseClient.query(
                StatementLabel.LOAD_FRAMES_CACHE,
                DuckDBFlamegraphQueries.ALL_FRAMES,
                (rs, rowNum) -> new FrameEntry(
                        rs.getLong("frame_hash"),
                        new DbJfrStackFrame(
                                rs.getString("class_name"),
                                rs.getString("method_name"),
                                rs.getString("frame_type"),
                                rs.getInt("line_number"),
                                rs.getInt("bytecode_index")
                        )
                )
        );

        for (FrameEntry entry : entries) {
            framesMap.put(entry.hash, entry.frame);
        }

        return new FramesCache(framesMap);
    }

    private record FrameEntry(long hash, DbJfrStackFrame frame) {
    }

    /**
     * Resolves frame hashes to frame data using primitive long array.
     *
     * @param frameHashes array of frame hashes to resolve
     * @return list of resolved frames, may be smaller if some hashes are not found
     */
    public List<DbJfrStackFrame> resolveFrames(long[] frameHashes) {
        if (frameHashes == null) {
            return null;
        }

        List<DbJfrStackFrame> frames = new ArrayList<>(frameHashes.length);
        for (long hash : frameHashes) {
            DbJfrStackFrame frame = framesMap.get(hash);
            if (frame != null) {
                frames.add(frame);
            }
        }
        return frames;
    }
}
