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

package cafe.jeffrey.profile.manager.model.container;

import java.util.List;

/**
 * Result of the CPU throttling detector for a profile. Assembled by
 * {@link cafe.jeffrey.profile.manager.ContainerCpuThrottlingAnalyzer} from the delta of consecutive
 * cumulative {@code jdk.ContainerCPUThrottling} samples, cross-referenced with the container's CFS
 * configuration.
 *
 * <p>All throttled-time values are exposed in <b>milliseconds</b> and ratios as <b>percentages</b> so
 * the frontend can format them without unit conversion.</p>
 */
public record ContainerCpuThrottlingData(
        Verdict verdict,
        Summary summary,
        List<ThrottlingPoint> timeseries,
        List<ThrottledWindow> windows) {

    public enum Severity {
        NONE, LOW, MEDIUM, HIGH, NOT_APPLICABLE
    }

    /**
     * @param throttled   whether any throttling was observed inside the recording window
     * @param severity    bucketed severity of the overall throttle ratio
     * @param title       short human verdict, e.g. "Throttling detected — early burst, then steady"
     * @param description one-paragraph explanation for the banner
     */
    public record Verdict(boolean throttled, Severity severity, String title, String description) {
    }

    /**
     * @param elapsedPeriods    CFS periods elapsed within the window (last − first counter)
     * @param throttledPeriods  periods throttled within the window
     * @param throttledTimeMillis total time parked, milliseconds
     * @param overallRatioPct   throttledPeriods / elapsedPeriods, percent
     * @param peakRatioPct      highest single-window ratio, percent
     * @param cpuLimitCores     quota / period, cores (null when no CFS quota)
     * @param cfsPeriodMillis   scheduling period length, milliseconds (null when unknown)
     * @param effectiveCpuCount effective processors the JVM sees (null when unknown)
     */
    public record Summary(
            long elapsedPeriods,
            long throttledPeriods,
            double throttledTimeMillis,
            double overallRatioPct,
            double peakRatioPct,
            Double cpuLimitCores,
            Double cfsPeriodMillis,
            Long effectiveCpuCount) {
    }

    /**
     * A single 30 s interval (the {@code jdk.ContainerCPUThrottling} cadence), expressed as the delta
     * between two consecutive cumulative samples.
     */
    public record ThrottlingPoint(
            long timestampMillis,
            long elapsedPeriodsDelta,
            long throttledPeriodsDelta,
            double throttledTimeMillisDelta,
            double ratioPct) {
    }

    /**
     * A window in which throttling actually occurred (delta throttled periods {@code > 0}). Feeds the
     * "most-throttled windows" drill-down table; {@code startMillis}/{@code endMillis} scope the
     * sub-second heatmap and flamegraph for that window.
     */
    public record ThrottledWindow(
            long startMillis,
            long endMillis,
            long throttledPeriods,
            double throttledTimeMillis,
            double ratioPct) {
    }
}
