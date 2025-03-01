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

import com.fasterxml.jackson.core.type.TypeReference;
import pbouda.jeffrey.common.model.ActiveSetting;
import pbouda.jeffrey.common.model.ActiveSettings;
import pbouda.jeffrey.common.persistence.CacheKey;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;

import java.util.Map;
import java.util.Optional;

public class CachingActiveSettingsProvider implements ActiveSettingsProvider {

    private static final TypeReference<Map<String, ActiveSetting>> ACTIVE_SETTINGS_TYPE =
            new TypeReference<Map<String, ActiveSetting>>() {
            };

    private final ActiveSettingsProvider activeSettingsProvider;
    private final ProfileCacheRepository cacheRepository;

    public CachingActiveSettingsProvider(
            ActiveSettingsProvider activeSettingsProvider,
            ProfileCacheRepository cacheRepository) {

        this.activeSettingsProvider = activeSettingsProvider;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public ActiveSettings get() {
        Optional<Map<String, ActiveSetting>> activeSettingsOpt = cacheRepository.get(
                CacheKey.PROFILE_ACTIVE_SETTINGS, ACTIVE_SETTINGS_TYPE);

        if (activeSettingsOpt.isEmpty()) {
            ActiveSettings activeSettings = activeSettingsProvider.get();
            cacheRepository.insert(CacheKey.PROFILE_ACTIVE_SETTINGS, activeSettings.settingsMap());
            return activeSettings;
        } else {
            return new ActiveSettings(activeSettingsOpt.get());
        }
    }
}
