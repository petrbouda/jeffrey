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
 */

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.common.event.ContainerConfiguration;
import cafe.jeffrey.profile.common.event.ContainerCpuThrottling;
import cafe.jeffrey.profile.manager.model.container.ContainerCpuThrottlingData;
import cafe.jeffrey.profile.manager.model.container.ContainerCpuThrottlingData.Severity;
import cafe.jeffrey.profile.manager.model.container.ContainerCpuThrottlingData.Summary;
import cafe.jeffrey.profile.manager.model.container.ContainerCpuThrottlingData.ThrottledWindow;
import cafe.jeffrey.profile.manager.model.container.ContainerCpuThrottlingData.ThrottlingPoint;
import cafe.jeffrey.profile.manager.model.container.ContainerCpuThrottlingData.Verdict;
import cafe.jeffrey.profile.manager.model.container.ThrottlingSample;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns the raw, cumulative {@code jdk.ContainerCPUThrottling} samples into a detector result.
 *
 * <p>The kernel counters are cumulative since cgroup creation, so the first in-window sample already
 * carries throttling that happened <b>before</b> the recording started. This analyzer therefore treats
 * the first sample as a baseline and sums per-consecutive-sample deltas — the totals are
 * {@code last − first}, never the raw absolutes.</p>
 */
public final class ContainerCpuThrottlingAnalyzer {

    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final double PERCENT = 100.0;

    // Severity buckets on the overall throttle ratio (throttled ÷ elapsed periods), percent.
    private static final double LOW_MAX_RATIO_PCT = 5.0;
    private static final double MEDIUM_MAX_RATIO_PCT = 25.0;

    private static final String TITLE_NOT_APPLICABLE = "Not applicable — no CPU limit set";
    private static final String DESC_NOT_APPLICABLE =
            "This container runs with no CFS quota, so the kernel never throttles it — it can use every "
                    + "host CPU it can schedule. Set a CPU limit to make throttling meaningful.";
    private static final String TITLE_NONE = "No throttling detected";
    private static final String DESC_NONE =
            "The container stayed within its CPU quota for the whole recording — no CFS period was throttled.";
    private static final String TITLE_THROTTLED = "Throttling detected";
    private static final String DESC_THROTTLED_PREFIX =
            "The container was parked by the CFS scheduler after exhausting its CPU quota. ";

    private ContainerCpuThrottlingAnalyzer() {
    }

