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

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static configuration of the external MCP server at {@code /api/mcp}.
 * <p>
 * Read once from {@code jeffrey.microscope.mcp.*} at wiring time: the server is on by default, and
 * whether an installation exposes it is a deployment decision made alongside the bind address and the
 * reverse proxy. Turning it off is an application property and takes a restart.
 * <p>
 * Authentication is optional: with no {@code token} the endpoint carries the trust assumption of the
 * rest of Jeffrey's HTTP API, and what limits who can reach it is the address Jeffrey binds to, the
 * host allowlist and whatever sits in front of it. With one, every request must present it as a
 * bearer token.
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
 * @param trustForwardedHeaders whether the request guard takes the host and scheme a client used from
 *                    {@code X-Forwarded-Host} and {@code X-Forwarded-Proto}. Only for a Jeffrey that
 *                    is reachable solely through a proxy that sets both, since anyone who can reach
 *                    Jeffrey directly can write them
 * @param token       the shared secret every request must present as {@code Authorization: Bearer};
 *                    blank (the default) requires none. Kept out of {@link #toString()}
 */
public record ExternalMcpProperties(
        boolean enabled,
        boolean hubsEnabled,
        boolean ideEnabled,
        Set<String> families,
        String preset,
        boolean trustForwardedHeaders,
        String token) {

    private static final String DEFAULT_PRESET = "all";

    private static final Map<String, Set<String>> PRESETS = Map.of(
            DEFAULT_PRESET, Set.of("profiles", "recordings", "jfr", "flamegraph", "compare", "traces",
                    "jvm", "http", "jdbc", "grpc", "methodtracing", "io", "blocking", "timeline",
                    "memory", "heap", "hubs", "ide", "operations"),
            // Everything analyze-jfr routes to: the per-domain families it hands a trace, an HTTP,
            // JDBC, gRPC, method-tracing, I/O, blocking, timeline or memory question to.
            "jfr", Set.of("profiles", "recordings", "jfr", "flamegraph", "jvm", "compare", "traces",
                    "http", "jdbc", "grpc", "methodtracing", "io", "blocking", "timeline", "memory",
                    "operations"),
            "heap", Set.of("profiles", "recordings", "heap", "operations"),
            "hub", Set.of("profiles", "recordings", "hubs", "operations"));

    private static final String OPERATIONS_FAMILY = "operations";

    /**
     * The families with a tool that is not read-only. Each one either starts work that only
     * {@code operations_status} can follow and {@code operations_cancel} can stop, or sits beside
     * those that do, so an explicit list that serves one of them has to serve {@code operations} too.
     */
    private static final Set<String> WRITER_FAMILIES = Set.of("recordings", "heap", "hubs", "ide", "jvm");

    private static final String HUBS_FAMILY = "hubs";
    private static final String RECORDINGS_FAMILY = "recordings";

    private static final String NO_TOKEN = "";
    private static final String REDACTED = "<redacted>";

    public ExternalMcpProperties {
        families = families == null ? Set.of() : Set.copyOf(families);
        token = token == null ? NO_TOKEN : token.strip();
        preset = preset == null ? DEFAULT_PRESET : preset;
        if (!PRESETS.containsKey(preset)) {
            throw new IllegalArgumentException("Unknown MCP preset '" + preset
                    + "'. Use a lowercase preset: all, jfr, heap or hub.");
        }
        Set<String> knownFamilies = knownFamilies();
        if (!knownFamilies.containsAll(families)) {
            throw new IllegalArgumentException("Unknown MCP families: "
                    + families.stream().filter(family -> !knownFamilies.contains(family)).sorted().toList()
                    + ". Family names are lowercase; supported families: "
                    + knownFamilies.stream().sorted().toList());
        }
        List<String> writers = families.stream().filter(WRITER_FAMILIES::contains).sorted().toList();
        if (!writers.isEmpty() && !families.contains(OPERATIONS_FAMILY)) {
            throw new IllegalArgumentException("MCP families " + writers + " write, and the operations "
                    + "family must be selected wherever a writer is: add 'operations' to the families list "
                    + "so their operationIds can be polled with operations_status and stopped with "
                    + "operations_cancel.");
        }
        if (families.contains(HUBS_FAMILY) && !families.contains(RECORDINGS_FAMILY)) {
            throw new IllegalArgumentException("MCP family 'hubs' downloads recordings, and the recordings "
                    + "family must be selected wherever hubs is: add 'recordings' to the families list so a "
                    + "downloaded session can be analysed with recordings_analyzeRecording.");
        }
    }

    /**
     * Every family name a reader may select, by an explicit list or through a preset.
     * <p>
     * The registry, not the gate: a family the assembler builds is served by default whether or not it
     * is named here. What being absent costs is selectability -- no preset can include it and naming it
     * is an error -- which is what {@code McpToolsetAssemblerTest} fails the build over.
     */
    public static Set<String> knownFamilies() {
        return PRESETS.get(DEFAULT_PRESET);
    }

    /** Whether a request has to present {@link #token()} before it is served. */
    public boolean tokenRequired() {
        return !token.isEmpty();
    }

    /** Every component but the token, which would otherwise land in any log that prints the bean. */
    @Override
    public String toString() {
        return "ExternalMcpProperties[enabled=" + enabled
                + ", hubsEnabled=" + hubsEnabled
                + ", ideEnabled=" + ideEnabled
                + ", families=" + families
                + ", preset=" + preset
                + ", trustForwardedHeaders=" + trustForwardedHeaders
                + ", token=" + (tokenRequired() ? REDACTED : NO_TOKEN) + "]";
    }

    /**
     * Whether a family is selected by the explicit allowlist, or by the preset when no allowlist is
     * set. The assembler applies the independent hub and IDE switches before retaining families.
     */
    public boolean advertises(String family) {
        if (families.isEmpty() && DEFAULT_PRESET.equals(preset)) {
            // Everything built, rather than everything listed. The list below is the registry of names
            // a reader may select, so consulting it here too would make a family added to the assembler
            // and forgotten in it disappear from the server with nothing saying so -- the one failure
            // this default is meant not to have.
            return true;
        }
        return (families.isEmpty() ? PRESETS.get(preset) : families).contains(family);
    }
}
