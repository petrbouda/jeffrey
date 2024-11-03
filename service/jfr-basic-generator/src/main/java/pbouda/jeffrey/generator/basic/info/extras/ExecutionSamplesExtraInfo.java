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

package pbouda.jeffrey.generator.basic.info.extras;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.ExecutionSampleType;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.basic.event.EventSummary;
import pbouda.jeffrey.generator.basic.info.ExtraInfoEnhancer;
import pbouda.jeffrey.settings.ActiveSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ExecutionSamplesExtraInfo implements ExtraInfoEnhancer {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionSamplesExtraInfo.class);

    private final ActiveSettings settings;

    public ExecutionSamplesExtraInfo(ActiveSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean isApplicable(Type eventType) {
        return Type.EXECUTION_SAMPLE.sameAs(eventType);
    }

    @Override
    public EventSummary apply(EventSummary event) {
        Map<String, String> entries = new HashMap<>();

        Optional<ExecutionSampleType> executionSampleType = settings.executionSampleType();
        if (executionSampleType.isPresent()) {
            ExecutionSampleType exec = executionSampleType.get();
            entries.put("source", exec.getSource().getLabel());
            entries.put("event", exec.name());
            entries.put("type", exec.getLabel());

            if (exec == ExecutionSampleType.METHOD) {
                settings.asprofRecording()
                        .flatMap(s -> s.getParam("event"))
                        .ifPresent(value -> entries.put("method", value));
            }
        } else {
            LOG.warn("The event source is not set for the Execution Samples");
            return event;
        }

        return event.copyAndAddExtras(entries);
    }
}
