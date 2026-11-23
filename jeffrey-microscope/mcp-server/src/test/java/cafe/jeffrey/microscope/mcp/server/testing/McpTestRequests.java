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

package cafe.jeffrey.microscope.mcp.server.testing;

import cafe.jeffrey.microscope.mcp.server.McpClientCapabilities;
import cafe.jeffrey.microscope.mcp.server.McpMetaKeys;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Builds the modern ({@code 2026-07-28}) requests every JSON-RPC test sends: the body with its
 * per-request {@code params._meta}, and the three transport headers that must agree with it.
 * <p>
 * One place, so a test that means "a well-formed call" cannot drift into a legacy request by leaving a
 * header out, and a test that means "a malformed call" starts from a correct one and breaks exactly one
 * thing ({@link Request#withHeaders}, {@link Request#editBody}). Shipped in this module's test-jar so
 * the adapter's tests in {@code mcp-server} and the controller tests in {@code core} build
 * the same requests.
 */
public final class McpTestRequests {

    /** The one revision the server speaks. */
    public static final String VERSION = "2026-07-28";

    /** The transport header names a conforming client sends, with the body's {@code _meta}. */
    public static final String PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";
    public static final String METHOD_HEADER = "Mcp-Method";
    public static final String NAME_HEADER = "Mcp-Name";

    private static final String JSONRPC_VERSION = "2.0";
    private static final String FIELD_JSONRPC = "jsonrpc";
    private static final String FIELD_ID = "id";
    private static final String FIELD_METHOD = "method";
    private static final String FIELD_PARAMS = "params";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_URI = "uri";
    private static final String FIELD_TASK_ID = "taskId";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_VERSION = "version";

    private static final String TOOLS_CALL = "tools/call";
    private static final String PROMPTS_GET = "prompts/get";
    private static final String RESOURCES_READ = "resources/read";

    /** The param each named method repeats in {@code Mcp-Name}; {@code tasks/*} name their {@code taskId}. */
    private static final Map<String, String> NAME_FIELDS = Map.of(
            TOOLS_CALL, FIELD_NAME,
            PROMPTS_GET, FIELD_NAME,
            RESOURCES_READ, FIELD_URI,
            "tasks/get", FIELD_TASK_ID,
            "tasks/update", FIELD_TASK_ID,
            "tasks/cancel", FIELD_TASK_ID);

    private static final String CLIENT_NAME = "mcp-test-client";
    private static final String CLIENT_VERSION = "1";
    private static final int DEFAULT_ID = 1;

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private McpTestRequests() {
    }

    /** A request with no params beyond its {@code _meta}, from a client that declared nothing. */
    public static Request request(String method) {
        return request(method, MAPPER.createObjectNode());
    }

    /** A request from a client that declared nothing. */
    public static Request request(String method, ObjectNode params) {
        return request(method, params, MAPPER.createObjectNode());
    }

    /** A request whose params are written as JSON, from a client that declared nothing. */
    public static Request request(String method, String paramsJson) {
        return request(method, (ObjectNode) MAPPER.readTree(paramsJson));
    }

    /**
     * @param params       the method's own params; {@code _meta} is added to a copy
     * @param capabilities what the client declares in {@code _meta}
     */
    public static Request request(String method, ObjectNode params, ObjectNode capabilities) {
        ObjectNode body = envelope(method, params, capabilities);
        body.set(FIELD_ID, IntNode.valueOf(DEFAULT_ID));
        return new Request(body, VERSION, method, nameOf(method, params));
    }

    /** The same request without an id: a notification. */
    public static Request notification(String method, ObjectNode params) {
        return new Request(envelope(method, params, MAPPER.createObjectNode()), VERSION, method, nameOf(method, params));
    }

    /** {@code tools/call} of one tool with these arguments; null arguments sends none. */
    public static Request toolCall(String toolName, ObjectNode arguments) {
        return request(TOOLS_CALL, toolCallParams(toolName, arguments));
    }

    /** {@code tools/call} from a client that declared {@code capabilities}. */
    public static Request toolCall(String toolName, ObjectNode arguments, ObjectNode capabilities) {
        return request(TOOLS_CALL, toolCallParams(toolName, arguments), capabilities);
    }

    /** A client that renders form elicitations. */
    public static ObjectNode elicitingClient() {
        return (ObjectNode) MAPPER.readTree("{\"elicitation\":{\"form\":{}}}");
    }

    /** A client that declared the tasks extension. */
    public static ObjectNode tasksClient() {
        ObjectNode capabilities = MAPPER.createObjectNode();
        capabilities.putObject("extensions").putObject(McpClientCapabilities.TASKS_EXTENSION);
        return capabilities;
    }

    /** The {@code params._meta} a modern client sends. */
    public static ObjectNode meta(String version, ObjectNode capabilities) {
        ObjectNode meta = MAPPER.createObjectNode();
        meta.put(McpMetaKeys.PROTOCOL_VERSION, version);
        meta.set(McpMetaKeys.CLIENT_CAPABILITIES, capabilities.deepCopy());
        meta.set(McpMetaKeys.CLIENT_INFO,
                MAPPER.createObjectNode().put(FIELD_NAME, CLIENT_NAME).put(FIELD_VERSION, CLIENT_VERSION));
        return meta;
    }

    private static ObjectNode toolCallParams(String toolName, ObjectNode arguments) {
        ObjectNode params = MAPPER.createObjectNode().put(FIELD_NAME, toolName);
        if (arguments != null) {
            params.set(FIELD_ARGUMENTS, arguments.deepCopy());
        }
        return params;
    }

    private static ObjectNode envelope(String method, ObjectNode params, ObjectNode capabilities) {
        ObjectNode withMeta = params.deepCopy();
        withMeta.set(McpMetaKeys.FIELD_META, meta(VERSION, capabilities));
        ObjectNode body = MAPPER.createObjectNode().put(FIELD_JSONRPC, JSONRPC_VERSION).put(FIELD_METHOD, method);
        body.set(FIELD_PARAMS, withMeta);
        return body;
    }

    private static String nameOf(String method, ObjectNode params) {
        String field = NAME_FIELDS.get(method);
        if (field == null) {
            return null;
        }
        JsonNode value = params.get(field);
        return value == null || !value.isString() ? null : value.asString();
    }

    /**
     * One request: the body and the transport headers that travel with it.
     *
     * @param body            the JSON-RPC body
     * @param protocolVersion the {@code MCP-Protocol-Version} header
     * @param method          the {@code Mcp-Method} header
     * @param name            the {@code Mcp-Name} header, or null where the method names nothing
     */
    public record Request(ObjectNode body, String protocolVersion, String method, String name) {

        public Request {
            body = Objects.requireNonNull(body, "body").deepCopy();
        }

        @Override
        public ObjectNode body() {
            return body.deepCopy();
        }

        /** The body as it goes on the wire. */
        public String json() {
            return body.toString();
        }

        /** The same request with the id replaced. */
        public Request withId(JsonNode id) {
            return editBody(edited -> edited.set(FIELD_ID, id));
        }

        /** The same headers over a body changed by {@code edit}, which receives a copy. */
        public Request editBody(Consumer<ObjectNode> edit) {
            ObjectNode edited = body.deepCopy();
            edit.accept(edited);
            return new Request(edited, protocolVersion, method, name);
        }

        /** The same headers over a body changed inside {@code params._meta}. */
        public Request editMeta(Consumer<ObjectNode> edit) {
            return editBody(edited -> edit.accept((ObjectNode) edited.get(FIELD_PARAMS).get(McpMetaKeys.FIELD_META)));
        }

        /** The headers that are present, by their HTTP names, for a servlet or MockMvc request. */
        public Map<String, String> httpHeaders() {
            Map<String, String> http = new LinkedHashMap<>();
            putIfPresent(http, PROTOCOL_VERSION_HEADER, protocolVersion);
            putIfPresent(http, METHOD_HEADER, method);
            putIfPresent(http, NAME_HEADER, name);
            return http;
        }

        private static void putIfPresent(Map<String, String> http, String name, String value) {
            if (value != null) {
                http.put(name, value);
            }
        }
    }
}
