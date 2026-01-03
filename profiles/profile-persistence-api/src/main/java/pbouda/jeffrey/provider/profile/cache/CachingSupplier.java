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

package pbouda.jeffrey.provider.profile.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import pbouda.jeffrey.provider.profile.repository.ProfileCacheRepository;

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
