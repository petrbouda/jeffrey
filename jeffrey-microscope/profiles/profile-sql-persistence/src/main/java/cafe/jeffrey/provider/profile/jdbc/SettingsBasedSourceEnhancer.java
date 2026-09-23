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
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.settings.ActiveSettings;
import cafe.jeffrey.provider.profile.api.EventTypeBuilder;

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
