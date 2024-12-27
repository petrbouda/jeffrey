/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.profile.summary.enhancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.EventSummary;
import pbouda.jeffrey.common.EventTypeName;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.profile.settings.ActiveSetting;
import pbouda.jeffrey.profile.settings.ActiveSettings;
import pbouda.jeffrey.profile.summary.EventSummaryEnhancer;

import java.time.Duration;
import java.util.Optional;

public class WallClockSamplesWeightEnhancer implements EventSummaryEnhancer {

    private static final Logger LOG = LoggerFactory.getLogger(WallClockSamplesWeightEnhancer.class);

    private final ActiveSettings settings;

    public WallClockSamplesWeightEnhancer(ActiveSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean isApplicable(Type eventType) {
        return Type.WALL_CLOCK_SAMPLE.sameAs(eventType);
    }

    @Override
    public EventSummary apply(EventSummary event) {
        Optional<ActiveSetting> execSettingsOpt = settings.findByName(EventTypeName.EXECUTION_SAMPLE);
        if (execSettingsOpt.isEmpty()) {
            LOG.warn("The ActiveSettings is now available for Execution Samples");
            return event;
        }
        ActiveSetting execSettings = execSettingsOpt.get();

        Optional<Duration> periodOpt = execSettings.getParam("wall")
                .map(Long::parseLong)
                .map(Duration::ofNanos);

        if (periodOpt.isEmpty()) {
            LOG.warn("The `period` or `interval` is not set for the Execution Samples");
            return event;
        }

        // Every sample is weighted by the period of the wall clock
        long periodInNanos = periodOpt.get().toNanos();
        return event
                .copyWithWeight(event.samples() * periodInNanos)
                .copyAndAddExtra("sample_interval", String.valueOf(periodInNanos));
    }
}
