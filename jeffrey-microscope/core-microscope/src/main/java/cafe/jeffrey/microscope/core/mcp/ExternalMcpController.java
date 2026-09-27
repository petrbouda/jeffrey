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
import cafe.jeffrey.microscope.mcp.protocol.McpCompletionProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpServerFeatures;
import cafe.jeffrey.microscope.mcp.protocol.McpSkillProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpTransportHeaders;
import cafe.jeffrey.profile.mcp.AbstractMcpStreamableHttpController;
import cafe.jeffrey.shared.common.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.function.Supplier;

/**
 * MCP Streamable-HTTP server for an <em>external</em> client — an interactive coding-agent session in the
 * developer's own repository, from any client that speaks MCP {@code 2026-07-28}: Claude Code on its v2 MCP
 * runtime, or Codex from v0.147.0 with its global {@code mcp_2026_07_28} feature flag on. A handshake-era
 * client (Gemini CLI today) is refused with {@code -32602} naming the version. Jeffrey's only MCP endpoint,
 * and its only AI integration: Jeffrey never calls a model itself, the client brings one and calls in.
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
 * agent at it is a name that has to be read past. It is the only address: the old one is not served.
 * <p>
 * Serving is on by default and switched off only through the {@code jeffrey.microscope.mcp.enabled}
 * application property, fixed at wiring time. While it is off the endpoint answers 404: a disabled server should
 * look like no server at all, not like one refusing to talk.
 * <p>
 * Almost every tool it exposes reads. Eleven tools in six families do not — importing or deleting a
 * recording, building a heap index, pulling a recording or an artifact off a hub, cancelling a running
 * operation, acting on the developer's editor, and the two slow reads that fill a cache
 * ({@code jvm_autoAnalysis} with {@code compute}, {@code heap_oql} with {@code includeRetainedSize}) —
 * and none of them changes an analysed profile; each says what it does through its
 * {@code readOnlyHint}, and the two families that reach outside this server have switches of their
 * own. Authentication is optional: without {@code jeffrey.microscope.mcp.token} the endpoint carries
 * the same trust assumption as the rest of Jeffrey's HTTP API -- reachable means trusted -- which is
 * why the documentation asks for a loopback bind, an SSH tunnel or a reverse proxy in front of
 * anything wider; with it, a request without the bearer token is answered 401.
 */
@RestController
@RequestMapping(ExternalMcpController.PATH)
public class ExternalMcpController extends AbstractMcpStreamableHttpController {

    /** Where the endpoint lives, and the address every manifest and documentation page spells. */
    public static final String PATH = "/api/mcp";

    /** Where a refused request is told why, outside the JSON-RPC envelope it never entered. */
    private static final String REFUSAL_FIELD = "error";

    /** The challenge a 401 carries, naming the one scheme the endpoint accepts. */
    private static final String BEARER_CHALLENGE = "Bearer";

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
            McpPromptRegistry prompts,
            McpDiagnostics diagnostics,
            AdvertisedFamilies advertised,
            McpTaskProvider tasks,
            McpSkillProvider skills) {
        this.assembler = assembler;
        this.properties = properties;
        this.guard = guard;
        // Prompts are a bean, fixed for the installation. The resources are built on the first
        // request that asks for them and then kept: what they hold -- the toolset, the names it
        // advertises, the server-info document -- is as fixed as the toolset is, while the
        // diagnostics they serve go through a supplier and are read afresh on every read. Deferred
        // rather than built here because the toolset is asked for lazily everywhere else, so that a
        // failure assembling it cannot stop the endpoint answering server/discover.
        // One McpResources instance answers three of the six: it serves the resources, and because it
        // is the only thing that knows which tool stands behind which URI, it is also what turns a
        // tool call into a resource link.
        // The skills are a bean loaded once from the classpath, fixed for the installation like the
        // prompts. An empty catalogue serves as NONE, so the extension is declared -- at discover and in
        // the server document alike -- exactly when there is a skill to serve.
        McpSkillProvider served = skills.servesAny() ? skills : McpSkillProvider.NONE;
        boolean servesSkills = served != McpSkillProvider.NONE;
        Supplier<McpResources> resources = once(() -> new McpResources(assembler.toolset(), assembler::documents,
                properties,
                () -> diagnostics.json(assembler.toolset(), toolMetrics().snapshot(), toolMetrics().droppedCalls()),
                servesSkills));
        // Built here rather than lazily: its constructor touches nothing, and whether it can complete
        // anything is a question about configuration. server/discover asks that question, and it
        // must answer even when assembling the toolset would fail -- which is why the toolset reaches
        // it as a supplier rather than as a resolved provider.
        McpCompletions completions = new McpCompletions(assembler::toolset, prompts, properties);
        // A pure function of configuration, like the completions: server/discover must answer even when
        // the toolset cannot be assembled, and what is advertised is already decided by the properties.
        String instructions = McpInstructions.text(advertised);
        this.features = new McpServerFeatures(
                assembler::toolset,
                () -> prompts,
                resources::get,
                () -> instructions,
                // NONE when the catalogue is not advertised, so the capability is not declared at all
                // rather than declared and then unable to complete the one argument it exists for.
                () -> completions.isAvailable() ? completions : McpCompletionProvider.NONE,
                resources::get)
                // A bean fixed by configuration like the completions, and resolved without the
                // toolset: NONE, and no extension declared, when no served family starts an operation.
                .withTasks(() -> tasks)
                // Resolved without the toolset too, so discover can declare it whatever the toolset does.
                .withSkills(() -> served);
    }

    /** A supplier that builds its value on the first call and answers every later one with it. */
    private static <T> Supplier<T> once(Supplier<? extends T> build) {
        return new Supplier<>() {
            private volatile T value;

            @Override
            public T get() {
                T current = value;
                if (current == null) {
                    synchronized (this) {
                        current = value;
                        if (current == null) {
                            current = build.get();
                            value = current;
                        }
                    }
                }
                return current;
            }
        };
    }

    @PostMapping
    public ResponseEntity<JsonNode> handle(
            @RequestBody JsonNode request,
            HttpServletRequest httpRequest) {
        ResponseEntity<JsonNode> refusal = refuseRequest(httpRequest);
        if (refusal != null) {
            return refusal;
        }
        // The protocol version, method and name the headers repeat: the envelope compares them with
        // the body and refuses a request whose headers disagree.
        return dispatch(request, McpTransportHeaders.read(httpRequest::getHeader), features);
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
        Refusal refusal = guard.refusalReason(httpRequest);
        if (refusal == null) {
            return null;
        }
        // The reason travels with the refusal. A bare 401 or 403 from a server that is otherwise
        // answering is the kind of thing somebody debugs a proxy over; the sentence names the check
        // and the property or variable that fixes it, and the plugin's status hook quotes it.
        ResponseEntity.BodyBuilder response = switch (refusal) {
            case Refusal.Unauthorized _ -> ResponseEntity.status(refusal.status())
                    .header(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
            case Refusal.Forbidden _ -> ResponseEntity.status(refusal.status());
        };
        return response.body(Json.createObject().put(REFUSAL_FIELD, refusal.message()));
    }
}
