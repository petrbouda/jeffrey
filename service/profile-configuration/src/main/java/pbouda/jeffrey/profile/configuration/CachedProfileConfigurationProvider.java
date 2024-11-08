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

package pbouda.jeffrey.profile.configuration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.persistence.CacheKey;
import pbouda.jeffrey.common.persistence.CacheRepository;

import java.util.Optional;

public class CachedProfileConfigurationProvider implements ProfileConfigurationProvider {

    private final ProfileConfigurationProvider profileConfigurationProvider;
    private final CacheRepository cacheRepository;

    public CachedProfileConfigurationProvider(
            ProfileConfigurationProvider profileConfigurationProvider,
            CacheRepository cacheRepository) {

        this.profileConfigurationProvider = profileConfigurationProvider;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public ObjectNode get() {
        Optional<ObjectNode> cached = cacheRepository.get(CacheKey.PROFILE_CONFIGURATION, ObjectNode.class);

        if (cached.isPresent()) {
            return cached.get();
        } else {
            ObjectNode configuration = profileConfigurationProvider.get();
            cacheRepository.insert(CacheKey.PROFILE_CONFIGURATION, configuration);
            return configuration;
        }
    }
}
