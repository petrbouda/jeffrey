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

package cafe.jeffrey.hub.core.manager.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.measure.Elapsed;
import cafe.jeffrey.shared.common.measure.Measuring;

import java.time.Clock;
import java.time.Instant;

/**
 * In-memory cache of the hub's {@link StorageOverview}. Computing the overview walks
 * every project repository on the filesystem, so it is recomputed by the periodic
 * {@code StorageOverviewRefresherJob} (and on demand via {@link #refresh()}) instead
 * of on every request. Readers get the last computed snapshot together with the time
 * it was computed at.
 */
public class StorageOverviewCache {

    /**
     * The last computed overview and the instant it was computed at.
     */
    public record CachedOverview(StorageOverview overview, Instant computedAt) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(StorageOverviewCache.class);

    private final StorageManager storageManager;
    private final Clock clock;

    private volatile CachedOverview cached;

    public StorageOverviewCache(StorageManager storageManager, Clock clock) {
        this.storageManager = storageManager;
        this.clock = clock;
    }

    /**
     * Returns the cached overview. If nothing has been computed yet (a request raced
     * ahead of the startup tick of the refresher job, or the job is disabled), the
     * overview is computed synchronously once.
     */
    public CachedOverview get() {
        CachedOverview current = cached;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (cached == null) {
                recompute();
            }
            return cached;
        }
    }

    /**
     * Recomputes the overview from the filesystem and replaces the cached snapshot.
     * Serialized so a manual refresh and a job tick never scan concurrently.
     */
    public synchronized CachedOverview refresh() {
        return recompute();
    }

    private synchronized CachedOverview recompute() {
        Elapsed<StorageOverview> elapsed = Measuring.s(storageManager::overview);
        CachedOverview result = new CachedOverview(elapsed.entity(), clock.instant());
        cached = result;
        LOG.debug("Recomputed storage overview: projects={} duration_ms={}",
                result.overview().projects().size(), elapsed.duration().toMillis());
        return result;
    }
}
