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

package pbouda.jeffrey.provider.writer.sql.enhancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.RecordingEventSource;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.settings.ActiveSettings;
import pbouda.jeffrey.provider.api.model.EventTypeBuilder;

import java.util.Optional;
import java.util.function.Function;

/**
 * A parameterized enhancer that looks up the {@link RecordingEventSource} from {@link ActiveSettings}.
 * Replaces individual enhancer classes like ThreadParkExtraEnhancer, TlabAllocationSamplesExtraEnhancer,
 * and MonitorEnterExtraEnhancer.
 */
public class SettingsBasedSourceEnhancer implements EventTypeEnhancer {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsBasedSourceEnhancer.class);

    private final Type eventType;
    private final Function<ActiveSettings, Optional<RecordingEventSource>> sourceExtractor;
    private final ActiveSettings settings;

    public SettingsBasedSourceEnhancer(
            Type eventType,
            Function<ActiveSettings, Optional<RecordingEventSource>> sourceExtractor,
            ActiveSettings settings) {
        this.eventType = eventType;
        this.sourceExtractor = sourceExtractor;
        this.settings = settings;
    }

    @Override
    public boolean isApplicable(Type type) {
        return eventType.sameAs(type);
    }

    @Override
    public EventTypeBuilder apply(EventTypeBuilder event) {
        Optional<RecordingEventSource> sourceOpt = sourceExtractor.apply(settings);
        if (sourceOpt.isEmpty()) {
            LOG.warn("The event source is not set: event_type={}", eventType.code());
            return event;
        }
        return event.withSource(sourceOpt.get());
    }
}
