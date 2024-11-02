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

import pbouda.jeffrey.common.Collector;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ActiveSettingsCollector implements Collector<Map<SettingNameLabel, ActiveSetting>, Map<SettingNameLabel, ActiveSetting>> {

    @Override
    public Supplier<Map<SettingNameLabel, ActiveSetting>> empty() {
        return HashMap::new;
    }

    @Override
    public Map<SettingNameLabel, ActiveSetting> combiner(
            Map<SettingNameLabel, ActiveSetting> partial1,
            Map<SettingNameLabel, ActiveSetting> partial2) {

        Map<SettingNameLabel, ActiveSetting> combined = new HashMap<>(partial1);
        for (Map.Entry<SettingNameLabel, ActiveSetting> entry : partial2.entrySet()) {
            combined.merge(entry.getKey(), entry.getValue(), (setting1, setting2) -> {
                if (setting2.isEnabled()) {
                    return setting2;
                } else {
                    return setting1;
                }
            });
        }
        return combined;
    }

    @Override
    public Map<SettingNameLabel, ActiveSetting> finisher(Map<SettingNameLabel, ActiveSetting> combined) {
        return combined;
    }
}
