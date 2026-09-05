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

/**
 * Decides whether one request to the external MCP endpoint may be served.
 * <p>
 * One check, and it is the one the MCP specification asks of every local HTTP server: a page in the
 * user's browser can post to {@code localhost} from any origin, so a server that ignores
 * {@code Origin} can be driven by a website the user merely visited — DNS rebinding, in the usual
 * telling. A coding agent sends no {@code Origin} header at all, so refusing a foreign one costs
 * Claude Code and Codex nothing and closes the browser path entirely.
 * <p>
 * It is not authentication and does not stand in for any. The endpoint has none, in common with
 * everything else under {@code /api/internal}: what decides who may reach it is the address Jeffrey
 * binds to and whatever sits in front of it, not this class.
 * <p>
 * This is a guard the endpoint consults rather than a servlet filter: the rule is the MCP endpoint's
 * own, and a filter would have to re-derive which requests it applies to.
 */
public final class McpRequestGuard {

    private static final Logger LOG = LoggerFactory.getLogger(McpRequestGuard.class);

    private static final String ORIGIN_HEADER = "Origin";

    /**
     * @return why the request must be refused, or {@code null} when it may be served
     */
    public String refusalReason(HttpServletRequest request) {
        String origin = request.getHeader(ORIGIN_HEADER);
        if (origin != null && !origin.isBlank() && !isSameHost(origin, request)) {
            LOG.warn("Refused an MCP request from a foreign origin: origin={}", origin);
            return "Cross-origin requests are not accepted by the MCP endpoint.";
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
}
