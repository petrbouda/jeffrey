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

package pbouda.jeffrey.generator.timeseries;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.settings.ActiveSettingsProvider;

import java.time.Duration;
import java.util.function.Function;

public abstract class AbstractTimeseriesGenerator implements TimeseriesGenerator {

    private static final Function<RecordedEvent, Long> INCREMENTAL_VALUE_EXTRACTOR = __ -> 1L;

    protected Function<RecordedEvent, Long> valueExtractor(Config config, ActiveSettingsProvider settingsProvider) {
        var valueExtractor = INCREMENTAL_VALUE_EXTRACTOR;

        // Special handling for wall clock samples, it contains a field "samples" which is the number of samples
        // aggregated in a single event. Weight for the wallcalock samples is calculated as samples * interval.
        if (config.eventType().isWallClockSample()) {
            if (config.collectWeight()) {
                Duration interval = settingsProvider.get().asprofInterval();
                valueExtractor = event -> (long) event.getInt("samples") * interval.toNanos();
            } else {
                valueExtractor = event -> (long) event.getInt("samples");
            }
        } else {
            if (config.collectWeight()) {
                valueExtractor = config.eventType().weightExtractor();
                if (valueExtractor == null) {
                    valueExtractor = INCREMENTAL_VALUE_EXTRACTOR;
                }
            }
        }
        return valueExtractor;
    }
}
