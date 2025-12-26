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

package pbouda.jeffrey.profile.parser;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.provider.api.model.EventSetting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveSettingResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ActiveSettingResolver.class);

    private final Map<Long, EventType> eventTypes = new HashMap<>();

    public void update(List<EventType> eventTypes) {
        eventTypes.forEach(e -> this.eventTypes.put(e.getId(), e));
    }

    public EventSetting resolveSetting(RecordedEvent event) {
        long eventTypeIt = event.getValue("id");
        String eventName = eventTypes.get(eventTypeIt).getName();
        if (eventName == null) {
            LOG.warn("Unknown event type: curr_event_id={} event_types={}", eventTypeIt, eventTypes);
            return null;
        }

        String name = event.getString("name");
        String value = event.getString("value");
        return new EventSetting(eventName, name, value);
    }
}
