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

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

/**
 * Aggregates {@code jdk.CPULoad} samples into average/max statistics. The event carries CPU loads
 * as fractions (0..1); results are converted to basis points (percent × 100).
 *
 * @param avgMachineBp average total machine CPU load in basis points
 * @param maxMachineBp maximum total machine CPU load in basis points
 * @param avgJvmBp     average JVM (user + system) CPU load in basis points
 * @param avgOtherBp   average non-JVM CPU load in basis points (machine − JVM, floored at 0)
 */
public class CpuLoadStatsBuilder implements RecordBuilder<GenericRecord, CpuLoadStatsBuilder.CpuLoadStats> {

    public record CpuLoadStats(long avgMachineBp, long maxMachineBp, long avgJvmBp, long avgOtherBp) {
    }

    private static final String JVM_USER_FIELD = "jvmUser";
    private static final String JVM_SYSTEM_FIELD = "jvmSystem";
    private static final String MACHINE_TOTAL_FIELD = "machineTotal";
    private static final double BASIS_POINTS_FACTOR = 10_000d;

    private double machineSum;
    private double machineMax;
    private double jvmSum;
    private long sampleCount;

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        double machineTotal = Json.readDouble(fields, MACHINE_TOTAL_FIELD);
        if (machineTotal < 0) {
            return;
        }
        double jvmUser = Math.max(0, Json.readDouble(fields, JVM_USER_FIELD));
        double jvmSystem = Math.max(0, Json.readDouble(fields, JVM_SYSTEM_FIELD));

        machineSum += machineTotal;
        machineMax = Math.max(machineMax, machineTotal);
        jvmSum += jvmUser + jvmSystem;
        sampleCount++;
    }

    @Override
    public CpuLoadStats build() {
        if (sampleCount == 0) {
            return new CpuLoadStats(0, 0, 0, 0);
        }
        long avgMachineBp = toBasisPoints(machineSum / sampleCount);
        long avgJvmBp = toBasisPoints(jvmSum / sampleCount);
        return new CpuLoadStats(
                avgMachineBp,
                toBasisPoints(machineMax),
                avgJvmBp,
                Math.max(0, avgMachineBp - avgJvmBp));
    }

    private static long toBasisPoints(double fraction) {
        return Math.round(fraction * BASIS_POINTS_FACTOR);
    }
}
