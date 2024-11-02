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
import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.EventTypeName;
import pbouda.jeffrey.common.ExecutionSampleType;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.basic.event.EventSummary;
import pbouda.jeffrey.generator.basic.info.ExtraInfoEnhancer;
import pbouda.jeffrey.settings.ActiveSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TlabAllocationSamplesExtraInfo implements ExtraInfoEnhancer {

    private static final Logger LOG = LoggerFactory.getLogger(TlabAllocationSamplesExtraInfo.class);

    private final ActiveSettings settings;

    public TlabAllocationSamplesExtraInfo(ActiveSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean isApplicable(Type eventType) {
        return Type.OBJECT_ALLOCATION_IN_NEW_TLAB.sameAs(eventType);
    }

    @Override
    public EventSummary apply(EventSummary event) {
        Optional<EventSource> eventSourceOpt = settings.allocationSupportedBy();
        if (eventSourceOpt.isEmpty()) {
            LOG.warn("The event source is not set for the TLAB allocation samples");
            return event;
        }

        Map<String, String> entries = new HashMap<>();
        EventSource eventSource = eventSourceOpt.get();
        entries.put("source", eventSource.getLabel());
        entries.put("type", EventTypeName.OBJECT_ALLOCATION_IN_NEW_TLAB);
        return event.copyAndAddExtras(entries);
    }
}
