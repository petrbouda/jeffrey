/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.settings;

import tools.jackson.core.type.TypeReference;
import cafe.jeffrey.microscope.model.settings.ActiveSetting;
import cafe.jeffrey.microscope.model.settings.ActiveSettings;
import cafe.jeffrey.shared.common.CacheKey;
import cafe.jeffrey.provider.profile.api.ProfileCacheRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;

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
