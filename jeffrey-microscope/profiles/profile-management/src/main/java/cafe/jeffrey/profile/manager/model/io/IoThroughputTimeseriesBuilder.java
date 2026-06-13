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
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

/**
 * Builds bytes-read-per-second and bytes-written-per-second series from the socket/file I/O events.
 */
public class IoThroughputTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String READ_SERIES_NAME = "Bytes Read / sec";
    private static final String WRITE_SERIES_NAME = "Bytes Written / sec";

    private final LongLongHashMap readTimeseries;
    private final LongLongHashMap writeTimeseries;

    public IoThroughputTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.readTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.writeTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        Type type = record.type();
        long bytes = IoEventFields.bytes(type, record.jsonFields());
        if (bytes <= 0) {
            return;
        }
        long seconds = record.timestampFromStart().toSeconds();
        if (IoEventFields.isRead(type)) {
            readTimeseries.addToValue(seconds, bytes);
        } else {
            writeTimeseries.addToValue(seconds, bytes);
        }
    }

    @Override
    public TimeseriesData build() {
        SingleSerie readSerie = TimeseriesUtils.buildSerie(READ_SERIES_NAME, readTimeseries);
        SingleSerie writeSerie = TimeseriesUtils.buildSerie(WRITE_SERIES_NAME, writeTimeseries);
        return new TimeseriesData(readSerie, writeSerie);
    }
}
