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

package cafe.jeffrey.microscope.core.manager.ide;

import cafe.jeffrey.microscope.persistence.api.IdeTargetLink;
import cafe.jeffrey.microscope.persistence.api.IdeTargetsRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the chosen IDE-window target per profile, so a window is picked once and reused for every
 * subsequent jump in that profile.
 *
 * <p>Write-through over {@link IdeTargetsRepository}: the map answers the jumps, and the store is what
 * makes the choice outlive a restart of Jeffrey. Keeping it in memory alone meant every restart
 * silently unlinked every profile, and the reader found out by clicking a button that no longer
 * worked.
 *
 * <p>Only the durable half of a target is stored — which project, in which IDE, at which path. The
 * port and pid belong to one run of one IDE process and are wrong by the time a stored link is read
 * back, so a restored target carries none and {@link JeffreyPluginBridge} re-resolves them by
 * discovery. Persisting them would restore a stale port and spend the first jump after a restart
 * failing against it.
 */
public final class IdeTargetCache {

    /** What a restored target has instead of a port or a pid: nothing, until discovery says. */
    private static final int UNKNOWN = 0;

    private final Map<String, IdeTarget> byProfile = new ConcurrentHashMap<>();
    private final IdeTargetsRepository repository;

    public IdeTargetCache(IdeTargetsRepository repository) {
        this.repository = repository;
    }

    /**
     * The window linked to a profile, read from the store on the first ask after a restart.
     */
    public IdeTarget get(String profileId) {
        if (profileId == null) {
            return null;
        }
        return byProfile.computeIfAbsent(profileId, this::restore);
    }

    public void put(String profileId, IdeTarget target) {
        if (profileId == null || target == null) {
            return;
        }
        byProfile.put(profileId, target);
        repository.save(profileId, new IdeTargetLink(
                target.projectId(), target.projectName(), target.ideName(), target.basePath()));
    }

    public void clear(String profileId) {
        if (profileId == null) {
            return;
        }
        byProfile.remove(profileId);
        // Removed from the store too: a link the reader disconnected must not come back at the next
        // restart, which is precisely when they would have stopped expecting it.
        repository.delete(profileId);
    }

    /**
     * @return the stored link as a target with no port or pid, or null when the profile has none.
     *         Null rather than an empty {@link Optional} because it feeds
     *         {@link Map#computeIfAbsent}, which reads null as "nothing to cache" and so does not
     *         remember the miss.
     */
    private IdeTarget restore(String profileId) {
        return repository.find(profileId)
                .map(link -> new IdeTarget(
                        UNKNOWN,
                        link.projectId(),
                        link.ideName(),
                        link.projectName(),
                        link.basePath(),
                        UNKNOWN))
                .orElse(null);
    }
}
