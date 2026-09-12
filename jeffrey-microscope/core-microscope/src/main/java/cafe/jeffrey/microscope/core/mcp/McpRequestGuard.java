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
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides whether one request to the external MCP endpoint may be served.
 * <p>
 * The trusted-host check prevents the caller from choosing both sides of the origin comparison with
 * a forged {@code Host} header. The origin check is the one the MCP specification asks of every local
 * HTTP server: a page in the user's browser can post to {@code localhost} from any origin, so a server
 * that ignores {@code Origin} can be driven by a website the user merely visited — DNS rebinding, in
 * the usual telling. A coding agent sends no {@code Origin} header at all, so refusing a foreign one
 * costs every coding agent nothing and closes the browser path entirely.
 * <p>
 * It is not authentication and does not stand in for any. The endpoint has none, in common with
 * everything else under {@code /api/internal}: what decides who may reach it is the address Jeffrey
 * binds to, this class's hostname allowlist, and whatever sits in front of it.
 * <p>
 * This is a guard the endpoint consults rather than a servlet filter: the rule is the MCP endpoint's
 * own, and a filter would have to re-derive which requests it applies to.
 */
public final class McpRequestGuard {

    private static final Logger LOG = LoggerFactory.getLogger(McpRequestGuard.class);

    private static final String ORIGIN_HEADER = "Origin";
    private static final Set<String> DEFAULT_ALLOWED_HOSTS = Set.of("localhost", "127.0.0.1", "::1");
    private static final Set<String> HTTP_SCHEMES = Set.of("http", "https");
    private static final String UNTRUSTED_HOST_REASON =
            "Requests to an untrusted host are not accepted by the MCP endpoint.";
    private static final String CROSS_ORIGIN_REASON =
            "Cross-origin requests are not accepted by the MCP endpoint.";

    private final Set<String> allowedHosts;

    public McpRequestGuard() {
        this(DEFAULT_ALLOWED_HOSTS);
    }

    public McpRequestGuard(Set<String> allowedHosts) {
        Objects.requireNonNull(allowedHosts, "allowedHosts");
        this.allowedHosts = allowedHosts.stream()
                .filter(Objects::nonNull)
                .map(McpRequestGuard::normalizeHost)
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @return why the request must be refused, or {@code null} when it may be served
     */
    public String refusalReason(HttpServletRequest request) {
        String requestHost = normalizeHost(request.getServerName());
        if (!allowedHosts.contains(requestHost)) {
            LOG.warn("Refused an MCP request to an untrusted host: host={}", request.getServerName());
            return UNTRUSTED_HOST_REASON;
        }

        String origin = request.getHeader(ORIGIN_HEADER);
        if (origin != null && !isSameOrigin(origin, request)) {
            LOG.warn("Refused an MCP request from a foreign origin: origin={}", origin);
            return CROSS_ORIGIN_REASON;
        }
        return null;
    }

    /**
     * Whether the browser that sent this request already had the page Jeffrey serves. Anything Jeffrey
     * itself serves is same-origin; anything else is a site the user happened to be on.
     */
    private static boolean isSameOrigin(String origin, HttpServletRequest request) {
        try {
            URI originUri = URI.create(origin);
            String scheme = normalizeScheme(originUri.getScheme());
            String host = originUri.getHost();
            if (!HTTP_SCHEMES.contains(scheme)
                    || host == null
                    || originUri.getRawUserInfo() != null
                    || hasText(originUri.getRawPath())
                    || originUri.getRawQuery() != null
                    || originUri.getRawFragment() != null) {
                return false;
            }
            int originPort = originUri.getPort() == -1 ? defaultPort(scheme) : originUri.getPort();
            return scheme.equals(normalizeScheme(request.getScheme()))
                    && normalizeHost(host).equals(normalizeHost(request.getServerName()))
                    && originPort == request.getServerPort();
        } catch (IllegalArgumentException e) {
            // An origin that is not a URI is not one Jeffrey served.
            return false;
        }
    }

    private static String normalizeScheme(String scheme) {
        return scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private static int defaultPort(String scheme) {
        return "https".equals(scheme) ? 443 : 80;
    }
}
