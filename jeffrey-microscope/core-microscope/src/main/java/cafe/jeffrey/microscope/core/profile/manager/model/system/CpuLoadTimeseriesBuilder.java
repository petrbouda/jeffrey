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
import cafe.jeffrey.microscope.core.profile.model.time.RelativeTimeRange;
import cafe.jeffrey.microscope.core.profile.timeseries.TimeseriesData;

/**
 * Builds the machine-vs-JVM CPU timeline from periodic {@code jdk.CPULoad} events. The event's
 * fraction values (0..1) are stored as basis points (percent × 100) per second, keeping the maximum
 * sample per second. The gap between Machine Total and the two JVM series is CPU consumed by other
 * processes on the host — the noisy-neighbor signal.
 */
public class CpuLoadTimeseriesBuilder implements RecordBuilder<GenericRecord, TimeseriesData> {

    private static final String MACHINE_SERIES_NAME = "Machine Total";
    private static final String JVM_USER_SERIES_NAME = "JVM User";
    private static final String JVM_SYSTEM_SERIES_NAME = "JVM System";
    private static final String JVM_USER_FIELD = "jvmUser";
    private static final String JVM_SYSTEM_FIELD = "jvmSystem";
    private static final String MACHINE_TOTAL_FIELD = "machineTotal";
    private static final double BASIS_POINTS_FACTOR = 10_000d;

    private final MaxPerSecondSeries machineTimeseries;
    private final MaxPerSecondSeries jvmUserTimeseries;
    private final MaxPerSecondSeries jvmSystemTimeseries;

    public CpuLoadTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.machineTimeseries = new MaxPerSecondSeries(MACHINE_SERIES_NAME, timeRange);
        this.jvmUserTimeseries = new MaxPerSecondSeries(JVM_USER_SERIES_NAME, timeRange);
        this.jvmSystemTimeseries = new MaxPerSecondSeries(JVM_SYSTEM_SERIES_NAME, timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long seconds = record.timestampFromStart().toSeconds();

        update(machineTimeseries, seconds, Json.readDouble(fields, MACHINE_TOTAL_FIELD));
        update(jvmUserTimeseries, seconds, Json.readDouble(fields, JVM_USER_FIELD));
        update(jvmSystemTimeseries, seconds, Json.readDouble(fields, JVM_SYSTEM_FIELD));
    }

    private static void update(MaxPerSecondSeries timeseries, long seconds, double fraction) {
        if (fraction < 0) {
            return;
        }
        long basisPoints = Math.round(fraction * BASIS_POINTS_FACTOR);
        timeseries.record(seconds, basisPoints);
    }

    @Override
    public TimeseriesData build() {
        return new TimeseriesData(machineTimeseries.build(), jvmUserTimeseries.build(), jvmSystemTimeseries.build());
    }
}
