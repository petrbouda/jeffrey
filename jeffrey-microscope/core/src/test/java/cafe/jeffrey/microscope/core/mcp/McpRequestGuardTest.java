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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import cafe.jeffrey.microscope.core.mcp.McpRequestGuard.Refusal;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one check the MCP specification asks of a local HTTP server. A page in the user's browser can
 * post to localhost from any origin, so a server that ignores {@code Origin} can be driven by a site
 * the user merely visited.
 */
class McpRequestGuardTest {

    private static final String SERVER_NAME = "localhost";
    private static final int SERVER_PORT = 8585;

    private static final String PUBLIC_HOST = "jeffrey.example";
    private static final String BACKEND_ADDRESS = "127.0.0.1";
    private static final String FORWARDED_HOST = "X-Forwarded-Host";
    private static final String FORWARDED_PROTO = "X-Forwarded-Proto";
    private static final String AUTHORIZATION = "Authorization";
    private static final String TOKEN = "s3cret-token";
    private static final String NO_TOKEN = "";

    private final McpRequestGuard guard = McpTestGuards.loopback();

    private static MockHttpServletRequest request(String origin) {
        return request("http", SERVER_NAME, SERVER_PORT, origin);
    }

    private static MockHttpServletRequest request(String scheme, String serverName, int serverPort, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme(scheme);
        request.setServerName(serverName);
        request.setServerPort(serverPort);
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
        void servesAPageJeffreyItselfServed() {
            assertNull(guard.refusalReason(request("http://localhost:8585")));
        }

        @Test
        void comparesTheHostCaseInsensitively() {
            assertNull(guard.refusalReason(request("http://LOCALHOST:8585")));
        }

        @Test
        void servesTheIpv6LoopbackAddress() {
            assertNull(guard.refusalReason(request("http", "[::1]", SERVER_PORT, "http://[::1]:8585")));
        }

        @Test
        void servesAConfiguredRemoteHost() {
            McpRequestGuard remoteGuard = McpTestGuards.allowing(Set.of("jeffrey.example"));

            assertNull(remoteGuard.refusalReason(
                    request("https", "jeffrey.example", 443, "https://jeffrey.example")));
        }
    }

    @Nested
    class Refused {

        @Test
        void refusesASiteTheUserHappenedToBeOn() {
            assertNotNull(guard.refusalReason(request("https://evil.example")));
        }

        @Test
        void refusesARequestWhoseUntrustedHostMatchesItsOrigin() {
            assertNotNull(guard.refusalReason(
                    request("http", "audit.invalid", SERVER_PORT, "http://audit.invalid:8585")));
        }

        @Test
        void refusesARequestWithNoOriginWhenItsHostIsUntrusted() {
            assertNotNull(guard.refusalReason(request("http", "audit.invalid", SERVER_PORT, null)));
        }

        /**
         * An agent reaching Jeffrey through {@code host.docker.internal} or a LAN address gets this
         * refusal, and the fix is one property. The sentence has to name the property and the host
         * that was refused, so it can be fixed without the docs.
         */
        @Test
        void namesThePropertyAndTheHostWhenRefusingAnUntrustedHost() {
            Refusal refusal = guard.refusalReason(request("http", "host.docker.internal", SERVER_PORT, null));

            assertInstanceOf(Refusal.Forbidden.class, refusal);
            assertEquals(403, refusal.status());
            assertTrue(refusal.message().contains(McpRequestGuard.ALLOWED_HOSTS_PROPERTY), refusal.message());
            assertTrue(refusal.message().contains("host.docker.internal"), refusal.message());
        }

        /**
         * The origin refusal is a different finding — a browser page Jeffrey did not serve — and
         * pointing its reader at the host allow-list would send them to widen the wrong thing.
         */
        @Test
        void keepsTheOriginRefusalApartFromTheHostOne() {
            Refusal refusal = guard.refusalReason(request("https://evil.example"));

            assertInstanceOf(Refusal.Forbidden.class, refusal);
            assertFalse(refusal.message().contains(McpRequestGuard.ALLOWED_HOSTS_PROPERTY), refusal.message());
        }

        @Test
        void doesNotTrustForwardedHeadersDirectly() {
            MockHttpServletRequest request = request(
                    "http", "audit.invalid", SERVER_PORT, "http://localhost:8585");
            request.addHeader("X-Forwarded-Host", "localhost:8585");
            request.addHeader("X-Forwarded-Proto", "http");

            assertNotNull(guard.refusalReason(request));
        }

