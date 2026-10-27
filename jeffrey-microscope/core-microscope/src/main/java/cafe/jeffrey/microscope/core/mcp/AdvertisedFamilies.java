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

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.profile.mcp.McpToolNames;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The tool families this installation actually advertises, and the one place a sentence that routes
 * a reader to another family asks whether that family is there.
 * <p>
 * A tool answer ends with where to go next, and the instructions sent at {@code server/discover} name
 * the families to reach for. Under a trimmed family list -- a preset, an explicit list, the hub or IDE
 * switch off -- a line naming a withheld family sends the reader after a tool that does not exist.
 * {@link #hint} keeps such a line only when its family is advertised; the line itself stays where it
 * is written.
 * <p>
 * Built from the same {@link ExternalMcpProperties} the assembler filters with, and with the same
 * two independent switches, so what a hint may name and what {@code tools/list} serves agree.
 *
 * @param families the advertised family names, as {@link ExternalMcpProperties#knownFamilies()} spells them
 */
public record AdvertisedFamilies(Set<String> families) {

    public static final String PROFILES = "profiles";
    public static final String RECORDINGS = "recordings";
    public static final String JFR = "jfr";
    public static final String FLAMEGRAPH = "flamegraph";
    public static final String COMPARE = "compare";
    public static final String TRACES = "traces";
    public static final String JVM = "jvm";
    public static final String HTTP = "http";
    public static final String JDBC = "jdbc";
    public static final String GRPC = "grpc";
    public static final String METHOD_TRACING = "methodtracing";
    public static final String IO = "io";
    public static final String BLOCKING = "blocking";
    public static final String TIMELINE = "timeline";
    public static final String MEMORY = "memory";
    public static final String HEAP = "heap";
    public static final String HUBS = "hubs";
    public static final String IDE = "ide";
    public static final String OPERATIONS = "operations";

    /** What a hint to a withheld family becomes: nothing, which {@code NextSteps} leaves out. */
    private static final String NO_HINT = "";

    /** The families behind a switch of their own, on top of the preset or explicit list. */
    private static final Map<String, Predicate<ExternalMcpProperties>> SWITCHES = Map.of(
            HUBS, ExternalMcpProperties::hubsEnabled,
            IDE, ExternalMcpProperties::ideEnabled);

    public AdvertisedFamilies {
        if (families == null) {
            throw new IllegalArgumentException("families must not be null");
        }
        families = Set.copyOf(families);
    }

    /**
     * Every known family the properties select, less the ones whose own switch is off.
     */
    public static AdvertisedFamilies of(ExternalMcpProperties properties) {
        return new AdvertisedFamilies(ExternalMcpProperties.knownFamilies().stream()
                .filter(properties::advertises)
                .filter(family -> SWITCHES.getOrDefault(family, any -> true).test(properties))
                .collect(Collectors.toUnmodifiableSet()));
    }

    public boolean has(String family) {
        return families.contains(family);
    }

    /**
     * Whether the named tool is served here: the same test {@link #hint} makes, asked of the family
     * the tool's name starts with. A next call to a tool this answers {@code false} for is left out.
     */
    public boolean servesTool(String tool) {
        return has(McpToolNames.familyOf(tool));
    }

    /**
     * The text when the family it routes to is advertised, and an empty string when it is not.
     */
    public String hint(String family, String text) {
        return hintOr(family, text, NO_HINT);
    }

    /**
     * The text when the family it routes to is advertised, and the fallback when it is not -- for an
     * answer whose next step is a single field that must not come back blank.
     */
    public String hintOr(String family, String text, String fallback) {
        return has(family) ? text : fallback;
    }
}
