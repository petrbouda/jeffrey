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

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.model.ActiveSetting;
import pbouda.jeffrey.common.model.ActiveSettings;
import pbouda.jeffrey.provider.api.repository.EventTypeWithFields;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbBasedActiveSettingsProvider implements ActiveSettingsProvider {

    private final ProfileEventRepository eventsReadRepository;

    public DbBasedActiveSettingsProvider(ProfileEventRepository eventsReadRepository) {
        this.eventsReadRepository = eventsReadRepository;
    }

    @Override
    public ActiveSettings get() {
        List<EventTypeWithFields> typeWithFields = eventsReadRepository.activeSettings();
        Map<String, ActiveSetting> combined = new HashMap<>();
        for (EventTypeWithFields entry : typeWithFields) {
            ObjectNode fields = entry.content();
            String paramName = fields.get("name").asText();
            String paramValue = fields.get("value").asText();

            combined.compute(entry.name(), (key, oldSetting) -> {
                ActiveSetting setting = oldSetting == null ? new ActiveSetting(entry.name()) : oldSetting;
                setting.putParam(paramName, paramValue);
                return setting;
            });
        }

        return new ActiveSettings(combined);
    }
}
