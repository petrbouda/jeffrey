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

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Decides whether one request to the external MCP endpoint may be served.
 * <p>
 * Two checks, and they answer different threats.
 * <p>
 * The first is the one the MCP specification asks of every local HTTP server: a page in the user's
 * browser can post to {@code localhost} from any origin, so a server that ignores {@code Origin} can be
 * driven by a website the user merely visited — DNS rebinding, in the usual telling. A coding agent
 * sends no {@code Origin} header at all, so refusing a foreign one costs Claude Code and Codex nothing
 * and closes the browser path entirely.
 * <p>
 * The second is optional and off by default, because Jeffrey has no authentication anywhere and this
 * endpoint should not be the one place that pretends otherwise. When a token is configured the endpoint
 * requires it, which is what makes a Jeffrey reachable from more than one machine defensible.
 * <p>
 * This is a guard the endpoint consults rather than a servlet filter: the rules are the MCP endpoint's
 * own, and a filter would have to re-derive which requests they apply to.
 */
public final class McpRequestGuard {

    private static final Logger LOG = LoggerFactory.getLogger(McpRequestGuard.class);

    private static final String ORIGIN_HEADER = "Origin";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final String token;

    public McpRequestGuard(String token) {
        this.token = token == null ? "" : token.trim();
    }

    /**
     * @return why the request must be refused, or {@code null} when it may be served
     */
    public String refusalReason(HttpServletRequest request) {
        String origin = request.getHeader(ORIGIN_HEADER);
        if (origin != null && !origin.isBlank() && !isSameHost(origin, request)) {
            LOG.warn("Refused an MCP request from a foreign origin: origin={}", origin);
            return "Cross-origin requests are not accepted by the MCP endpoint.";
        }
        if (!token.isEmpty() && !hasValidToken(request)) {
            LOG.warn("Refused an MCP request with a missing or wrong token: remote_addr={}",
                    request.getRemoteAddr());
            return "A bearer token is required. Jeffrey's Settings page shows the header to send.";
        }
        return null;
    }

    /**
     * Whether the browser that sent this request already had the page Jeffrey serves. Anything Jeffrey
     * itself serves is same-origin; anything else is a site the user happened to be on.
     */
    private static boolean isSameHost(String origin, HttpServletRequest request) {
        try {
            URI originUri = URI.create(origin);
            String host = originUri.getHost();
            if (host == null) {
                return false;
            }
            int originPort = originUri.getPort() == -1 ? defaultPort(originUri.getScheme()) : originUri.getPort();
            return host.equalsIgnoreCase(request.getServerName()) && originPort == request.getServerPort();
        } catch (IllegalArgumentException e) {
            // An origin that is not a URI is not one Jeffrey served.
            return false;
        }
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    /**
     * Compared without short-circuiting, so the answer does not depend on how much of the token matched.
     */
    private boolean hasValidToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] presented = header.substring(BEARER_PREFIX.length()).trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(presented, token.getBytes(StandardCharsets.UTF_8));
    }
}
