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

import cafe.jeffrey.profile.model.FlamegraphPanel;
import cafe.jeffrey.profile.model.WeightKind;
import cafe.jeffrey.profile.panel.PanelSection;

import java.util.Map;

/**
 * What a flamegraph panel's samples measure, as far as choosing an investigation goes: where the CPU
 * went, where the time went, what allocated, what waited on a lock — or something no menu area is
 * about (method traces, native allocation).
 */
enum SampleKind {

    CPU,
    WALL,
    ALLOCATION,
    BLOCKING,
    OTHER;

    private static final Map<String, SampleKind> BY_JFR_SECTION = Map.of(
            PanelSection.EXECUTION.id(), CPU,
            PanelSection.CPU_TIME.id(), CPU,
            PanelSection.WALL.id(), WALL,
            PanelSection.ALLOCATION.id(), ALLOCATION,
            PanelSection.BLOCKING.id(), BLOCKING);

    /**
     * The kind of a JFR panel by its section, or of an imported sample set's panel by its weight: an
     * imported set that counts bytes is allocation, any other is where the CPU went.
     */
    static SampleKind of(FlamegraphPanel panel, boolean stackSampleImport) {
        if (stackSampleImport) {
            return panel.weight().kind() == WeightKind.BYTES ? ALLOCATION : CPU;
        }
        return BY_JFR_SECTION.getOrDefault(panel.section(), OTHER);
    }
}
