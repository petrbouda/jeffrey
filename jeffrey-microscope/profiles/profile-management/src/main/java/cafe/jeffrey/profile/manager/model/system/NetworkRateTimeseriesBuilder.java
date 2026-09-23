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

package cafe.jeffrey.profile.manager.model.system;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
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
