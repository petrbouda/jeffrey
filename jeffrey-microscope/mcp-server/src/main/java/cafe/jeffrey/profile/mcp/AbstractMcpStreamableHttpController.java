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

package cafe.jeffrey.profile.mcp;

import cafe.jeffrey.microscope.mcp.protocol.McpDispatcher;
import cafe.jeffrey.microscope.mcp.protocol.McpDispatcherSettings;
import cafe.jeffrey.microscope.mcp.protocol.McpProtocolVersions;
import cafe.jeffrey.microscope.mcp.protocol.McpResponse;
import cafe.jeffrey.microscope.mcp.protocol.McpServerFeatures;
import cafe.jeffrey.microscope.mcp.protocol.McpToolCall;
import cafe.jeffrey.microscope.mcp.protocol.McpTransportHeaders;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Jeffrey's MCP Streamable-HTTP endpoint base: the Spring boundary around the protocol's
 * {@link McpDispatcher}, which owns everything the protocol says — request validation, the method
 * table, {@code server/discover}, tools, prompts, resources, completions, tasks, skills, and the
 * success/error response shape. This class only maps the dispatcher's answer onto a
 * {@link ResponseEntity} and hands the dispatcher what is Jeffrey's: its identity, the most a result may
 * carry ({@link McpToolOutput#MAX_CHARS}), how Jeffrey's exceptions read ({@link JeffreyFailurePolicy}),
 * and the per-tool metrics {@code jeffrey://diagnostics} serves.
 * <p>
 * A subclass keeps its own {@code @RestController}/{@code @RequestMapping}/{@code @PostMapping} and
 * delegates to {@link #dispatch(JsonNode, McpTransportHeaders, McpServerFeatures)}. Every provider
 * inside the features is invoked lazily, only for the methods that need it.
 */
public abstract class AbstractMcpStreamableHttpController {

    /**
     * What a client is told when a tool or a request failed for a reason it can do nothing about. The
     * exception's own words — a helpful-NPE sentence naming a field, a database driver's message — are
     * for whoever reads the server log, where they are written in full; the client only needs to know
     * that its request was not what went wrong.
     */
    public static final String INTERNAL_FAILURE_MESSAGE =
            "The tool failed inside Jeffrey; the server log has the detail";

    private final McpToolMetrics metrics = new McpToolMetrics();

    private final McpDispatcher dispatcher = new McpDispatcher(new McpDispatcherSettings(
            JeffreyMcpServer.IDENTITY,
            McpToolOutput.MAX_CHARS,
            new JeffreyFailurePolicy(INTERNAL_FAILURE_MESSAGE),
            this::recordCall));

    protected McpToolMetrics toolMetrics() {
        return metrics;
    }

    public static String serverVersion() {
        return JeffreyMcpServer.IDENTITY.version();
    }

    /** The revisions this server speaks. */
    public static List<String> supportedProtocolVersions() {
        return McpProtocolVersions.SUPPORTED;
    }

    /**
     * Validates one request and answers it; see {@link McpDispatcher#dispatch}. The HTTP status of a
     * refusal comes from its code, and a notification is accepted with {@code 202} and no body.
     *
     * @param headers the transport headers the request arrived with
     */
    protected ResponseEntity<JsonNode> dispatch(
            JsonNode body, McpTransportHeaders headers, McpServerFeatures features) {
        return toResponseEntity(dispatcher.dispatch(body, headers, features));
    }

    protected final ResponseEntity<JsonNode> parseErrorResponse() {
        return toResponseEntity(dispatcher.parseError());
    }

    private void recordCall(McpToolCall call) {
        metrics.record(call.tool(), call.durationNanos(), call.resultBytes(), call.isError());
    }

    private static ResponseEntity<JsonNode> toResponseEntity(McpResponse response) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.status());
        return response.hasBody() ? builder.body(response.body()) : builder.build();
    }
}
