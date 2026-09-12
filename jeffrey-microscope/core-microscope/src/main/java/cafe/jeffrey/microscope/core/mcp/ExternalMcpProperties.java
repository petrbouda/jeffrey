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

package cafe.jeffrey.microscope.core.mcp;

import java.util.Map;
import java.util.Set;

/**
 * Static configuration of the external MCP server at {@code /api/mcp}.
 * <p>
 * Read once from {@code jeffrey.microscope.mcp.*} at wiring time: the server is on by default, and
 * whether an installation exposes it is a deployment decision made alongside the bind address and the
 * reverse proxy. Turning it off is an application property and takes a restart.
 * <p>
 * The endpoint has no authentication of its own, in common with everything else under
 * {@code /api/internal}. What limits who can reach it is the address Jeffrey binds to and whatever
 * sits in front of it.
 *
 * @param enabled     whether the endpoint answers at all; while off it responds {@code 404}
 * @param hubsEnabled whether the {@code hubs_} family is advertised, which lets a client list and
 *                    pull recordings from a connected Jeffrey Hub. Its own switch because it is the
 *                    one family that reaches past this machine: everything else reads what is already
 *                    here, while this one talks to remote infrastructure and can move gigabytes off it
 * @param ideEnabled  whether the {@code ide_} family is advertised, which lets a client ask the
 *                    developer's running IntelliJ where a frame lives, read a class through it, and
 *                    move its editor. Its own switch for the same reason as the hub family, one step
 *                    closer to home: everything else reads a recording Jeffrey already holds, while
 *                    this reaches into another process on this machine and can put a file on
 *                    somebody's screen
 * @param families    the tool families to advertise, empty meaning the selected preset. A client that pays
 *                    for every schema on every turn can be given only the families it uses
 * @param preset      a lowercase preset name: all (default), jfr, heap or hub. Explicit families
 *                    override the preset; the hub and IDE switches remain independent gates
 */
public record ExternalMcpProperties(
        boolean enabled,
        boolean hubsEnabled,
        boolean ideEnabled,
        Set<String> families,
        String preset) {

    private static final String DEFAULT_PRESET = "all";

    private static final Map<String, Set<String>> PRESETS = Map.of(
            DEFAULT_PRESET, Set.of("profiles", "recordings", "jfr", "flamegraph", "compare", "traces",
                    "jvm", "http", "jdbc", "grpc", "methodtracing", "io", "blocking", "timeline",
                    "memory", "heap", "hubs", "ide"),
            "jfr", Set.of("profiles", "recordings", "jfr", "flamegraph", "jvm", "compare"),
            "heap", Set.of("profiles", "recordings", "heap"),
            "hub", Set.of("profiles", "recordings", "hubs"));

    public ExternalMcpProperties {
        families = families == null ? Set.of() : Set.copyOf(families);
        preset = preset == null ? DEFAULT_PRESET : preset;
        if (!PRESETS.containsKey(preset)) {
            throw new IllegalArgumentException("Unknown MCP preset '" + preset
                    + "'. Use a lowercase preset: all, jfr, heap or hub.");
        }
        Set<String> knownFamilies = PRESETS.get(DEFAULT_PRESET);
        if (!knownFamilies.containsAll(families)) {
            throw new IllegalArgumentException("Unknown MCP families: "
                    + families.stream().filter(family -> !knownFamilies.contains(family)).sorted().toList()
                    + ". Family names are lowercase; supported families: "
                    + knownFamilies.stream().sorted().toList());
        }
    }

    /** Existing callers keep the full tool surface unless they explicitly select families. */
    public ExternalMcpProperties(boolean enabled, boolean hubsEnabled, boolean ideEnabled, Set<String> families) {
        this(enabled, hubsEnabled, ideEnabled, families, DEFAULT_PRESET);
    }

    /**
     * Whether a family is selected by the explicit allowlist, or by the preset when no allowlist is
     * set. The assembler applies the independent hub and IDE switches before retaining families.
     */
    public boolean advertises(String family) {
        return (families.isEmpty() ? PRESETS.get(preset) : families).contains(family);
    }
}
