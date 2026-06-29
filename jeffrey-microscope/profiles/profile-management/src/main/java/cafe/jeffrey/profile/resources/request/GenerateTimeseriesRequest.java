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

package cafe.jeffrey.profile.resources.request;

import cafe.jeffrey.profile.TimeRangeRequest;
import cafe.jeffrey.shared.common.model.ThreadInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.profile.common.analysis.marker.Marker;

import java.util.List;

/**
 * @param timeRange     optional window (relative millis from recording start) to restrict the
 *                      query to; {@code null} means the whole recording.
 * @param targetBuckets optional cap on the number of returned points. When set, the series is
 *                      aggregated server-side into at most this many buckets, so long recordings
 *                      stay readable; {@code null} keeps the full per-second resolution (backward
 *                      compatible). Presence of {@code targetBuckets} also selects the overview
 *                      activity query (no stacktrace join).
 * @param allEventTypes when {@code true}, aggregate across every event type (total activity) and
 *                      ignore {@code eventType}. Only honoured by the overview query.
 */
public record GenerateTimeseriesRequest(
        Type eventType,
        String search,
        boolean useWeight,
        boolean excludeNonJavaSamples,
        boolean excludeIdleSamples,
        boolean onlyUnsafeAllocationSamples,
        ThreadInfo threadInfo,
        List<Marker> markers,
        TimeRangeRequest timeRange,
        Integer targetBuckets,
        boolean allEventTypes) {
}
