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

package pbouda.jeffrey.manager.model.gc;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;
import pbouda.jeffrey.timeseries.SingleSerie;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

public class GCTimeseriesBuilder implements RecordBuilder<GenericRecord, SingleSerie> {

    private final GCTimeseriesType timeseriesType;
    private final LongLongHashMap timeseries;

    public GCTimeseriesBuilder(RelativeTimeRange timeRange, GCTimeseriesType timeseriesType) {
        this.timeseriesType = timeseriesType;
        this.timeseries = TimeseriesUtils.structure(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        long seconds = record.timestampFromStart().toSeconds();

        switch  (timeseriesType) {
            case COUNT -> {
                timeseries.addToValue(seconds, 1);
            }
            case MAX_PAUSE -> {
                long pause = Json.readLong(record.jsonFields(), "sumOfPauses");
                timeseries.updateValue(seconds, -1, first -> Math.max(first, pause));
            }
            case SUM_OF_PAUSES -> {
                long pause = Json.readLong(record.jsonFields(), "sumOfPauses");
                timeseries.addToValue(seconds, pause);
            }
        }
    }

    @Override
    public SingleSerie build() {
        return TimeseriesUtils.buildSerie(timeseriesType.getDescription(), timeseries);
    }
}
