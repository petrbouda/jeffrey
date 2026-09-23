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
