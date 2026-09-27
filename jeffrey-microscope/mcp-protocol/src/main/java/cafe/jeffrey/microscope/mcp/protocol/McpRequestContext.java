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

package cafe.jeffrey.microscope.mcp.protocol;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One validated request: the revision and capabilities its {@code _meta} declared, and everything a
 * method handler needs to answer it.
 *
 * @param protocolVersion the revision from {@code _meta}; always one of {@link McpProtocolVersions#SUPPORTED}
 * @param capabilities    what the client declared in {@code _meta}
 * @param clientInfo      the client's {@code Implementation} from {@code _meta}, or null when it sent none
 * @param id              the JSON-RPC id, or null for a notification
 * @param method          the JSON-RPC method
 * @param params          the request's params; an absent one reads as empty
 */
public record McpRequestContext(
        String protocolVersion,
        McpClientCapabilities capabilities,
        ObjectNode clientInfo,
        JsonNode id,
        String method,
        ObjectNode params) {

    private static final String FIELD_INPUT_RESPONSES = "inputResponses";
    private static final String INPUT_RESPONSES_NOT_AN_OBJECT = "inputResponses must be an object keyed by request";

    public McpRequestContext {
        if (!McpProtocolVersions.isSupported(protocolVersion)) {
            throw new IllegalArgumentException("Not a revision this server speaks: " + protocolVersion);
        }
        Objects.requireNonNull(capabilities, "capabilities");
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("A request needs a method");
        }
        clientInfo = clientInfo == null ? null : clientInfo.deepCopy();
        params = params == null ? McpJson.createObject() : params;
    }

    @Override
    public ObjectNode clientInfo() {
        return clientInfo == null ? null : clientInfo.deepCopy();
    }

    /** Whether the request carried no id: a notification, which is accepted and never answered. */
    public boolean isNotification() {
        return id == null;
    }

    /**
     * What a tool run for this request is told: the client's capabilities, the answers a retried call
     * carries in {@code params.inputResponses}, and the W3C trace context in {@code params._meta} — which,
     * malformed, is left out rather than refused.
     *
     * @throws McpProtocolException {@code -32602} when {@code inputResponses} is not an object or one of
     *                              its answers is malformed
     */
    public McpCallContext callContext() {
        return new McpCallContext(
                capabilities, inputResponses(), McpTraceContext.from(params.get(McpMetaKeys.FIELD_META)));
    }

    private Map<String, McpInputResponse> inputResponses() {
        JsonNode responses = params.get(FIELD_INPUT_RESPONSES);
        if (responses == null || responses.isNull()) {
            return Map.of();
        }
        if (!responses.isObject()) {
            throw new McpProtocolException(McpErrorCode.INVALID_PARAMS, INPUT_RESPONSES_NOT_AN_OBJECT);
        }
        Map<String, McpInputResponse> parsed = new LinkedHashMap<>();
        responses.properties().forEach(entry ->
                parsed.put(entry.getKey(), McpInputResponse.parse(entry.getValue())));
        return parsed;
    }
}
