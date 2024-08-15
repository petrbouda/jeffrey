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

package pbouda.jeffrey.generator.basic.info;

import jdk.jfr.EventType;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.basic.event.EventSummary;

import java.util.HashMap;
import java.util.Map;

public class ExecutionSamplesExtraInfo implements ExtraInfoEnhancer {

    private final Map<String, String> settings;

    public ExecutionSamplesExtraInfo(Map<String, String> settings) {
        this.settings = settings;
    }

    @Override
    public boolean isApplicable(EventType eventType) {
        return Type.EXECUTION_SAMPLE.sameAs(eventType);
    }

    @Override
    public EventSummary apply(EventSummary event) {
        Map<String, Object> entries = new HashMap<>();
        String source = settings.get("source");
        if (source != null) {
            entries.put("source", source);
        }

        String cpuEvent = settings.get("cpu_event");
        if (cpuEvent != null) {
            entries.put("cpu_event", cpuEvent);
        }
        return event.copyAndAddExtras(entries);
    }
}
