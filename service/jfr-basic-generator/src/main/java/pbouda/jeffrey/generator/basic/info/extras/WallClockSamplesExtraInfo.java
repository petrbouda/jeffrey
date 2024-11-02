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

import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.EventTypeName;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.basic.event.EventSummary;
import pbouda.jeffrey.generator.basic.info.ExtraInfoEnhancer;

import java.util.HashMap;
import java.util.Map;

public class WallClockSamplesExtraInfo implements ExtraInfoEnhancer {

    @Override
    public boolean isApplicable(Type eventType) {
        return Type.WALL_CLOCK_SAMPLE.sameAs(eventType);
    }

    @Override
    public EventSummary apply(EventSummary event) {
        Map<String, String> entries = new HashMap<>();
        entries.put("source", EventSource.ASYNC_PROFILER.getLabel());
        entries.put("type", EventTypeName.WALL_CLOCK_SAMPLE);
        return event.copyAndAddExtras(entries);
    }
}
