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

package cafe.jeffrey.profile.heapdump.model;

/**
 * Outcome of the static unloadability check performed on a single class
 * loader.
 *
 * <ul>
 *   <li>{@link #UNLOADABLE} — the loader has no live instances of any class
 *       it defined and is itself not a GC root, so the next metaspace-aware
 *       GC cycle is free to unload it.</li>
 *   <li>{@link #PINNED_ROOTED} — the loader is directly held by a GC root.
 *       This is the normal state for Bootstrap, Platform, System loaders and
 *       any application loader still referenced by a live thread.</li>
 *   <li>{@link #PINNED_TRANSITIVE} — the loader is not a GC root, yet
 *       instances of its classes remain reachable. This is the canonical
 *       "redeploy leak" signature.</li>
 * </ul>
 */
public enum UnloadabilityVerdict {
    UNLOADABLE,
    PINNED_ROOTED,
    PINNED_TRANSITIVE
}
