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

package pbouda.jeffrey.settings;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.SingleEventProcessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ActiveSettingsProcessor extends SingleEventProcessor<Map<String, ActiveSetting>> {

    private Map<Long, EventType> eventTypes = Map.of();

    private final Map<String, ActiveSetting> result = new HashMap<>();

    public ActiveSettingsProcessor() {
        super(Type.ACTIVE_SETTING);
    }

    @Override
    public void onStart(List<EventType> eventTypes) {
        this.eventTypes = eventTypes.stream()
                .collect(Collectors.toMap(EventType::getId, e -> e));
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        String eventName = activeSettingValue(event.getValue("id"));
        String name = event.getString("name");
        String value = event.getString("value");

        ActiveSetting setting = result.get(eventName);
        if (setting == null) {
            setting = new ActiveSetting(eventName);
            result.put(eventName, setting);
        }
        setting.putParam(name, value);
        return Result.CONTINUE;
    }

    @Override
    public Map<String, ActiveSetting> get() {
        return result;
    }

    private String activeSettingValue(long eventId) {
        EventType eventType = eventTypes.get(eventId);
        return eventType == null ? String.valueOf(eventId) : eventType.getLabel();
    }
}
