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

package pbouda.jeffrey.profile.summary.enhancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.EventTypeName;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.profile.summary.EventSummaryEnhancer;
import pbouda.jeffrey.profile.summary.event.EventSummary;
import pbouda.jeffrey.profile.settings.ActiveSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ThreadParkExtraEnhancer implements EventSummaryEnhancer {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadParkExtraEnhancer.class);

    private final ActiveSettings settings;

    public ThreadParkExtraEnhancer(ActiveSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean isApplicable(Type eventType) {
        return Type.THREAD_PARK.sameAs(eventType);
    }

    @Override
    public EventSummary apply(EventSummary event) {
        Optional<EventSource> eventSourceOpt = settings.threadParkSupportedBy();
        if (eventSourceOpt.isEmpty()) {
            LOG.warn("The event source is not set for the Thread Park samples");
            return event;
        }

        Map<String, String> entries = new HashMap<>();
        entries.put("source", eventSourceOpt.get().getLabel());
        entries.put("type", EventTypeName.THREAD_PARK);
        return event.copyAndAddExtras(entries);
    }
}
