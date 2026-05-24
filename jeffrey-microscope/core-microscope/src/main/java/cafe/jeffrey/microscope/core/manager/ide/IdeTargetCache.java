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

package cafe.jeffrey.microscope.core.manager.ide;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the chosen IDE-window target per profile, so a window is picked once and reused for every
 * subsequent jump in that profile. In-memory for v1 (resets on Microscope restart).
 */
public final class IdeTargetCache {

    private final Map<String, IdeTarget> byProfile = new ConcurrentHashMap<>();

    public IdeTarget get(String profileId) {
        if (profileId == null) {
            return null;
        }
        return byProfile.get(profileId);
    }

    public void put(String profileId, IdeTarget target) {
        if (profileId != null && target != null) {
            byProfile.put(profileId, target);
        }
    }

    public void clear(String profileId) {
        if (profileId != null) {
            byProfile.remove(profileId);
        }
    }
}
