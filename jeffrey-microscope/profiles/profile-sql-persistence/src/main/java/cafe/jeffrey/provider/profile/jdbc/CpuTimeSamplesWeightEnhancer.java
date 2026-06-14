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

package cafe.jeffrey.provider.profile.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.DurationUtils;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.settings.ActiveSetting;
import cafe.jeffrey.shared.common.settings.ActiveSettings;
import cafe.jeffrey.provider.profile.api.EventTypeBuilder;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Enriches the JDK 25 CPU-time profiler event ({@code jdk.CPUTimeSample}). The event is always
 * JDK-sourced, so the source is fixed; in addition, each sample is weighted by the sampling
 * period so the flamegraph can render approximate on-CPU time.
 * <p>
 * The CPU-time sampler is configured via a {@code throttle} rate (JEP 509, e.g. {@code "500/s"})
 * rather than a fixed {@code period}; both are attempted, newest convention first. When neither is
 * available the enhancer still fixes the source and leaves the weight unset — sample counts remain
 * a valid (unscaled) flamegraph dimension, mirroring {@link ExecutionSamplesWeightEnhancer}'s
 * graceful degrade.
 */
public class CpuTimeSamplesWeightEnhancer implements EventTypeEnhancer {

    private static final Logger LOG = LoggerFactory.getLogger(CpuTimeSamplesWeightEnhancer.class);

    private static final String THROTTLE_PARAM = "throttle";
    private static final String PERIOD_PARAM = "period";
    private static final String SAMPLE_INTERVAL_EXTRA = "sample_interval";
    private static final String RATE_PER_SECOND_SUFFIX = "/s";
    private static final String THROTTLE_OFF = "off";
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final ActiveSettings settings;

    public CpuTimeSamplesWeightEnhancer(ActiveSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean isApplicable(Type eventType) {
        return Type.CPU_TIME_SAMPLE.sameAs(eventType);
    }

    @Override
    public EventTypeBuilder apply(EventTypeBuilder builder) {
        // CPU-time sampling is a JDK-only event source.
        builder.withSource(RecordingEventSource.JDK);

        Optional<ActiveSetting> cpuSettingsOpt = settings.findFirstByType(Type.CPU_TIME_SAMPLE);
        if (cpuSettingsOpt.isEmpty()) {
            LOG.warn("The ActiveSettings is not available for CPU Time Samples");
            return builder;
        }
        ActiveSetting cpuSettings = cpuSettingsOpt.get();

        Optional<Long> periodInNanos = resolveThrottleInterval(cpuSettings)
                .or(() -> cpuSettings.getParam(PERIOD_PARAM).map(DurationUtils::parse).map(Duration::toNanos));

        if (periodInNanos.isEmpty()) {
            LOG.warn("Neither `throttle` nor `period` is set for the CPU Time Samples");
            return builder;
        }

        return builder.putExtras(Map.of(SAMPLE_INTERVAL_EXTRA, String.valueOf(periodInNanos.get())));
    }

    /**
     * Converts a {@code throttle} rate to a per-sample interval in nanoseconds. Accepts the
     * {@code "<rate>/s"} form and a bare numeric rate; {@code "off"} (or a non-positive rate)
     * yields no interval.
     */
    private static Optional<Long> resolveThrottleInterval(ActiveSetting cpuSettings) {
        return cpuSettings.getParam(THROTTLE_PARAM)
                .map(String::trim)
                .filter(value -> !value.equalsIgnoreCase(THROTTLE_OFF))
                .map(value -> value.endsWith(RATE_PER_SECOND_SUFFIX)
                        ? value.substring(0, value.length() - RATE_PER_SECOND_SUFFIX.length()).trim()
                        : value)
                .flatMap(CpuTimeSamplesWeightEnhancer::parsePositiveLong)
                .map(rate -> NANOS_PER_SECOND / rate);
    }

    private static Optional<Long> parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
