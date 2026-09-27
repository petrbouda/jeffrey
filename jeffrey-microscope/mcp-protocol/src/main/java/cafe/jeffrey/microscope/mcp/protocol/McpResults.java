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

import java.util.Objects;

/**
 * Finishes a result the way {@code 2026-07-28} requires: every result says what kind it is in
 * {@code resultType} and which server answered in {@code _meta["io.modelcontextprotocol/serverInfo"]}.
 * Only a {@code complete} result carries a cache hint; a question and a task never do.
 * <p>
 * One per server, holding the identity every result names. Each method changes the node in place and
 * returns it, keeping any {@code _meta} it already held.
 */
public final class McpResults {

    private static final String FIELD_RESULT_TYPE = "resultType";
    private static final String RESULT_COMPLETE = "complete";
    private static final String RESULT_INPUT_REQUIRED = "input_required";
    private static final String RESULT_TASK = "task";
    private static final String FIELD_INPUT_REQUESTS = "inputRequests";
    private static final String UNANSWERABLE = "The client did not declare it can answer the input request: ";

    private final McpServerIdentity server;

    /**
     * @param server the identity every result states in {@code _meta["io.modelcontextprotocol/serverInfo"]}
     */
    public McpResults(McpServerIdentity server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    /**
     * @param hint the cache hint the method's result carries; {@link McpCacheHint#NONE} adds none
     */
    public ObjectNode complete(ObjectNode result, McpCacheHint hint) {
        Objects.requireNonNull(hint, "hint");
        result.put(FIELD_RESULT_TYPE, RESULT_COMPLETE);
        hint.writeTo(result);
        return withServerInfo(result);
    }

    /**
     * Marks a result holding {@code inputRequests}. Never carries a cache hint: the answer is the user's,
     * and the retry is a new request.
     */
    public ObjectNode inputRequired(ObjectNode result) {
        result.put(FIELD_RESULT_TYPE, RESULT_INPUT_REQUIRED);
        return withServerInfo(result);
    }

    /**
     * An {@code input_required} result asking a tool's questions, in the order the tool asked them.
     *
     * @throws IllegalStateException when the client did not declare it can answer one of them — the tool
     *                               was told it could not ask, so asking is a bug
     */
    public ObjectNode inputRequired(McpToolOutcome.InputRequired questions, McpClientCapabilities client) {
        ObjectNode result = McpJson.createObject();
        ObjectNode requests = result.putObject(FIELD_INPUT_REQUESTS);
        questions.requests().forEach((key, request) -> {
            if (!request.answerableBy(client)) {
                throw new IllegalStateException(UNANSWERABLE + key);
            }
            requests.set(key, request.toJson());
        });
        return inputRequired(result);
    }

    /**
     * Marks a {@code CreateTaskResult}. The task's own {@code ttlMs} is its retention, set by the caller;
     * no cache hint is added.
     */
    public ObjectNode task(ObjectNode task) {
        task.put(FIELD_RESULT_TYPE, RESULT_TASK);
        return withServerInfo(task);
    }

    private ObjectNode withServerInfo(ObjectNode result) {
        JsonNode existing = result.get(McpMetaKeys.FIELD_META);
        ObjectNode meta = existing instanceof ObjectNode object
                ? object
                : result.putObject(McpMetaKeys.FIELD_META);
        meta.set(McpMetaKeys.SERVER_INFO, server.toJson());
        return result;
    }
}
