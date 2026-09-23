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

package cafe.jeffrey.profile.manager.model.io;

import java.util.List;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

/**
 * Builds the per-second socket/file I/O timeline, either across every endpoint or scoped to a
 * single one via an {@link IoTargetFilter}.
 * <p>
 * Four series are emitted, in this order: bytes read, bytes written, read operations, write
 * operations. The byte series answer "how much moved", the operation series answer "how often we
 * called" — a high call rate moving few bytes is the signature of chatty, undersized reads, which
 * the byte series alone cannot show.
 * <p>
 * Note the two families deliberately disagree about zero-byte events: a read returning no bytes
 * (EOF, an empty write) contributes nothing to throughput but is still an invocation, so it is
 * counted while being left out of the byte sums.
 */
public class IoTimelineTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String READ_BYTES_SERIES_NAME = "Bytes Read / sec";
    private static final String WRITE_BYTES_SERIES_NAME = "Bytes Written / sec";
    private static final String READ_COUNT_SERIES_NAME = "Reads / sec";
    private static final String WRITE_COUNT_SERIES_NAME = "Writes / sec";

    private static final long SINGLE_OPERATION = 1;

    private final IoTargetFilter targetFilter;
    private final LongLongHashMap readBytesTimeseries;
    private final LongLongHashMap writeBytesTimeseries;
    private final LongLongHashMap readCountTimeseries;
    private final LongLongHashMap writeCountTimeseries;

    public IoTimelineTimeseriesBuilder(RelativeTimeRange timeRange, IoTargetFilter targetFilter) {
        this.targetFilter = targetFilter;
        this.readBytesTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.writeBytesTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.readCountTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.writeCountTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        Type type = record.type();
        if (!targetFilter.matches(type, record.jsonFields())) {
            return;
        }
        long seconds = record.timestampFromStart().toSeconds();
        long bytes = IoEventFields.bytes(type, record.jsonFields());

        if (IoEventFields.isRead(type)) {
            readCountTimeseries.addToValue(seconds, SINGLE_OPERATION);
            if (bytes > 0) {
                readBytesTimeseries.addToValue(seconds, bytes);
            }
        } else {
            writeCountTimeseries.addToValue(seconds, SINGLE_OPERATION);
            if (bytes > 0) {
                writeBytesTimeseries.addToValue(seconds, bytes);
            }
        }
    }

    @Override
    public TimeseriesData build() {
        SingleSerie readBytes = TimeseriesUtils.buildSerie(READ_BYTES_SERIES_NAME, readBytesTimeseries);
        SingleSerie writeBytes = TimeseriesUtils.buildSerie(WRITE_BYTES_SERIES_NAME, writeBytesTimeseries);
        SingleSerie readCount = TimeseriesUtils.buildSerie(READ_COUNT_SERIES_NAME, readCountTimeseries);
        SingleSerie writeCount = TimeseriesUtils.buildSerie(WRITE_COUNT_SERIES_NAME, writeCountTimeseries);
        return new TimeseriesData(List.of(readBytes, writeBytes, readCount, writeCount));
    }
}
