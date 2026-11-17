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

package cafe.jeffrey.provider.profile.api;

import tools.jackson.core.type.TypeReference;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * A generic caching decorator for {@link Supplier} implementations.
 * Caches the result of the delegate supplier in a {@link ProfileCacheRepository}.
 *
 * @param <T> the type of the cached value
 */
public class CachingSupplier<T> implements Supplier<T> {

    private final Supplier<T> delegate;
    private final ProfileCacheRepository cache;
    private final String cacheKey;
    private final Class<T> type;
    private final TypeReference<T> typeRef;

    /**
     * Creates a caching supplier using a Class for type information.
     */
    public CachingSupplier(
            Supplier<T> delegate,
            ProfileCacheRepository cache,
            String cacheKey,
            Class<T> type) {
        this.delegate = delegate;
        this.cache = cache;
        this.cacheKey = cacheKey;
        this.type = type;
        this.typeRef = null;
    }

    /**
     * Creates a caching supplier using a TypeReference for generic types.
     */
    public CachingSupplier(
            Supplier<T> delegate,
            ProfileCacheRepository cache,
            String cacheKey,
            TypeReference<T> typeRef) {
        this.delegate = delegate;
        this.cache = cache;
        this.cacheKey = cacheKey;
        this.type = null;
        this.typeRef = typeRef;
    }

    @Override
    public T get() {
        Optional<T> cached = getCached();
        if (cached.isPresent()) {
            return cached.get();
        }

        T result = delegate.get();
        cache.put(cacheKey, result);
        return result;
    }

    private Optional<T> getCached() {
        if (type != null) {
            return cache.get(cacheKey, type);
        } else {
            return cache.get(cacheKey, typeRef);
        }
    }
}
