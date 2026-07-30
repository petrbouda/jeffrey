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
 * Builds a per-second series per endpoint in a single pass, returning the top endpoints with their
 * shape. Feeds the sparkline gallery, where every peer's shape has to be visible before the user
 * picks one — one query instead of one request per tile.
 * <p>
 * The {@link IoMetric} decides both what the series holds and how the endpoints are ranked, and the
 * ranking runs before the top-N cut. That is the whole point of passing the metric down here rather
 * than letting the frontend re-sort: a chatty endpoint moving a few kilobytes is not merely further
 * down the bytes-ranked list, it is absent from it, and no client-side sort can recover a tile the
 * server never sent.
 * <p>
 * Per-endpoint buckets are collected sparsely while streaming and only zero-filled across the whole
 * recording for the endpoints that actually make the cut, so a recording with thousands of peers
 * does not allocate a full-length series for each of them.
 */
public class IoEndpointTimelinesBuilder implements RecordBuilder<GenericRecord, List<IoEndpointTimeline>> {

    private static final String BYTES_SERIES_NAME = "Bytes / sec";
    private static final String COUNT_SERIES_NAME = "Ops / sec";

    private static final long SINGLE_OPERATION = 1;

    private final RelativeTimeRange timeRange;
    private final int maxEndpoints;
    private final IoMetric metric;
    private final IoEndpointGrouping grouping = new IoEndpointGrouping();
    private final Map<String, LongLongHashMap> valuesPerSecondByTarget = new HashMap<>();

    public IoEndpointTimelinesBuilder(RelativeTimeRange timeRange, int maxEndpoints, IoMetric metric) {
        this.timeRange = timeRange;
        this.maxEndpoints = maxEndpoints;
        this.metric = metric;
    }

    @Override
    public void onRecord(GenericRecord record) {
        String target = IoEventFields.target(record.type(), record.jsonFields());
        grouping.record(target, record);

        // A zero-byte read moves nothing but is still a call, so it belongs in the count series and
        // not in the bytes one — the same asymmetry IoTimelineTimeseriesBuilder documents.
        long value;
        if (metric == IoMetric.COUNT) {
            value = SINGLE_OPERATION;
        } else {
            value = IoEventFields.bytes(record.type(), record.jsonFields());
            if (value <= 0) {
                return;
            }
        }
        valuesPerSecondByTarget.computeIfAbsent(target, key -> new LongLongHashMap())
                .addToValue(record.timestampFromStart().toSeconds(), value);
    }

    @Override
    public List<IoEndpointTimeline> build() {
        List<IoEndpoint> ranked = grouping.rankedBy(metric);
        List<IoEndpointTimeline> result = new ArrayList<>(Math.min(ranked.size(), maxEndpoints));
        for (IoEndpoint endpoint : ranked.subList(0, Math.min(ranked.size(), maxEndpoints))) {
            result.add(new IoEndpointTimeline(endpoint, serieOf(endpoint.target())));
        }
        return result;
    }

    private SingleSerie serieOf(String target) {
        LongLongHashMap timeseries = TimeseriesUtils.initWithZeros(timeRange);
        LongLongHashMap recorded = valuesPerSecondByTarget.get(target);
        if (recorded != null) {
            recorded.forEachKeyValue(timeseries::addToValue);
        }
        String name = metric == IoMetric.COUNT ? COUNT_SERIES_NAME : BYTES_SERIES_NAME;
        return TimeseriesUtils.buildSerie(name, timeseries);
    }
}
