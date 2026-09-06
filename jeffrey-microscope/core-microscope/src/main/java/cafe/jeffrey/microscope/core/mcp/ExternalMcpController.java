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

import cafe.jeffrey.profile.mcp.AbstractMcpStreamableHttpController;
import cafe.jeffrey.profile.mcp.McpServerFeatures;
import cafe.jeffrey.shared.common.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * MCP Streamable-HTTP server for an <em>external</em> client — an interactive Claude Code session in
 * the developer's own repository, rather than the headless CLI Jeffrey spawns for itself.
 * <p>
 * One server for the whole installation: the profile is a tool argument rather than a query parameter,
 * so a reader registers this endpoint once and can then move between profiles, and between the JFR,
 * flamegraph, trace and heap-dump families, inside one session. The per-profile, provider-gated
 * endpoint at {@code /api/internal/mcp/claude-code} stays as it is — it serves a different client with
 * different needs, and folding the two together would mean branching on which query parameters happen
 * to be present.
 * <p>
 * Serving is on by default and switched off only through the {@code jeffrey.microscope.mcp.enabled}
 * application property, fixed at wiring time — unlike the sibling controller, whose provider gate is a
 * live setting checked per request. While it is off the endpoint answers 404: a disabled server should
 * look like no server at all, not like one refusing to talk.
 * <p>
 * Every tool it exposes is read-only. There is no authentication yet, so the endpoint carries the same
 * trust assumption as the rest of {@code /api/internal/**}: reachable means trusted. That is why the
 * documentation asks for a loopback bind, an SSH tunnel or a reverse proxy in front of anything wider.
 */
@RestController
@RequestMapping("/api/internal/mcp")
public class ExternalMcpController extends AbstractMcpStreamableHttpController {

    /** Where a refused request is told why, outside the JSON-RPC envelope it never entered. */
    private static final String REFUSAL_FIELD = "error";

    private final McpToolsetAssembler assembler;
    private final ExternalMcpProperties properties;
    private final McpRequestGuard guard;
    private final McpServerFeatures features;

    /**
     * One constructor, deliberately. This class is component-scanned, and Spring picks a constructor
     * to inject only when there is exactly one — a second, shorter convenience overload made every
     * candidate ambiguous, so the container fell back to a no-arg constructor that does not exist and
     * the application failed to start. The prompt registry is a {@code @Bean} in
     * {@code McpConfiguration} rather than built here for the same reason the toolset is: wiring
     * belongs in configuration, where it can be seen.
     */
    public ExternalMcpController(
            McpToolsetAssembler assembler,
            ExternalMcpProperties properties,
            McpRequestGuard guard,
            McpPromptRegistry prompts) {
        this.assembler = assembler;
        this.properties = properties;
        this.guard = guard;
        // Prompts and resources are fixed for the installation the way the toolset is, so they are
        // built once here rather than per request.
        this.features = new McpServerFeatures(
                assembler::toolset,
                () -> prompts,
                () -> new McpResources(assembler.toolset()));
    }

    @PostMapping
    public ResponseEntity<JsonNode> handle(
            @RequestBody JsonNode request,
            HttpServletRequest httpRequest) {
        if (!properties.enabled()) {
            return ResponseEntity.notFound().build();
        }
        String refusal = guard.refusalReason(httpRequest);
        if (refusal != null) {
            // The reason travels with the refusal. A bare 403 from a server that is otherwise
            // answering is the kind of thing somebody debugs a proxy over; the sentence says it was
            // the origin check and nothing else.
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Json.createObject().put(REFUSAL_FIELD, refusal));
        }
        return dispatch(request, features);
    }
}
