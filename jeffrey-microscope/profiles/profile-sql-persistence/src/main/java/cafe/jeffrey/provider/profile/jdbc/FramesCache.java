/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import org.eclipse.collections.api.map.primitive.LongObjectMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrameImpl;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Cache for frame data to enable fast Java-side frame resolution.
 * Uses Eclipse Collections {@link LongObjectHashMap} for primitive long keys to avoid boxing overhead.
 * Loading all frames once is much faster than resolving them in SQL for each query.
 */
public class FramesCache {

    private final LongObjectMap<JfrStackFrameImpl> framesMap;

    private FramesCache(LongObjectMap<JfrStackFrameImpl> framesMap) {
        this.framesMap = framesMap;
    }

    /**
     * Loads all frames from the database into memory.
     *
     * @param databaseClient the database client for querying
     * @return a new FramesCache with all frames loaded
     */
    public static FramesCache load(DatabaseClient databaseClient) {
        LongObjectHashMap<JfrStackFrameImpl> framesMap = new LongObjectHashMap<>();

        List<FrameEntry> entries = databaseClient.query(
                StatementLabel.LOAD_FRAMES_CACHE,
                DuckDBFlamegraphQueries.ALL_FRAMES,
                (rs, rowNum) -> new FrameEntry(
                        rs.getLong("frame_hash"),
                        new JfrStackFrameImpl(
                                rs.getString("class_name"),
                                rs.getString("method_name"),
                                rs.getString("hidden_class_id"),
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

    private record FrameEntry(long hash, JfrStackFrameImpl frame) {
    }

    /**
     * Resolves frame hashes to frame data using primitive long array.
     *
     * @param frameHashes array of frame hashes to resolve
     * @return list of resolved frames, may be smaller if some hashes are not found
     */
    public List<JfrStackFrameImpl> resolveFrames(long[] frameHashes) {
        if (frameHashes == null) {
            return null;
        }

        List<JfrStackFrameImpl> frames = new ArrayList<>(frameHashes.length);
        for (long hash : frameHashes) {
            JfrStackFrameImpl frame = framesMap.get(hash);
            if (frame != null) {
                frames.add(frame);
            }
        }
        return frames;
    }
}
