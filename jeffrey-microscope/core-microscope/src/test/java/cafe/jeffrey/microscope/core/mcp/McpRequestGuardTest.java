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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The one check the MCP specification asks of a local HTTP server. A page in the user's browser can
 * post to localhost from any origin, so a server that ignores {@code Origin} can be driven by a site
 * the user merely visited.
 */
class McpRequestGuardTest {

    private static final String SERVER_NAME = "localhost";
    private static final int SERVER_PORT = 8585;

    private final McpRequestGuard guard = new McpRequestGuard();

    private static MockHttpServletRequest request(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName(SERVER_NAME);
        request.setServerPort(SERVER_PORT);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }

    @Nested
    class Served {

        /**
         * A coding agent sends no Origin at all, which is why refusing a foreign one costs Claude Code
         * and Codex nothing.
         */
        @Test
        void servesARequestWithNoOriginAtAll() {
            assertNull(guard.refusalReason(request(null)));
        }

        @Test
        void servesARequestWithABlankOrigin() {
            assertNull(guard.refusalReason(request("  ")));
        }

        @Test
        void servesAPageJeffreyItselfServed() {
            assertNull(guard.refusalReason(request("http://localhost:8585")));
        }

        @Test
        void comparesTheHostCaseInsensitively() {
            assertNull(guard.refusalReason(request("http://LOCALHOST:8585")));
        }
    }

    @Nested
    class Refused {

        @Test
        void refusesASiteTheUserHappenedToBeOn() {
            assertNotNull(guard.refusalReason(request("https://evil.example")));
        }

        /**
         * A different port on the same host is a different origin, and on a developer's machine it is
         * very often a different application.
         */
        @Test
        void refusesTheSameHostOnAnotherPort() {
            assertNotNull(guard.refusalReason(request("http://localhost:3000")));
        }

        /**
         * The loopback address and the name that resolves to it are different origins to a browser,
         * so they are different origins here.
         */
        @Test
        void refusesTheLoopbackAddressWhenJeffreyWasReachedByName() {
            assertNotNull(guard.refusalReason(request("http://127.0.0.1:8585")));
        }

        @Test
        void refusesAnOriginWithNoHost() {
            assertNotNull(guard.refusalReason(request("null")));
        }

        @Test
        void refusesSomethingThatIsNotAUriAtAll() {
            assertNotNull(guard.refusalReason(request("http://[not a uri")));
        }

        /**
         * A default port is the port, so an origin that omits it still has to match.
         */
        @Test
        void refusesAnOriginWhoseDefaultPortIsNotJeffreysPort() {
            assertNotNull(guard.refusalReason(request("http://localhost")));
        }
    }

    /**
     * Jeffrey behind a reverse proxy on 443 is reached without a port, and the page it serves posts
     * back from exactly that origin.
     */
    @Test
    void servesAnOriginOnTheDefaultPortWhenJeffreyIsThere() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("jeffrey.example");
        request.setServerPort(443);
        request.addHeader("Origin", "https://jeffrey.example");

        assertNull(guard.refusalReason(request));
    }
}