        /**
         * A different port on the same host is a different origin, and on a developer's machine it is
         * very often a different application.
         */
        @Test
        void refusesTheSameHostOnAnotherPort() {
            assertNotNull(guard.refusalReason(request("http://localhost:3000")));
        }

        @Test
        void refusesTheSameHostAndPortWithAnotherScheme() {
            assertNotNull(guard.refusalReason(
                    request("https", SERVER_NAME, SERVER_PORT, "http://localhost:8585")));
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

        @ParameterizedTest
        @ValueSource(strings = {
                " ",
                "http://user@localhost:8585",
                "http://localhost:8585/path",
                "http://localhost:8585?query",
                "http://localhost:8585#fragment"
        })
        void refusesAnOriginThatIsNotAPlainOrigin(String origin) {
            assertNotNull(guard.refusalReason(request(origin)));
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
        McpRequestGuard remoteGuard = McpTestGuards.allowing(Set.of("jeffrey.example"));
        MockHttpServletRequest request = request(
                "https", "jeffrey.example", 443, "https://jeffrey.example");

        assertNull(remoteGuard.refusalReason(request));
    }

    /**
     * Behind a reverse proxy the servlet sees the proxy's connection to the backend, not the address
     * the client used. With forwarded headers trusted, the authority the client typed is the one the
     * allowlist and the origin comparison judge.
     */
    @Nested
    class ForwardedHeaders {

        private final McpRequestGuard trusting = new McpRequestGuard(Set.of(PUBLIC_HOST), true, NO_TOKEN);

        private MockHttpServletRequest proxied(String forwardedHost, String forwardedProto, String origin) {
            MockHttpServletRequest request = request("http", BACKEND_ADDRESS, SERVER_PORT, origin);
            if (forwardedHost != null) {
                request.addHeader(FORWARDED_HOST, forwardedHost);
            }
            if (forwardedProto != null) {
                request.addHeader(FORWARDED_PROTO, forwardedProto);
            }
            return request;
        }

        @Test
        void judgesTheForwardedHostAgainstTheAllowlist() {
            assertNull(trusting.refusalReason(proxied(PUBLIC_HOST, "https", null)));
        }

        @Test
        void comparesTheOriginWithTheForwardedSchemeHostAndDefaultPort() {
            assertNull(trusting.refusalReason(proxied(PUBLIC_HOST, "https", "https://jeffrey.example")));
        }

        @Test
        void takesThePortTheForwardedHostCarries() {
            assertNull(trusting.refusalReason(
                    proxied("jeffrey.example:8443", "https", "https://jeffrey.example:8443")));
        }

        /**
         * A chain of proxies appends one value per hop; the first is the one the client sent.
         */
        @Test
        void takesTheFirstForwardedHostOfAChain() {
            assertNull(trusting.refusalReason(proxied("jeffrey.example, internal.proxy", "https", null)));
        }

        /**
         * Only the first hop counts: an allowed name later in the chain does not rescue a client that
         * dialled a host outside the list.
         */
        @Test
        void refusesAChainWhoseFirstForwardedHostIsUntrusted() {
            assertInstanceOf(Refusal.Forbidden.class,
                    trusting.refusalReason(proxied("evil.example, jeffrey.example", "https", null)));
        }

        @Test
        void refusesAForwardedHostOutsideTheAllowlistAndNamesIt() {
            Refusal refusal = trusting.refusalReason(proxied("evil.example", "https", null));

            assertInstanceOf(Refusal.Forbidden.class, refusal);
            assertTrue(refusal.message().contains("evil.example"), refusal.message());
        }

        @Test
        void refusesAnOriginWhoseSchemeDiffersFromTheForwardedOne() {
            assertNotNull(trusting.refusalReason(proxied(PUBLIC_HOST, "https", "http://jeffrey.example")));
        }

        @Test
        void fallsBackToTheServletAuthorityWithoutAForwardedHost() {
            McpRequestGuard loopbackTrusting = new McpRequestGuard(Set.of(BACKEND_ADDRESS), true, NO_TOKEN);

            assertNull(loopbackTrusting.refusalReason(proxied(null, null, null)));
        }

        @Test
        void ignoresForwardedHeadersUnlessTrusted() {
            McpRequestGuard notTrusting = new McpRequestGuard(Set.of(PUBLIC_HOST), false, NO_TOKEN);

            assertInstanceOf(Refusal.Forbidden.class,
                    notTrusting.refusalReason(proxied(PUBLIC_HOST, "https", null)));
        }

        @Test
        void aLoopbackRequestIsNotRefusedOverAForwardedHeaderItDoesNotTrust() {
            MockHttpServletRequest request = request(null);
            request.addHeader(FORWARDED_HOST, "evil.example");

            assertNull(guard.refusalReason(request));
        }
    }

    /**
     * An optional shared secret for a Jeffrey reachable from more than this machine. Claude Code,
     * Codex and Gemini can all send a bearer token, so the check is the header they already know.
     */
    @Nested
    class BearerToken {

        private final McpRequestGuard guarded = new McpRequestGuard(Set.of(SERVER_NAME), false, TOKEN);

        private MockHttpServletRequest authorized(String authorization) {
            MockHttpServletRequest request = request(null);
            if (authorization != null) {
                request.addHeader(AUTHORIZATION, authorization);
            }
            return request;
        }

        @Test
        void servesTheMatchingToken() {
            assertNull(guarded.refusalReason(authorized("Bearer " + TOKEN)));
        }

        /**
         * The authentication scheme is case-insensitive (RFC 9110); the token itself is not.
         */
        @Test
        void acceptsTheSchemeInAnyCase() {
            assertNull(guarded.refusalReason(authorized("bearer " + TOKEN)));
        }

        @Test
        void refusesAMismatchedTokenWith401() {
            Refusal refusal = guarded.refusalReason(authorized("Bearer wrong"));

            assertInstanceOf(Refusal.Unauthorized.class, refusal);
            assertEquals(401, refusal.status());
            assertFalse(refusal.message().contains(TOKEN), refusal.message());
            assertNamesEveryClientsTokenSource(refusal);
        }

        @Test
        void refusesATokenThatDiffersOnlyInCase() {
            assertInstanceOf(Refusal.Unauthorized.class,
                    guarded.refusalReason(authorized("Bearer " + TOKEN.toUpperCase())));
        }

        @Test
        void refusesAnAbsentTokenAndSaysWhatToSet() {
            Refusal refusal = guarded.refusalReason(authorized(null));

            assertInstanceOf(Refusal.Unauthorized.class, refusal);
            assertTrue(refusal.message().contains(McpRequestGuard.TOKEN_PROPERTY), refusal.message());
            assertNamesEveryClientsTokenSource(refusal);
        }

        /**
         * The Claude Code plugin sends its own setting and never reads the variable, so a text that
         * named only {@code JEFFREY_MCP_TOKEN} sent Claude Code users to set something nothing reads.
         */
        private void assertNamesEveryClientsTokenSource(Refusal refusal) {
            assertTrue(refusal.message().contains(McpRequestGuard.TOKEN_SOURCES), refusal.message());
            assertTrue(refusal.message().contains("Claude Code: the plugin's Jeffrey MCP token setting"),
                    refusal.message());
            assertTrue(refusal.message().contains(McpRequestGuard.TOKEN_ENV_VAR), refusal.message());
            assertTrue(refusal.message().contains("bearer_token_env_var"), refusal.message());
        }

        @Test
        void refusesAnotherAuthenticationScheme() {
            assertInstanceOf(Refusal.Unauthorized.class, guarded.refusalReason(authorized("Basic " + TOKEN)));
        }

        /**
         * The manifests always send the header, with an empty value when the reader set no token, so a
         * server without a token has to ignore it rather than refuse it.
         */
        @ParameterizedTest
        @ValueSource(strings = {"Bearer ", "Bearer", "Bearer anything", "Basic xyz"})
        void ignoresAnyAuthorizationWhenNoTokenIsConfigured(String authorization) {
            assertNull(guard.refusalReason(authorized(authorization)));
        }

        /**
         * A request to the wrong host is a configuration problem the operator fixes with a property;
         * answering it with 401 would send them looking for a token instead.
         */
        @Test
        void checksTheHostBeforeTheToken() {
            Refusal refusal = guarded.refusalReason(request("http", "audit.invalid", SERVER_PORT, null));

            assertInstanceOf(Refusal.Forbidden.class, refusal);
        }
    }
}
