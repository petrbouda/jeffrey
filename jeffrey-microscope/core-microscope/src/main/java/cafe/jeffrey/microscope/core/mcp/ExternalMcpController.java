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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * MCP Streamable-HTTP server for an <em>external</em> client — an interactive Claude Code, Codex or
 * Gemini CLI session in the developer's own repository. Jeffrey's only MCP endpoint, and its only AI integration:
 * Jeffrey never calls a model itself, the client brings one and calls in.
 * <p>
 * One server for the whole installation: the profile is a tool argument rather than a query parameter,
 * so a reader registers this endpoint once and can then move between profiles, and between the JFR,
 * flamegraph, trace and heap-dump families, inside one session.
 * <p>
 * It sits at {@code /api/mcp} rather than under {@code /api/internal/**} with the rest of the HTTP
 * API, because that prefix means "the frontend's own API" — the SPA calling the server that served
 * it — and this is the one endpoint whose caller is a different program on the other side of the
 * network. The prefix was never a network boundary and moving it grants nobody access they did not
 * have, but a path that says "internal" while documentation asks the reader to point an external
 * agent at it is a name that has to be read past. {@link #LEGACY_PATH} keeps answering so a client
 * configured against the old address keeps working.
 * <p>
 * Serving is on by default and switched off only through the {@code jeffrey.microscope.mcp.enabled}
 * application property, fixed at wiring time. While it is off the endpoint answers 404: a disabled server should
 * look like no server at all, not like one refusing to talk.
 * <p>
 * Almost every tool it exposes reads. Four families do not — importing a recording, building a heap
 * index, pulling a recording off a hub, and acting on the developer's editor — and none of them changes
 * an analysed profile; each says what it does through its {@code readOnlyHint}, and the two that reach
 * outside this server have switches of their own. There is no authentication yet, so the endpoint
 * carries the same trust assumption as the rest of Jeffrey's HTTP API: reachable means trusted.
 * That is why the documentation asks for a loopback bind, an SSH tunnel or a reverse proxy in front of
 * anything wider.
 */
@RestController
@RequestMapping({ExternalMcpController.PATH, ExternalMcpController.LEGACY_PATH})
public class ExternalMcpController extends AbstractMcpStreamableHttpController {

    /** Where the endpoint lives, and the address every manifest and documentation page spells. */
    public static final String PATH = "/api/mcp";

    /**
     * Where it lived while it was named after the prefix the frontend's API uses. Kept mapped because
     * the address is written down outside this repository — in each client's own configuration — so a
     * plugin nobody has updated, or a hand-registered server, would otherwise stop finding the one it
     * was pointed at.
     */
    public static final String LEGACY_PATH = "/api/internal/mcp";

    /** Where a refused request is told why, outside the JSON-RPC envelope it never entered. */
    private static final String REFUSAL_FIELD = "error";

    /**
     * The revision the client settled on at {@code initialize}, which the specification asks it to
     * repeat on every later request. Passed to the envelope, which refuses one it does not implement.
     */
    private static final String PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";

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
                () -> new McpResources(assembler.toolset(), properties));
    }

    @PostMapping
    public ResponseEntity<JsonNode> handle(
            @RequestBody JsonNode request,
            HttpServletRequest httpRequest) {
        ResponseEntity<JsonNode> refusal = refuseRequest(httpRequest);
        if (refusal != null) {
            return refusal;
        }
        return dispatch(request, httpRequest.getHeader(PROTOCOL_VERSION_HEADER), features);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<JsonNode> malformedJson(HttpServletRequest httpRequest) {
        ResponseEntity<JsonNode> refusal = refuseRequest(httpRequest);
        return refusal == null ? parseErrorResponse() : refusal;
    }

    private ResponseEntity<JsonNode> refuseRequest(HttpServletRequest httpRequest) {
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
        return null;
    }
}
