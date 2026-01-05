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

package pbouda.jeffrey.shared.persistence;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class EvictableCache<K, V> {

    private final ConcurrentHashMap<K, V> cache = new ConcurrentHashMap<>();
    private final Predicate<V> evictionPredicate;
    private final BiConsumer<K, V> evictionListener;

    public EvictableCache(
            Predicate<V> evictionPredicate,
            BiConsumer<K, V> evictionListener,
            Duration checkInterval,
            ScheduledExecutorService scheduler) {

        this.evictionPredicate = evictionPredicate;
        this.evictionListener = evictionListener;
        scheduler.scheduleAtFixedRate(
                this::evictExpired, checkInterval.toMillis(), checkInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void evictExpired() {
        cache.forEach((key, value) -> {
            if (evictionPredicate.test(value)) {
                if (cache.remove(key, value)) {
                    evictionListener.accept(key, value);
                }
            }
        });
    }

    public void put(K key, V value) {
        cache.put(key, value);
    }

    public V get(K key) {
        return cache.get(key);
    }

    public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
        return cache.computeIfAbsent(key, mappingFunction);
    }

    public V remove(K key) {
        return cache.remove(key);
    }
}
