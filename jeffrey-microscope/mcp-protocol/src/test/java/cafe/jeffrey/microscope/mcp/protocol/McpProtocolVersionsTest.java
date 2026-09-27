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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpProtocolVersionsTest {

    /** The server speaks the stateless revision and nothing older. */
    @Test
    void supportsOnlyTheStatelessRevision() {
        assertEquals(List.of("2026-07-28"), McpProtocolVersions.SUPPORTED);
    }

    @Test
    void acceptsTheSupportedRevision() {
        assertTrue(McpProtocolVersions.isSupported("2026-07-28"));
    }

    /** A handshake revision is no longer served: there is no fallback to {@code initialize}. */
    @Test
    void refusesHandshakeRevisions() {
        assertFalse(McpProtocolVersions.isSupported("2024-11-05"));
        assertFalse(McpProtocolVersions.isSupported("2025-03-26"));
        assertFalse(McpProtocolVersions.isSupported("2025-06-18"));
        assertFalse(McpProtocolVersions.isSupported("2025-11-25"));
    }

    @Test
    void refusesAnUnknownOrAbsentRevision() {
        assertFalse(McpProtocolVersions.isSupported("2099-01-01"));
        assertFalse(McpProtocolVersions.isSupported(""));
        assertFalse(McpProtocolVersions.isSupported(null));
    }
}
