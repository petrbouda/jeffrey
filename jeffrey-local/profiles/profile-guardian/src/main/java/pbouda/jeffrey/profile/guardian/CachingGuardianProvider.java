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

package pbouda.jeffrey.profile.guardian;

import com.fasterxml.jackson.core.type.TypeReference;
import pbouda.jeffrey.provider.profile.cache.CachingSupplier;
import pbouda.jeffrey.provider.profile.repository.ProfileCacheRepository;
import pbouda.jeffrey.shared.common.CacheKey;

import java.util.List;

/**
 * A caching decorator for {@link GuardianProvider} that caches the result in a {@link ProfileCacheRepository}.
 */
public class CachingGuardianProvider implements GuardianProvider {

    private static final TypeReference<List<GuardianResult>> GUARDIAN_RESULT_TYPE =
            new TypeReference<List<GuardianResult>>() {
            };

    private final CachingSupplier<List<GuardianResult>> cachingSupplier;

    public CachingGuardianProvider(
            ProfileCacheRepository cacheRepository,
            GuardianProvider delegate) {
        this.cachingSupplier = new CachingSupplier<>(
                delegate, cacheRepository, CacheKey.PROFILE_GUARDIAN, GUARDIAN_RESULT_TYPE);
    }

    @Override
    public List<GuardianResult> get() {
        return cachingSupplier.get();
    }
}
