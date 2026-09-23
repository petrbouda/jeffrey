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
import cafe.jeffrey.microscope.model.settings.ActiveSettings;
import cafe.jeffrey.provider.profile.api.EventTypeBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ExecutionSamplesExtraEnhancer implements EventTypeEnhancer {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionSamplesExtraEnhancer.class);

    private final ActiveSettings settings;

    public ExecutionSamplesExtraEnhancer(ActiveSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean isApplicable(Type eventType) {
        return Type.EXECUTION_SAMPLE.sameAs(eventType);
    }

    @Override
    public EventTypeBuilder apply(EventTypeBuilder builder) {
        Optional<EventSubtype> executionSampleType = settings.executionSampleType();
        if (executionSampleType.isPresent()) {
            EventSubtype exec = executionSampleType.get();
            builder.withSource(exec.getSource())
                    .withSubtype(exec.name());

            if (exec == EventSubtype.METHOD) {
                Map<String, String> entries = new HashMap<>();
                settings.findFirstByType(Type.ACTIVE_RECORDING)
                        .flatMap(s -> s.getParam("event"))
                        .ifPresent(value -> entries.put("method", value));
                builder.putExtras(entries);
            }
        } else {
            LOG.warn("The event source is not set for the Execution Samples");
        }

        return builder;
    }
}
