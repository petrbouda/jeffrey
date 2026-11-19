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

package cafe.jeffrey.microscope.core.profile.manager.model.jit;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.microscope.core.profile.timeseries.MaxPerSecondSeries;
import cafe.jeffrey.microscope.core.profile.persistence.api.GenericRecord;
import cafe.jeffrey.microscope.core.profile.persistence.api.RecordBuilder;
import cafe.jeffrey.microscope.common.Json;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.microscope.core.profile.timeseries.SingleSerie;
import cafe.jeffrey.microscope.core.profile.timeseries.TimeseriesData;

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

    private final MaxPerSecondSeries c1Timeseries;
    private final MaxPerSecondSeries c2Timeseries;

    public CompilerQueueTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.c1Timeseries = new MaxPerSecondSeries(C1_SERIES_NAME, timeRange);
        this.c2Timeseries = new MaxPerSecondSeries(C2_SERIES_NAME, timeRange);
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

        MaxPerSecondSeries target = C1_COMPILER.equalsIgnoreCase(compiler) ? c1Timeseries : c2Timeseries;
        target.record(seconds, queueSize);
    }

    @Override
    public TimeseriesData build() {
        SingleSerie c1Serie = c1Timeseries.build();
        SingleSerie c2Serie = c2Timeseries.build();
        return new TimeseriesData(c1Serie, c2Serie);
    }
}
