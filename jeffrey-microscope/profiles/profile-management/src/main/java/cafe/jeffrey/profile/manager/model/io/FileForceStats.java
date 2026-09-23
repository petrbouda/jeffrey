/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.manager.model.io;

import java.util.List;

/**
 * Fsync (file-force) latency summary from {@code jdk.FileForce}. Unlike read/write, force events carry no
 * byte count — the signal is purely latency, so this is a dedicated stat block rather than part of the
 * read/write throughput model.
 *
 * @param count         number of force operations
 * @param totalNanos    summed force duration
 * @param avgNanos      mean force duration
 * @param maxNanos      slowest single force
 * @param metadataCount how many forces also flushed file metadata ({@code metaData == true})
 * @param slowest       the slowest force operations, longest first
 */
public record FileForceStats(
        long count,
        long totalNanos,
        long avgNanos,
        long maxNanos,
        long metadataCount,
        List<FileForceOp> slowest) {

    public boolean hasEvents() {
        return count > 0;
    }

    /**
     * A single force operation.
     *
     * @param timeOffsetMillis offset from recording start
     * @param path             the file being forced
     * @param metaData         whether file metadata was also flushed
     * @param durationNanos    force duration
     * @param thread           the thread that issued the force
     */
    public record FileForceOp(
            long timeOffsetMillis,
            String path,
            boolean metaData,
            long durationNanos,
            String thread) {
    }
}
