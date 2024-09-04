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
import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.basic.event.EventSummary;

public class AllocationSamplesExtraInfo implements ExtraInfoEnhancer {

    private final ExtraInfo extraInfo;

    public AllocationSamplesExtraInfo(ExtraInfo extraInfo) {
        this.extraInfo = extraInfo;
    }

    @Override
    public boolean isApplicable(EventType eventType) {
        return Type.OBJECT_ALLOCATION_IN_NEW_TLAB.sameAs(eventType);
    }

    @Override
    public EventSummary apply(EventSummary eventSummary) {
        if (extraInfo.allocSource() == EventSource.ASYNC_PROFILER && extraInfo.allocEvent() != null) {
            return eventSummary.copyAndAddExtra("source", extraInfo.allocSource());
        }
        return eventSummary;
    }
}
