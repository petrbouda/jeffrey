/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.parser;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provider.profile.api.EventSetting;

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
        EventType eventType = eventTypes.get(eventTypeIt);
        if (eventType == null) {
            LOG.warn("Unknown event type: curr_event_id={} event_types={}", eventTypeIt, eventTypes);
            return null;
        }
        String eventName = eventType.getName();

        String name = event.getString("name");
        String value = event.getString("value");
        return new EventSetting(eventName, name, value);
    }
}
