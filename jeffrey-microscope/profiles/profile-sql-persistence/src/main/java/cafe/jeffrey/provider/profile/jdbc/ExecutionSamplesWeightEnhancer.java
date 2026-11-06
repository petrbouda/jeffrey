/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.model.EventSubtype;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.shared.common.DurationUtils;
import cafe.jeffrey.microscope.model.settings.ActiveSetting;
import cafe.jeffrey.microscope.model.settings.ActiveSettings;
import cafe.jeffrey.provider.profile.api.EventTypeBuilder;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class ExecutionSamplesWeightEnhancer implements EventTypeEnhancer {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionSamplesWeightEnhancer.class);
    private static final Duration ASYNC_PROFILER_DEFAULT_INTERVAL = Duration.ofMillis(10);
    private static final String PERIOD_PARAM = "period";
    private static final String INTERVAL_PARAM = "interval";

    private final ActiveSettings settings;

    public ExecutionSamplesWeightEnhancer(ActiveSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean isApplicable(Type eventType) {
        return Type.EXECUTION_SAMPLE.sameAs(eventType);
    }

    @Override
    public EventTypeBuilder apply(EventTypeBuilder builder) {
        Optional<ActiveSetting> execSettingsOpt = settings.findFirstByType(Type.EXECUTION_SAMPLE);
        if (execSettingsOpt.isEmpty()) {
            LOG.warn("The ActiveSettings is now available for Execution Samples");
            return builder;
        }
        ActiveSetting execSettings = execSettingsOpt.get();

        // The JDK's own method profiling samples keep their period as a duration (`period`); async-profiler's
        // profiling modes keep it as an interval in nanoseconds (`interval`), where zero means its default.
        EventSubtype executionSampleType = settings.executionSampleType().orElse(null);
        Optional<Duration> periodOpt = switch (executionSampleType) {
            case null -> Optional.empty();
            case EXECUTION_SAMPLE -> execSettings.getParam(PERIOD_PARAM).map(DurationUtils::parse);
            case CPU, CTIMER, ITIMER, WALL, METHOD -> execSettings.getParam(INTERVAL_PARAM)
                    .map(Long::parseLong)
                    .map(Duration::ofNanos)
                    .map(interval -> interval.isZero() ? ASYNC_PROFILER_DEFAULT_INTERVAL : interval);
        };

        if (periodOpt.isEmpty()) {
            LOG.warn("The `period` or `interval` is not set for the Execution Samples");
            return builder;
        }

        // Every sample is weighted by its sampling period
        long periodInNanos = periodOpt.get().toNanos();
        return builder.putExtras(Map.of("sample_interval", String.valueOf(periodInNanos)));
    }
}
