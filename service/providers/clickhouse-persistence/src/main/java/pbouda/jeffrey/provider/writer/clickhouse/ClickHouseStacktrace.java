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

package pbouda.jeffrey.provider.writer.clickhouse;

import java.util.List;

/**
 * Represents a collapsed stacktrace from ClickHouse with aggregated metrics.
 * This directly maps from ClickHouse query results without intermediate Frame objects.
 */
public record ClickHouseStacktrace(
        long stacktraceId,
        long totalSamples,
        long totalWeight,
        String weightEntity,
        List<ClickHouseFrameData> frames) {

    /**
     * Creates a stacktrace from structured ClickHouse frame data.
     */
    public static ClickHouseStacktrace of(
            long stacktraceId,
            long totalSamples,
            long totalWeight,
            String weightEntity,
            List<ClickHouseFrameData> frames) {

        return new ClickHouseStacktrace(stacktraceId, totalSamples, totalWeight, weightEntity, frames);
    }

    /**
     * Returns the depth of this stacktrace (number of frames).
     */
    public int depth() {
        return frames.size();
    }

    /**
     * Returns frames in root-to-leaf order (reverse of typical stacktrace order).
     */
    public List<ClickHouseFrameData> rootToLeafFrames() {
        List<ClickHouseFrameData> reversed = new java.util.ArrayList<>(frames);
        java.util.Collections.reverse(reversed);
        return reversed;
    }
}
