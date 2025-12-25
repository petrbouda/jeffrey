/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import pbouda.jeffrey.common.settings.ActiveSetting;
import pbouda.jeffrey.common.settings.ActiveSettings;
import pbouda.jeffrey.common.persistence.CacheKey;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;

import java.util.List;
import java.util.Optional;

public class CachedActiveSettingsProvider implements ActiveSettingsProvider {

    private static final TypeReference<List<ActiveSetting>> ACTIVE_SETTINGS_TYPE =
            new TypeReference<List<ActiveSetting>>() {
            };

    private final ProfileEventTypeRepository eventTypeRepository;
    private final ProfileCacheRepository cacheRepository;

    public CachedActiveSettingsProvider(
            ProfileEventTypeRepository eventTypeRepository,
            ProfileCacheRepository cacheRepository) {

        this.eventTypeRepository = eventTypeRepository;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public ActiveSettings get() {
        Optional<List<ActiveSetting>> cachedSettings = cacheRepository.get(
                CacheKey.PROFILE_ACTIVE_SETTINGS, ACTIVE_SETTINGS_TYPE);

        if (cachedSettings.isPresent()) {
            return new ActiveSettings(cachedSettings.get());
        } else {
            List<ActiveSetting> settings = eventTypeRepository.eventSummaries().stream()
                    .map(type -> new ActiveSetting(type.name(), type.settings()))
                    .toList();

            ActiveSettings activeSettings = new ActiveSettings(settings);
            cacheRepository.put(CacheKey.PROFILE_ACTIVE_SETTINGS, settings);
            return activeSettings;
        }
    }
}
