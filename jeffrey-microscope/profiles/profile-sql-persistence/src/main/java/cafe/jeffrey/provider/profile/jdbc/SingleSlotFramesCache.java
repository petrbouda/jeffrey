/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.provider.profile.jdbc;

import javax.sql.DataSource;
import java.util.function.Supplier;

/**
 * Keeps at most one {@link FramesCache} alive — for the single currently-open profile (mirroring
 * {@code SingleSlotDatabaseManager}, which keeps a single profile database open at a time). The
 * profile is identified by the identity of its {@link DataSource}: the single-slot database manager
 * hands out the same instance for repeated opens of the same profile and a new one after a switch,
 * so an identity check is both a correct and cheap cache key.
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
