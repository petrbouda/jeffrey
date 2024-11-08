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

package pbouda.jeffrey.profile.settings;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.EventProcessor;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ActiveSettingsProcessor implements EventProcessor<Map<SettingNameLabel, ActiveSetting>> {

    private static final Logger LOG = LoggerFactory.getLogger(ActiveSettingsProcessor.class);

    private static final ProcessableEvents PROCESSABLE_EVENTS = new ProcessableEvents(List.of(Type.ACTIVE_SETTING));

    private Map<Long, EventType> eventTypes = Map.of();

    private final Map<SettingNameLabel, ActiveSetting> result = new HashMap<>();

    @Override
    public ProcessableEvents processableEvents() {
        return PROCESSABLE_EVENTS;
    }

    @Override
    public void onStart(List<EventType> eventTypes) {
        this.eventTypes = eventTypes.stream()
                .collect(Collectors.toMap(EventType::getId, e -> e));
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        EventType eventType = eventTypes.get((long) event.getValue("id"));
        if (eventType == null) {
            LOG.warn("Unknown event type: {}", event);
            return Result.CONTINUE;
        }
        Type type = Type.from(eventType);

        String name = event.getString("name");
        String value = event.getString("value");

        SettingNameLabel nameLabel = new SettingNameLabel(type.code(), eventType.getLabel());
        ActiveSetting setting = result.get(nameLabel);
        if (setting == null) {
            setting = new ActiveSetting(type, eventType.getLabel());
            result.put(nameLabel, setting);
        }
        setting.putParam(name, value);
        return Result.CONTINUE;
    }

    @Override
    public Map<SettingNameLabel, ActiveSetting> get() {
        return result;
    }
}
