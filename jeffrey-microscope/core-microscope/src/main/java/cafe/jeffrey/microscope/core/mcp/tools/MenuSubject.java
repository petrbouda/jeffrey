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
package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.model.ProfileKind;

import java.util.Map;
import java.util.Optional;

/**
 * What a menu is offered for: a recording, whose areas read events and samples, or a heap dump, whose
 * areas read its index and stored reports. A profile is offered the groups of its own subject only, so
 * neither kind is buried under lines it cannot answer.
 */
enum MenuSubject {

    RECORDING,
    HEAP_DUMP;

    /** The subject of each kind of profile; a perf-counters snapshot has none, its counters have no menu. */
    private static final Map<ProfileKind, MenuSubject> BY_KIND = Map.of(
            ProfileKind.JFR, RECORDING,
            ProfileKind.STACK_SAMPLES, RECORDING,
            ProfileKind.HEAP_DUMP, HEAP_DUMP);

    static Optional<MenuSubject> of(ProfileKind kind) {
        return Optional.ofNullable(BY_KIND.get(kind));
    }
}
