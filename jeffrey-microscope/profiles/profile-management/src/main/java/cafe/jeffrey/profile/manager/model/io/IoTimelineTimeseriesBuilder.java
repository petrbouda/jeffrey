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

import java.util.List;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
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
