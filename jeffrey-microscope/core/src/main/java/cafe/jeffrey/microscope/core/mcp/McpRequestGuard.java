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

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
 * Behind a reverse proxy the servlet sees the proxy's connection rather than the address the client
 * used. With {@code jeffrey.microscope.mcp.trust-forwarded-headers} on, the host comes from the first
 * {@code X-Forwarded-Host} value and the scheme from {@code X-Forwarded-Proto}, for the allowlist and
 * the origin comparison alike. Off by default, because anyone who reaches Jeffrey directly can write
 * those headers.
 * <p>
 * Authentication is optional. With {@code jeffrey.microscope.mcp.token} set, a request must carry
 * {@code Authorization: Bearer <token>}, compared in constant time; without it the header is ignored,
 * so a client manifest can always send one. The host and origin checks run first: a request to the
 * wrong address is a configuration problem the operator fixes with a property, and answering it with
 * 401 would send them looking for a token instead.
 * <p>
 * This is a guard the endpoint consults rather than a servlet filter: the rule is the MCP endpoint's
 * own, and a filter would have to re-derive which requests it applies to.
 */
public final class McpRequestGuard {

    private static final Logger LOG = LoggerFactory.getLogger(McpRequestGuard.class);

    /**
     * The application property that lists the hosts this endpoint answers on. Named in the refusal,
     * because the operator reading a 403 from behind {@code host.docker.internal} or a LAN address
     * needs the property to set, not a description of the check that refused them.
     */
    public static final String ALLOWED_HOSTS_PROPERTY = "jeffrey.microscope.mcp.allowed-hosts";

    /** The application property that makes the guard read the addressed host and scheme from a proxy. */
    public static final String TRUST_FORWARDED_HEADERS_PROPERTY = "jeffrey.microscope.mcp.trust-forwarded-headers";

    /** The application property that holds the bearer token, named in a 401 for the same reason. */
    public static final String TOKEN_PROPERTY = "jeffrey.microscope.mcp.token";

    /** The environment variable the Codex, Gemini and hand-written client configurations read the token from. */
    public static final String TOKEN_ENV_VAR = "JEFFREY_MCP_TOKEN";

    /**
     * The Claude Code plugin's token setting. The plugin's server sends only this setting, never
     * {@link #TOKEN_ENV_VAR}, so a refusal that named the variable alone would send a Claude Code
     * user to set something their client does not read.
     */
    public static final String CLAUDE_CODE_TOKEN_SETTING = "Jeffrey MCP token";

    /** The Codex option that names the variable a server's bearer token is read from. */
    public static final String CODEX_TOKEN_OPTION = "bearer_token_env_var";

    /** Where each client takes the token from, quoted by both 401 texts and by the plugin's hook. */
    public static final String TOKEN_SOURCES =
            "Claude Code: the plugin's " + CLAUDE_CODE_TOKEN_SETTING + " setting (/plugin -> microscope); "
                    + "Codex / Gemini / other clients: " + TOKEN_ENV_VAR + " (Codex: " + CODEX_TOKEN_OPTION + ")";

    private static final String ORIGIN_HEADER = "Origin";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String FORWARDED_HOST_HEADER = "X-Forwarded-Host";
    private static final String FORWARDED_PROTO_HEADER = "X-Forwarded-Proto";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String FORWARDED_VALUE_SEPARATOR = ",";
    private static final String AUTHORITY_URI_PREFIX = "//";
    private static final String DEFAULT_ALLOWED_HOSTS_VALUE = "localhost,127.0.0.1,::1";
    private static final String NO_TOKEN = "";
    private static final String HTTPS = "https";
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;
    private static final int NO_PORT = -1;
    private static final Set<String> HTTP_SCHEMES = Set.of("http", HTTPS);
    private static final String UNTRUSTED_HOST_REASON_FORMAT =
            "Requests to an untrusted host are not accepted by the MCP endpoint: the request Host '%s' is not "
                    + "listed in " + ALLOWED_HOSTS_PROPERTY + ". Set that property to a comma-separated list "
                    + "that includes it (the default is " + DEFAULT_ALLOWED_HOSTS_VALUE + ") and restart Jeffrey.";
    private static final String CROSS_ORIGIN_REASON =
            "Cross-origin requests are not accepted by the MCP endpoint.";
    private static final String MISSING_TOKEN_REASON =
            "The MCP endpoint requires a bearer token (" + TOKEN_PROPERTY + " is set): send "
                    + "'Authorization: Bearer <token>'. Set the configured token where your client reads it: "
                    + TOKEN_SOURCES + ".";
    private static final String WRONG_TOKEN_REASON =
            "The bearer token does not match " + TOKEN_PROPERTY + ". Set the token this Jeffrey is configured "
                    + "with where your client reads it: " + TOKEN_SOURCES + ".";

    private final Set<String> allowedHosts;
    private final boolean trustForwardedHeaders;
    private final byte[] token;

    /**
     * Why a request was refused, and the HTTP status that says so. Two kinds because a client acts on
     * them differently: a 401 is fixed by the token it sends, a 403 by the server's configuration.
     */
    public sealed interface Refusal {

        int status();

        String message();

        /** The request presented no token, or the wrong one. */
        record Unauthorized(String message) implements Refusal {

            private static final int STATUS = 401;

            public Unauthorized {
                Objects.requireNonNull(message, "message");
            }

            @Override
            public int status() {
                return STATUS;
            }
        }

