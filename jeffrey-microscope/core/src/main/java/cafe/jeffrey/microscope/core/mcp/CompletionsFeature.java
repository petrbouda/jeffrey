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

import cafe.jeffrey.microscope.core.utils.Json;
import cafe.jeffrey.microscope.mcp.server.McpCompletion;
import cafe.jeffrey.microscope.mcp.server.McpCompletionRef;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.McpAsyncResponse;
import io.modelcontextprotocol.modern.server.McpFeature;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.util.Set;

/**
 * Serves {@code completion/complete} on the modern server as a custom {@link McpFeature}, backed by
 * {@link McpCompletions}: the two arguments nobody types from memory, {@code profileId} and
 * {@code baselineProfileId}. The capability is declared at {@code server/discover} exactly when the
 * {@code profiles} family is served; the endpoint answers every client, returning an empty list for an
 * argument it does not complete.
 */
public final class CompletionsFeature implements McpFeature {

    private static final String METHOD_COMPLETE = "completion/complete";
    private static final String FIELD_REF = "ref";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_URI = "uri";
    private static final String FIELD_ARGUMENT = "argument";
    private static final String FIELD_VALUE = "value";

    private final McpCompletions completions;

    public CompletionsFeature(McpCompletions completions) {
        this.completions = completions;
    }

    @Override
    public Set<String> methods() {
        return Set.of(METHOD_COMPLETE);
    }

    @Override
    public Mono<? extends McpAsyncResponse<? extends McpSchema.Result>> handle(
            McpRequestContext context, Object params) {
        JsonNode node = Json.toTree(params);
        if (node == null || !node.hasNonNull(FIELD_REF) || !node.hasNonNull(FIELD_ARGUMENT)) {
            throw McpException.invalidParams("completion/complete needs a ref and an argument");
        }
        JsonNode ref = node.get(FIELD_REF);
        String type = ref.path(FIELD_TYPE).asString("");
        // A prompt reference names itself; a resource reference names its uri template.
        String name = McpCompletionRef.TYPE_RESOURCE.equals(type)
                ? ref.path(FIELD_URI).asString("")
                : ref.path(FIELD_NAME).asString("");
        JsonNode argument = node.get(FIELD_ARGUMENT);
        String argumentName = argument.path(FIELD_NAME).asString("");
        String value = argument.path(FIELD_VALUE).asString("");

        McpCompletion completion = completions.complete(new McpCompletionRef(type, name), argumentName, value);
        McpSchema.CompleteResult result = McpSchema.CompleteResult.of(new McpSchema.CompleteResult.Completion(
                completion.values(), completion.total(), completion.hasMore()));
        return Mono.just(McpAsyncResponse.result(result));
    }

    @Override
    public void capabilities(McpSchema.ServerCapabilities.Builder builder) {
        if (completions.isAvailable()) {
            builder.completions();
        }
    }
}
