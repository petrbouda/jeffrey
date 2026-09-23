/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.thread;

import cafe.jeffrey.provider.profile.api.CachingSupplier;
import cafe.jeffrey.provider.profile.api.ProfileCacheRepository;
import cafe.jeffrey.shared.common.CacheKey;

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
