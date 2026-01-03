/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.profile.enhancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.common.model.EventSubtype;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.shared.common.DurationUtils;
import pbouda.jeffrey.shared.common.settings.ActiveSetting;
import pbouda.jeffrey.shared.common.settings.ActiveSettings;
import pbouda.jeffrey.provider.profile.model.EventTypeBuilder;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class ExecutionSamplesWeightEnhancer implements EventTypeEnhancer {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionSamplesWeightEnhancer.class);
    private static final Duration ASYNC_PROFILER_DEFAULT_INTERVAL = Duration.ofMillis(10);

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

        RecordingEventSource eventSource = settings.executionSampleType()
                .map(EventSubtype::getSource)
                .orElse(null);

        Optional<Duration> periodOpt = switch (eventSource) {
            case JDK -> execSettings.getParam("period").map(DurationUtils::parse);
            case ASYNC_PROFILER -> {
                yield execSettings.getParam("interval")
                        .map(Long::parseLong)
                        .map(Duration::ofNanos)
                        .map(interval -> interval == Duration.ZERO ? ASYNC_PROFILER_DEFAULT_INTERVAL : interval);
            }
            case UNKNOWN -> Optional.empty();
            case null -> Optional.empty();
        };

        if (periodOpt.isEmpty()) {
            LOG.warn("The `period` or `interval` is not set for the Execution Samples");
            return builder;
        }

        // Every sample is weighted by the period of the interval/period (depending on the source)
        long periodInNanos = periodOpt.get().toNanos();
        return builder.putExtras(Map.of("sample_interval", String.valueOf(periodInNanos)));
    }
}
