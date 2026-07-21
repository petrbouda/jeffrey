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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.common.event.ContainerConfiguration;
import cafe.jeffrey.profile.common.event.ContainerCpuThrottling;
import cafe.jeffrey.profile.manager.model.container.ContainerCpuThrottlingData;
import cafe.jeffrey.profile.manager.model.container.ContainerCpuThrottlingData.Severity;
import cafe.jeffrey.profile.manager.model.container.ThrottlingSample;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerCpuThrottlingAnalyzerTest {

    private static final long ONE_CORE_QUOTA_NANOS = 100_000_000L;
    private static final long CFS_PERIOD_NANOS = 100_000_000L;

    private static ContainerConfiguration config(Long cpuQuota, Long cpuSlicePeriod) {
        return new ContainerConfiguration(
                "cgroupv2", cpuSlicePeriod, cpuQuota, -1L, 8L, 0L, -1L, -1L, 33_390_182_400L, -1L);
    }

    private static ThrottlingSample sample(long tMillis, long elapsed, long throttled, Long ttNanos) {
        return new ThrottlingSample(tMillis, new ContainerCpuThrottling(elapsed, throttled, ttNanos));
    }

    @Nested
    class DeltaMath {

        @Test
        void subtractsPreRecordingBaselineAndSumsDeltas() {
            // First sample carries large cumulative counters accrued BEFORE the recording (baseline).
            List<ThrottlingSample> samples = List.of(
                    sample(0, 100, 50, 10_000_000_000L),      // baseline — must be excluded
                    sample(30_000, 300, 60, 10_500_000_000L), // Δ elapsed=200 throttled=10 time=500ms
                    sample(60_000, 600, 60, 10_500_000_000L)  // Δ elapsed=300 throttled=0  time=0
            );

            ContainerCpuThrottlingData result =
                    ContainerCpuThrottlingAnalyzer.analyze(samples, config(ONE_CORE_QUOTA_NANOS, CFS_PERIOD_NANOS));

            assertEquals(500, result.summary().elapsedPeriods());
            assertEquals(10, result.summary().throttledPeriods());
            assertEquals(500.0, result.summary().throttledTimeMillis(), 0.001);
            assertEquals(2.0, result.summary().overallRatioPct(), 0.001);   // 10 / 500
            assertEquals(5.0, result.summary().peakRatioPct(), 0.001);      // 10 / 200 in the first window
            assertEquals(1.0, result.summary().cpuLimitCores(), 0.001);
            assertEquals(100.0, result.summary().cfsPeriodMillis(), 0.001);
            assertEquals(8L, result.summary().effectiveCpuCount());
        }

        @Test
        void reportsOneWindowPerThrottledInterval() {
            List<ThrottlingSample> samples = List.of(
                    sample(0, 100, 50, 0L),
                    sample(30_000, 300, 55, 200_000_000L),  // throttled window
                    sample(60_000, 600, 55, 200_000_000L),  // quiet
                    sample(90_000, 900, 58, 350_000_000L)   // throttled window
            );

            ContainerCpuThrottlingData result =
                    ContainerCpuThrottlingAnalyzer.analyze(samples, config(ONE_CORE_QUOTA_NANOS, CFS_PERIOD_NANOS));

            assertEquals(2, result.windows().size());
            // sorted by ratio desc — both windows present, timeseries has one point per interval
            assertEquals(3, result.timeseries().size());
        }

        @Test
        void lowRatioIsLowSeverityAndThrottled() {
            // ~0.3% overall (mirrors the real profile: 11 of ~4131 periods).
            List<ThrottlingSample> samples = List.of(
                    sample(0, 0, 0, 0L),
                    sample(30_000, 4131, 11, 740_000_000L)
            );

            ContainerCpuThrottlingData result =
                    ContainerCpuThrottlingAnalyzer.analyze(samples, config(ONE_CORE_QUOTA_NANOS, CFS_PERIOD_NANOS));

            assertTrue(result.verdict().throttled());
            assertEquals(Severity.LOW, result.verdict().severity());
            assertEquals(11, result.summary().throttledPeriods());
            assertEquals(740.0, result.summary().throttledTimeMillis(), 0.001);
        }

        @Test
        void highRatioIsHighSeverity() {
            List<ThrottlingSample> samples = List.of(
                    sample(0, 0, 0, 0L),
                    sample(30_000, 100, 40, 400_000_000L) // 40%
            );

            ContainerCpuThrottlingData result =
                    ContainerCpuThrottlingAnalyzer.analyze(samples, config(ONE_CORE_QUOTA_NANOS, CFS_PERIOD_NANOS));

            assertEquals(Severity.HIGH, result.verdict().severity());
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void noQuotaIsNotApplicable() {
            List<ThrottlingSample> samples = List.of(
                    sample(0, 0, 0, null),
                    sample(30_000, 0, 0, null)
            );

            ContainerCpuThrottlingData result =
                    ContainerCpuThrottlingAnalyzer.analyze(samples, config(null, CFS_PERIOD_NANOS));

            assertEquals(Severity.NOT_APPLICABLE, result.verdict().severity());
            assertFalse(result.verdict().throttled());
            assertTrue(result.timeseries().isEmpty());
            assertTrue(result.windows().isEmpty());
        }

        @Test
        void zeroThrottlingIsNoneSeverity() {
            List<ThrottlingSample> samples = List.of(
                    sample(0, 100, 0, 0L),
                    sample(30_000, 400, 0, 0L)
            );

            ContainerCpuThrottlingData result =
                    ContainerCpuThrottlingAnalyzer.analyze(samples, config(ONE_CORE_QUOTA_NANOS, CFS_PERIOD_NANOS));

            assertEquals(Severity.NONE, result.verdict().severity());
            assertFalse(result.verdict().throttled());
        }

        @Test
        void nullCountersAreTreatedAsZero() {
            List<ThrottlingSample> samples = List.of(
                    sample(0, 0, 0, null),
                    sample(30_000, 300, 0, null)
            );

            ContainerCpuThrottlingData result =
                    ContainerCpuThrottlingAnalyzer.analyze(samples, config(ONE_CORE_QUOTA_NANOS, CFS_PERIOD_NANOS));

            assertEquals(0.0, result.summary().throttledTimeMillis(), 0.001);
            assertEquals(Severity.NONE, result.verdict().severity());
        }
    }
}
