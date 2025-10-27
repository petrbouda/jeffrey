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

package pbouda.jeffrey.provider.writer.clickhouse.model;

import pbouda.jeffrey.provider.writer.sql.SingleThreadHasher;

import java.util.List;

public record ClickHouseStacktrace(
        String profileId,
        long stackHash,
        List<Long> frameHashes) {

    /**
     * Creates a new stacktrace.
     */
    public static ClickHouseStacktrace create(
            String profileId,
            long stackHash,
            List<Long> frameHashes) {

        return new ClickHouseStacktrace(profileId, stackHash, frameHashes);
    }

    /**
     * Creates a stacktrace from frame hashes, calculating the stack hash automatically.
     * Uses XXHash64 for consistent, high-performance hashing.
     */
    public static ClickHouseStacktrace fromFrameHashes(
            String profileId,
            List<Long> frameHashes) {

        long stackHash = SingleThreadHasher.stacktraceHash(frameHashes);
        return create(profileId, stackHash, frameHashes);
    }

    /**
     * Returns the depth (number of frames) of this stacktrace.
     */
    public int depth() {
        return frameHashes.size();
    }
}
