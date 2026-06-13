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

    private final LongLongHashMap machineTimeseries;
    private final LongLongHashMap jvmUserTimeseries;
    private final LongLongHashMap jvmSystemTimeseries;

    public CpuLoadTimeseriesBuilder(RelativeTimeRange timeRange) {
        this.machineTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.jvmUserTimeseries = TimeseriesUtils.initWithZeros(timeRange);
        this.jvmSystemTimeseries = TimeseriesUtils.initWithZeros(timeRange);
    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        long seconds = record.timestampFromStart().toSeconds();

        update(machineTimeseries, seconds, Json.readDouble(fields, MACHINE_TOTAL_FIELD));
        update(jvmUserTimeseries, seconds, Json.readDouble(fields, JVM_USER_FIELD));
        update(jvmSystemTimeseries, seconds, Json.readDouble(fields, JVM_SYSTEM_FIELD));
    }

    private static void update(LongLongHashMap timeseries, long seconds, double fraction) {
        if (fraction < 0) {
            return;
        }
        long basisPoints = Math.round(fraction * BASIS_POINTS_FACTOR);
        timeseries.updateValue(seconds, 0, existing -> Math.max(existing, basisPoints));
    }

    @Override
    public TimeseriesData build() {
        SingleSerie machineSerie = TimeseriesUtils.buildSerie(MACHINE_SERIES_NAME, machineTimeseries);
        SingleSerie userSerie = TimeseriesUtils.buildSerie(JVM_USER_SERIES_NAME, jvmUserTimeseries);
        SingleSerie systemSerie = TimeseriesUtils.buildSerie(JVM_SYSTEM_SERIES_NAME, jvmSystemTimeseries);
        return new TimeseriesData(machineSerie, userSerie, systemSerie);
    }
}
