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
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.settings.ActiveSetting;
import cafe.jeffrey.microscope.model.settings.ActiveSettings;
import cafe.jeffrey.provider.profile.api.EventTypeBuilder;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class WallClockSamplesWeightEnhancer implements EventTypeEnhancer {

    private static final Logger LOG = LoggerFactory.getLogger(WallClockSamplesWeightEnhancer.class);

    private static final Duration ASYNC_PROFILER_DEFAULT_INTERVAL = Duration.ofMillis(10);

    private final ActiveSettings settings;

    public WallClockSamplesWeightEnhancer(ActiveSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean isApplicable(Type eventType) {
        return Type.WALL_CLOCK_SAMPLE.sameAs(eventType);
    }

    @Override
    public EventTypeBuilder apply(EventTypeBuilder event) {
        Optional<ActiveSetting> execSettingsOpt = settings.findFirstByType(Type.EXECUTION_SAMPLE);
        if (execSettingsOpt.isEmpty()) {
            LOG.warn("The ActiveSettings is now available for Execution Samples");
            return event;
        }
        ActiveSetting execSettings = execSettingsOpt.get();

        Optional<Duration> periodOpt = execSettings.getParam("wall")
                .map(Long::parseLong)
                .map(Duration::ofNanos);

        if (periodOpt.isEmpty() || periodOpt.get().equals(Duration.ZERO)) {
            periodOpt = Optional.of(ASYNC_PROFILER_DEFAULT_INTERVAL);
        }

        // Every sample is weighted by the period of the wall clock
        long periodInNanos = periodOpt.get().toNanos();
        return event.putExtras(Map.of("sample_interval", String.valueOf(periodInNanos)));
    }
}
