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

import pbouda.jeffrey.common.persistence.CacheKey;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;

import java.util.Optional;

public class CachingThreadProvider implements ThreadInfoProvider {

    private final ThreadInfoProvider threadInfoProvider;
    private final ProfileCacheRepository cacheRepository;

    public CachingThreadProvider(
            ThreadInfoProvider profileThreadInfoProvider,
            ProfileCacheRepository cacheRepository) {

        this.threadInfoProvider = profileThreadInfoProvider;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public ThreadRoot get() {
        Optional<ThreadRoot> cached = cacheRepository.get(CacheKey.PROFILE_THREAD, ThreadRoot.class);

        if (cached.isPresent()) {
            return cached.get();
        } else {
            ThreadRoot threadRows = threadInfoProvider.get();
            cacheRepository.insert(CacheKey.PROFILE_THREAD, threadRows);
            return threadRows;
        }
    }
}