    public static ContainerCpuThrottlingData analyze(List<ThrottlingSample> samples, ContainerConfiguration config) {
        Double cpuLimitCores = cpuLimitCores(config);
        Double cfsPeriodMillis = cfsPeriodMillis(config);
        Long effectiveCpuCount = config != null ? config.effectiveCpuCount() : null;

        // No CFS quota → the container cannot be throttled, regardless of what counters say.
        if (cpuLimitCores == null) {
            Verdict verdict = new Verdict(false, Severity.NOT_APPLICABLE, TITLE_NOT_APPLICABLE, DESC_NOT_APPLICABLE);
            Summary summary = new Summary(0, 0, 0, 0, 0, null, cfsPeriodMillis, effectiveCpuCount);
            return new ContainerCpuThrottlingData(verdict, summary, List.of(), List.of());
        }

        List<ThrottlingPoint> timeseries = new ArrayList<>();
        List<ThrottledWindow> windows = new ArrayList<>();
        long totalElapsed = 0;
        long totalThrottled = 0;
        long totalThrottledNanos = 0;
        double peakRatioPct = 0.0;

        for (int i = 1; i < samples.size(); i++) {
            ThrottlingSample prev = samples.get(i - 1);
            ThrottlingSample cur = samples.get(i);

            long deltaElapsed = Math.max(0, elapsed(cur) - elapsed(prev));
            long deltaThrottled = Math.max(0, throttled(cur) - throttled(prev));
            long deltaNanos = Math.max(0, throttledNanos(cur) - throttledNanos(prev));
            double ratioPct = deltaElapsed > 0 ? (double) deltaThrottled / deltaElapsed * PERCENT : 0.0;
            double deltaMillis = deltaNanos / NANOS_PER_MILLI;

            timeseries.add(new ThrottlingPoint(
                    cur.timestampMillis(), deltaElapsed, deltaThrottled, deltaMillis, ratioPct));

            if (deltaThrottled > 0) {
                windows.add(new ThrottledWindow(
                        prev.timestampMillis(), cur.timestampMillis(), deltaThrottled, deltaMillis, ratioPct));
            }

            totalElapsed += deltaElapsed;
            totalThrottled += deltaThrottled;
            totalThrottledNanos += deltaNanos;
            peakRatioPct = Math.max(peakRatioPct, ratioPct);
        }

        double overallRatioPct = totalElapsed > 0 ? (double) totalThrottled / totalElapsed * PERCENT : 0.0;
        double throttledTimeMillis = totalThrottledNanos / NANOS_PER_MILLI;

        windows.sort(Comparator
                .comparingDouble(ThrottledWindow::ratioPct)
                .thenComparingDouble(ThrottledWindow::throttledTimeMillis)
                .reversed());

        Severity severity = severityOf(totalThrottled, overallRatioPct);
        Verdict verdict = verdictOf(severity, overallRatioPct, throttledTimeMillis, totalThrottled, totalElapsed);
        Summary summary = new Summary(totalElapsed, totalThrottled, throttledTimeMillis,
                overallRatioPct, peakRatioPct, cpuLimitCores, cfsPeriodMillis, effectiveCpuCount);

        return new ContainerCpuThrottlingData(verdict, summary, timeseries, windows);
    }

    private static Severity severityOf(long totalThrottled, double overallRatioPct) {
        if (totalThrottled == 0) {
            return Severity.NONE;
        }
        if (overallRatioPct < LOW_MAX_RATIO_PCT) {
            return Severity.LOW;
        }
        if (overallRatioPct < MEDIUM_MAX_RATIO_PCT) {
            return Severity.MEDIUM;
        }
        return Severity.HIGH;
    }

    private static Verdict verdictOf(
            Severity severity, double overallRatioPct, double throttledTimeMillis,
            long totalThrottled, long totalElapsed) {
        if (severity == Severity.NONE) {
            return new Verdict(false, Severity.NONE, TITLE_NONE, DESC_NONE);
        }
        String description = DESC_THROTTLED_PREFIX + String.format(
                "%d of %d CFS periods were throttled (%.1f%% overall) for %.0f ms of parked time.",
                totalThrottled, totalElapsed, overallRatioPct, throttledTimeMillis);
        return new Verdict(true, severity, TITLE_THROTTLED, description);
    }

    private static Double cpuLimitCores(ContainerConfiguration config) {
        if (config == null || config.cpuQuota() == null || config.cpuSlicePeriod() == null) {
            return null;
        }
        long quota = config.cpuQuota();
        long period = config.cpuSlicePeriod();
        if (quota <= 0 || period <= 0) {
            return null;
        }
        return (double) quota / period;
    }

    private static Double cfsPeriodMillis(ContainerConfiguration config) {
        if (config == null || config.cpuSlicePeriod() == null || config.cpuSlicePeriod() <= 0) {
            return null;
        }
        return config.cpuSlicePeriod() / NANOS_PER_MILLI;
    }

    private static long elapsed(ThrottlingSample s) {
        return value(s.counters().cpuElapsedSlices());
    }

    private static long throttled(ThrottlingSample s) {
        return value(s.counters().cpuThrottledSlices());
    }

    private static long throttledNanos(ThrottlingSample s) {
        return value(s.counters().cpuThrottledTime());
    }

    private static long value(Long v) {
        return v == null ? 0L : v;
    }
}
