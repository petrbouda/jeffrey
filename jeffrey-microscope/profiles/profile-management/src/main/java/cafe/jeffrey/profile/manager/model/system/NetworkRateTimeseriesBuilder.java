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

package cafe.jeffrey.profile.manager.model.system;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

/**
 * Builds the per-interface network utilization timeline from {@code jdk.NetworkUtilization} events.
 * The event reports read/write rates in bits per second; values are converted to bytes per second
 * so the UI can use byte formatting. Interface filtering happens in SQL (JSON-field push-down),
 * so every record reaching this builder belongs to the selected interface.
 */
public class NetworkRateTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String READ_SERIES_NAME = "Read";
    private static final String WRITE_SERIES_NAME = "Write";
    private static final String READ_RATE_FIELD = "readRate";
    private static final String WRITE_RATE_FIELD = "writeRate";
    private static final long BITS_PER_BYTE = 8;

    private final LongLongHashMap readTimeseries;
    private final LongLongHashMap writeTimeseries;

    public NetworkRateTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.readTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.writeTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long seconds = record.timestampFromStart().toSeconds();

        long readBitsPerSec = Json.readLong(fields, READ_RATE_FIELD);
        if (readBitsPerSec >= 0) {
            long bytesPerSec = readBitsPerSec / BITS_PER_BYTE;
            readTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, bytesPerSec));
        }
        long writeBitsPerSec = Json.readLong(fields, WRITE_RATE_FIELD);
        if (writeBitsPerSec >= 0) {
            long bytesPerSec = writeBitsPerSec / BITS_PER_BYTE;
            writeTimeseries.updateValue(seconds, 0, existing -> Math.max(existing, bytesPerSec));
        }
    }

    @Override
    public TimeseriesData build() {
        SingleSerie readSerie = TimeseriesUtils.buildSerie(READ_SERIES_NAME, readTimeseries);
        SingleSerie writeSerie = TimeseriesUtils.buildSerie(WRITE_SERIES_NAME, writeTimeseries);
        return new TimeseriesData(readSerie, writeSerie);
    }
}
