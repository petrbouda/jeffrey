/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
