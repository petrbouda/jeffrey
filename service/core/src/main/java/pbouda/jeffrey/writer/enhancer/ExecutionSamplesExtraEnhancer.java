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

package pbouda.jeffrey.writer.enhancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.EventSubtype;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.model.profile.EventType;
import pbouda.jeffrey.profile.settings.ActiveSettings;

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
    public EventType apply(EventType event) {
        EventType newEvent = event;

        Optional<EventSubtype> executionSampleType = settings.executionSampleType();
        if (executionSampleType.isPresent()) {
            EventSubtype exec = executionSampleType.get();
            newEvent = event.copyWithSource(exec.getSource())
                    .copyWithSubtype(exec.name());

            if (exec == EventSubtype.METHOD) {
                Map<String, String> entries = new HashMap<>();
                settings.asprofRecording()
                        .flatMap(s -> s.getParam("event"))
                        .ifPresent(value -> entries.put("method", value));
                newEvent = newEvent.copyAndAddExtras(entries);
            }
        } else {
            LOG.warn("The event source is not set for the Execution Samples");
        }

        return newEvent;
    }
}