        /** The request reached an address, or came from an origin, this endpoint does not serve. */
        record Forbidden(String message) implements Refusal {

            private static final int STATUS = 403;

            public Forbidden {
                Objects.requireNonNull(message, "message");
            }

            @Override
            public int status() {
                return STATUS;
            }
        }
    }

    /** The scheme, host and port the client addressed, from the servlet or from trusted proxy headers. */
    private record Authority(String scheme, String host, int port) {
    }

    /**
     * @param allowedHosts          the hosts the endpoint answers on
     * @param trustForwardedHeaders whether the addressed host and scheme come from {@code X-Forwarded-*}
     * @param token                 the bearer token every request must present; blank requires none
     */
    public McpRequestGuard(Set<String> allowedHosts, boolean trustForwardedHeaders, String token) {
        Objects.requireNonNull(allowedHosts, "allowedHosts");
        this.allowedHosts = allowedHosts.stream()
                .filter(Objects::nonNull)
                .map(McpRequestGuard::normalizeHost)
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.token = (token == null ? NO_TOKEN : token.strip()).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @return why the request must be refused, or {@code null} when it may be served
     */
    public Refusal refusalReason(HttpServletRequest request) {
        Authority authority = addressedAuthority(request);
        if (!allowedHosts.contains(normalizeHost(authority.host()))) {
            LOG.warn("Refused an MCP request to an untrusted host: host={} property={}",
                    authority.host(), ALLOWED_HOSTS_PROPERTY);
            return new Refusal.Forbidden(UNTRUSTED_HOST_REASON_FORMAT.formatted(authority.host()));
        }

        String origin = request.getHeader(ORIGIN_HEADER);
        if (origin != null && !isSameOrigin(origin, authority)) {
            LOG.warn("Refused an MCP request from a foreign origin: origin={}", origin);
            return new Refusal.Forbidden(CROSS_ORIGIN_REASON);
        }

        if (token.length > 0) {
            return tokenRefusal(request.getHeader(AUTHORIZATION_HEADER));
        }
        return null;
    }

    private Refusal tokenRefusal(String authorization) {
        if (authorization == null || !startsWithIgnoringCase(authorization, BEARER_PREFIX)) {
            LOG.warn("Refused an MCP request without a bearer token: property={}", TOKEN_PROPERTY);
            return new Refusal.Unauthorized(MISSING_TOKEN_REASON);
        }
        byte[] presented = authorization.substring(BEARER_PREFIX.length()).strip()
                .getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(presented, token)) {
            LOG.warn("Refused an MCP request with a mismatched bearer token: property={}", TOKEN_PROPERTY);
            return new Refusal.Unauthorized(WRONG_TOKEN_REASON);
        }
        return null;
    }

    /**
     * The authority the client typed. The servlet's own unless forwarded headers are trusted and the
     * proxy supplied them; a header the proxy left out falls back to what the servlet saw.
     */
    private Authority addressedAuthority(HttpServletRequest request) {
        String scheme = normalizeScheme(request.getScheme());
        if (!trustForwardedHeaders) {
            return new Authority(scheme, request.getServerName(), request.getServerPort());
        }
        String forwardedProto = firstValue(request.getHeader(FORWARDED_PROTO_HEADER));
        if (forwardedProto != null) {
            scheme = normalizeScheme(forwardedProto);
        }
        String forwardedHost = firstValue(request.getHeader(FORWARDED_HOST_HEADER));
        if (forwardedHost == null) {
            return new Authority(scheme, request.getServerName(), request.getServerPort());
        }
        return forwardedAuthority(scheme, forwardedHost);
    }

    /**
     * A forwarded host may carry a port; without one the client used the scheme's default port. A
     * value that is not an authority at all is kept whole, so the allowlist refuses it by name.
     */
    private static Authority forwardedAuthority(String scheme, String forwardedHost) {
        try {
            URI uri = URI.create(AUTHORITY_URI_PREFIX + forwardedHost);
            if (uri.getHost() == null) {
                return new Authority(scheme, forwardedHost, NO_PORT);
            }
            int port = uri.getPort() == NO_PORT ? defaultPort(scheme) : uri.getPort();
            return new Authority(scheme, uri.getHost(), port);
        } catch (IllegalArgumentException e) {
            return new Authority(scheme, forwardedHost, NO_PORT);
        }
    }

    private static String firstValue(String header) {
        if (header == null) {
            return null;
        }
        String first = header.split(FORWARDED_VALUE_SEPARATOR, 2)[0].strip();
        return first.isEmpty() ? null : first;
    }

    /**
     * Whether the browser that sent this request already had the page Jeffrey serves. Anything Jeffrey
     * itself serves is same-origin; anything else is a site the user happened to be on.
     */
    private static boolean isSameOrigin(String origin, Authority authority) {
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
            int originPort = originUri.getPort() == NO_PORT ? defaultPort(scheme) : originUri.getPort();
            return scheme.equals(authority.scheme())
                    && normalizeHost(host).equals(normalizeHost(authority.host()))
                    && originPort == authority.port();
        } catch (IllegalArgumentException e) {
            // An origin that is not a URI is not one Jeffrey served.
            return false;
        }
    }

    private static boolean startsWithIgnoringCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String normalizeScheme(String scheme) {
        return scheme == null ? "" : scheme.strip().toLowerCase(Locale.ROOT);
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
        return HTTPS.equals(scheme) ? HTTPS_PORT : HTTP_PORT;
    }
}
