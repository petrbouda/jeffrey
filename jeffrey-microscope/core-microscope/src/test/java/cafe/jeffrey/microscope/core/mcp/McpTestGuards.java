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

import java.util.Set;

/**
 * {@link McpRequestGuard}s for a test that checks the host allowlist only: no forwarded headers
 * trusted and no token required.
 */
public final class McpTestGuards {

    /** The hosts {@code jeffrey.microscope.mcp.allowed-hosts} allows by default. */
    public static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    private static final boolean TRUST_FORWARDED_HEADERS = false;
    private static final String NO_TOKEN = "";

    private McpTestGuards() {
    }

    /** What production answers on with the default allowlist. */
    public static McpRequestGuard loopback() {
        return allowing(LOOPBACK_HOSTS);
    }

    public static McpRequestGuard allowing(Set<String> allowedHosts) {
        return new McpRequestGuard(allowedHosts, TRUST_FORWARDED_HEADERS, NO_TOKEN);
    }
}
