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

package cafe.jeffrey.profile.manager.model.jit;

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
 * Builds the compiler-queue backlog timeline from {@code jdk.CompilerQueueUtilization} events, one
 * series per compiler (C1, C2). A sustained C2 backlog during warmup means compilation pressure —
 * methods waiting for optimization while running interpreted/C1 code.
 */
public class CompilerQueueTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String C1_SERIES_NAME = "C1 Queue";
    private static final String C2_SERIES_NAME = "C2 Queue";
    private static final String COMPILER_FIELD = "compiler";
    private static final String QUEUE_SIZE_FIELD = "queueSize";
    private static final String C1_COMPILER = "c1";

    private final LongLongHashMap c1Timeseries;
    private final LongLongHashMap c2Timeseries;

    public CompilerQueueTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.c1Timeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.c2Timeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long queueSize = Json.readLong(fields, QUEUE_SIZE_FIELD);
        if (queueSize < 0) {
            return;
        }
        long seconds = record.timestampFromStart().toSeconds();
        String compiler = Json.readString(fields, COMPILER_FIELD);

        LongLongHashMap target = C1_COMPILER.equalsIgnoreCase(compiler) ? c1Timeseries : c2Timeseries;
        target.updateValue(seconds, 0, existing -> Math.max(existing, queueSize));
    }

    @Override
    public TimeseriesData build() {
        SingleSerie c1Serie = TimeseriesUtils.buildSerie(C1_SERIES_NAME, c1Timeseries);
        SingleSerie c2Serie = TimeseriesUtils.buildSerie(C2_SERIES_NAME, c2Timeseries);
        return new TimeseriesData(c1Serie, c2Serie);
    }
}
