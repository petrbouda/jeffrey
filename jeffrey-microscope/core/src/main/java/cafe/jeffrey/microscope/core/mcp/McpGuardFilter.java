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

import cafe.jeffrey.microscope.core.mcp.McpRequestGuard.Refusal;
import cafe.jeffrey.microscope.core.utils.Json;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs Jeffrey's {@link McpRequestGuard} and the {@code jeffrey.microscope.mcp.enabled} toggle in front
 * of the modern MCP server's router. The modern server is driven by a standard library with no Jeffrey
 * controller to consult the guard, so the host/origin/bearer rule moves into a servlet filter on
 * {@code /api/mcp} — the same checks, the same 404/401/403 shapes and reason bodies the hand-rolled
 * {@code ExternalMcpController} produced, in front of the SDK's transport.
 * <p>
 * The SDK's own {@code ServerHttpHeaderValidator} is left as {@code NOOP} (a bean in
 * {@code McpConfiguration}) so there is one authority for these checks, not two.
 */
public final class McpGuardFilter extends OncePerRequestFilter {

    private static final String REFUSAL_FIELD = "error";
    private static final String BEARER_CHALLENGE = "Bearer";

    private final ExternalMcpProperties properties;
    private final McpRequestGuard guard;

    public McpGuardFilter(ExternalMcpProperties properties, McpRequestGuard guard) {
        this.properties = properties;
        this.guard = guard;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // A disabled server looks like no server at all, not like one refusing to talk.
        if (!properties.enabled()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Refusal refusal = guard.refusalReason(request);
        if (refusal == null) {
            chain.doFilter(request, response);
            return;
        }
        // The reason travels with the refusal, naming the check and the property/variable that fixes it.
        response.setStatus(refusal.status());
        if (refusal instanceof Refusal.Unauthorized) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(Json.toString(Json.createObject().put(REFUSAL_FIELD, refusal.message())));
    }
}
