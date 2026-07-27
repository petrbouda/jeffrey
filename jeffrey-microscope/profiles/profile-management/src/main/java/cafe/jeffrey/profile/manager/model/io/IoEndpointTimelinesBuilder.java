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

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a bytes-per-second series per endpoint in a single pass, returning the heaviest endpoints
 * with their throughput shape. Feeds the sparkline gallery, where every peer's shape has to be
 * visible before the user picks one — one query instead of one request per tile.
 * <p>
 * Per-endpoint buckets are collected sparsely while streaming and only zero-filled across the whole
 * recording for the endpoints that actually make the cut, so a recording with thousands of peers
 * does not allocate a full-length series for each of them.
 */
public class IoEndpointTimelinesBuilder implements RecordBuilder<GenericRecord, List<IoEndpointTimeline>> {

    private static final String THROUGHPUT_SERIES_NAME = "Bytes / sec";

    private final RelativeTimeRange timeRange;
    private final int maxEndpoints;
    private final IoEndpointGrouping grouping = new IoEndpointGrouping();
    private final Map<String, LongLongHashMap> bytesPerSecondByTarget = new HashMap<>();

    public IoEndpointTimelinesBuilder(RelativeTimeRange timeRange, int maxEndpoints) {
        this.timeRange = timeRange;
        this.maxEndpoints = maxEndpoints;
    }

    @Override
    public void onRecord(GenericRecord record) {
        String target = IoEventFields.target(record.type(), record.jsonFields());
        grouping.record(target, record);

        long bytes = IoEventFields.bytes(record.type(), record.jsonFields());
        if (bytes <= 0) {
            return;
        }
        bytesPerSecondByTarget.computeIfAbsent(target, key -> new LongLongHashMap())
                .addToValue(record.timestampFromStart().toSeconds(), bytes);
    }

    @Override
    public List<IoEndpointTimeline> build() {
        List<IoEndpoint> ranked = grouping.rankedByBytes();
        List<IoEndpointTimeline> result = new ArrayList<>(Math.min(ranked.size(), maxEndpoints));
        for (IoEndpoint endpoint : ranked.subList(0, Math.min(ranked.size(), maxEndpoints))) {
            result.add(new IoEndpointTimeline(endpoint, throughputOf(endpoint.target())));
        }
        return result;
    }

    private SingleSerie throughputOf(String target) {
        LongLongHashMap timeseries = TimeseriesUtils.initWithZeros(timeRange);
        LongLongHashMap recorded = bytesPerSecondByTarget.get(target);
        if (recorded != null) {
            recorded.forEachKeyValue(timeseries::addToValue);
        }
        return TimeseriesUtils.buildSerie(THROUGHPUT_SERIES_NAME, timeseries);
    }
}
