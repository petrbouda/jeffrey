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

package pbouda.jeffrey.profile.thread;

import pbouda.jeffrey.provider.profile.cache.CachingSupplier;
import pbouda.jeffrey.provider.profile.repository.ProfileCacheRepository;
import pbouda.jeffrey.shared.common.CacheKey;

/**
 * A caching decorator for {@link ThreadInfoProvider} that caches the result in a {@link ProfileCacheRepository}.
 */
public class CachingThreadProvider implements ThreadInfoProvider {

    private final CachingSupplier<ThreadRoot> cachingSupplier;

    public CachingThreadProvider(
            ThreadInfoProvider delegate,
            ProfileCacheRepository cacheRepository) {
        this.cachingSupplier = new CachingSupplier<>(
                delegate, cacheRepository, CacheKey.PROFILE_THREAD, ThreadRoot.class);
    }

    @Override
    public ThreadRoot get() {
        return cachingSupplier.get();
    }
}
