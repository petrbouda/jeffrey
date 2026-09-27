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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The labelled sections of a JVM's configuration, one per JDK configuration event, as the Jeffrey UI
 * shows them as tabs. {@code jvm_configuration} takes one of these constants and lists them by the same
 * names, so a value from one answer is the input to the next.
 */
public enum ConfigurationTab {

    JVM_INFORMATION("JVM Information"),
    CONTAINER_CONFIGURATION("Container Configuration"),
    CPU_INFORMATION("CPU Information"),
    OS_INFORMATION("OS Information"),
    GC_CONFIGURATION("GC Configuration"),
    GC_HEAP_CONFIGURATION("GC Heap Configuration"),
    GC_SURVIVOR_CONFIGURATION("GC Survivor Configuration"),
    TLAB_CONFIGURATION("TLAB Configuration"),
    YOUNG_GENERATION_CONFIGURATION("Young Generation Configuration"),
    COMPILER_CONFIGURATION("Compiler Configuration"),
    VIRTUALIZATION_INFORMATION("Virtualization Information");

    private static final Map<String, ConfigurationTab> BY_LABEL = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ConfigurationTab::label, Function.identity()));

    private final String label;

    ConfigurationTab(String label) {
        this.label = label;
    }

    /** The JDK's {@code @Label} of the event, which is what the configuration is keyed by. */
    String label() {
        return label;
    }

    /** The tab a configuration key belongs to; empty for a key no tab is defined for. */
    static Optional<ConfigurationTab> ofLabel(String label) {
        return Optional.ofNullable(BY_LABEL.get(label));
    }
}
