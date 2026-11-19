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

package cafe.jeffrey.microscope.core.profile.manager.model.system;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.microscope.core.profile.timeseries.MaxPerSecondSeries;
import cafe.jeffrey.microscope.core.profile.persistence.api.GenericRecord;
import cafe.jeffrey.microscope.core.profile.persistence.api.RecordBuilder;
import cafe.jeffrey.microscope.common.Json;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.microscope.core.profile.timeseries.SingleSerie;
import cafe.jeffrey.microscope.core.profile.timeseries.TimeseriesData;

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

    private final MaxPerSecondSeries readTimeseries;
    private final MaxPerSecondSeries writeTimeseries;

    public NetworkRateTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.readTimeseries = new MaxPerSecondSeries(READ_SERIES_NAME, timeRange);
        this.writeTimeseries = new MaxPerSecondSeries(WRITE_SERIES_NAME, timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long seconds = record.timestampFromStart().toSeconds();

        long readBitsPerSec = Json.readLong(fields, READ_RATE_FIELD);
        if (readBitsPerSec >= 0) {
            long bytesPerSec = readBitsPerSec / BITS_PER_BYTE;
            readTimeseries.record(seconds, bytesPerSec);
        }
        long writeBitsPerSec = Json.readLong(fields, WRITE_RATE_FIELD);
        if (writeBitsPerSec >= 0) {
            long bytesPerSec = writeBitsPerSec / BITS_PER_BYTE;
            writeTimeseries.record(seconds, bytesPerSec);
        }
    }

    @Override
    public TimeseriesData build() {
        SingleSerie readSerie = readTimeseries.build();
        SingleSerie writeSerie = writeTimeseries.build();
        return new TimeseriesData(readSerie, writeSerie);
    }
}
