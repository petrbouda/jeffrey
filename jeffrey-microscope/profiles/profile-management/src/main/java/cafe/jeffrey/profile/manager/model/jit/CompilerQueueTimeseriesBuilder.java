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

package cafe.jeffrey.profile.manager.model.jit;

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
