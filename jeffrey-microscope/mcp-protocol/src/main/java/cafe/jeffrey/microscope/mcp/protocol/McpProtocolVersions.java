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

package cafe.jeffrey.microscope.mcp.protocol;

import java.util.List;
import java.util.Set;

/**
 * The MCP revisions this server implements: only the stateless {@code 2026-07-28}. Every request
 * carries its own version in {@code _meta}; there is no {@code initialize}, no session and no fallback
 * to a handshake revision.
 */
public final class McpProtocolVersions {

    /** The revisions this server serves, in the order a refusal names them. */
    public static final List<String> SUPPORTED = List.of("2026-07-28");

    private static final Set<String> SUPPORTED_SET = Set.copyOf(SUPPORTED);

    private McpProtocolVersions() {
    }

    public static boolean isSupported(String version) {
        return version != null && SUPPORTED_SET.contains(version);
    }
}
