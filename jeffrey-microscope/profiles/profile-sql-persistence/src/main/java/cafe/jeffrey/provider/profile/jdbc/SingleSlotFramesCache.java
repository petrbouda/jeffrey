/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.provider.profile.jdbc;

import javax.sql.DataSource;
import java.util.function.Supplier;

/**
 * Keeps at most one {@link FramesCache} alive — for the most recently used profile. The profile is
 * identified by the identity of its {@link DataSource}: {@code CachingDatabaseManager} hands out the
 * same stable handle instance for repeated opens of the same profile and a distinct one per profile,
 * so an identity check is both a correct and cheap cache key. When several profiles are used
 * concurrently this slot is reloaded per switch (a cache-miss cost), but it always returns frames
 * matching the requested {@link DataSource}.
 *
 * <p>Frame-mutating operations (class renaming, stacktrace transformations) must
 * {@link #invalidate(DataSource)} the slot so the next request reloads fresh frames.
 */
public class SingleSlotFramesCache {

    private record Slot(DataSource dataSource, FramesCache framesCache) {
    }

    private Slot current;

    /**
     * Returns the cached frames for the given profile database, loading them via {@code loader}
     * only when the slot is empty or belongs to a different (previously opened) profile.
     */
    public synchronized FramesCache resolve(DataSource dataSource, Supplier<FramesCache> loader) {
        if (current != null && current.dataSource() == dataSource) {
            return current.framesCache();
        }

        FramesCache framesCache = loader.get();
        current = new Slot(dataSource, framesCache);
        return framesCache;
    }

    /**
     * Drops the cached frames of the given profile database (no-op when another profile is cached).
     */
    public synchronized void invalidate(DataSource dataSource) {
        if (current != null && current.dataSource() == dataSource) {
            current = null;
        }
    }
}
