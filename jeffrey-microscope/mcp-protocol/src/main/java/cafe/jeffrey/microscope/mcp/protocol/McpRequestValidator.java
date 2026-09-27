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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Decides whether a request may be dispatched, before anything is resolved to answer it.
 * <p>
 * The server speaks {@code 2026-07-28} only, so every request carries its own revision and client
 * capabilities in {@code params._meta}, and repeats its revision, method and name in the transport
 * headers. The steps run in a fixed order and the first that fails decides the refusal:
 * <ol>
 *     <li>the body is not an object — arrays included, batching does not exist — {@code -32600};</li>
 *     <li>the JSON-RPC shape ({@code jsonrpc}, {@code method}, the type of {@code id}) — {@code -32600};</li>
 *     <li>{@code params} present and not an object — {@code -32602} (400);</li>
 *     <li>no {@code _meta} protocol version — every handshake-era client, {@code initialize}
 *         included — a request missing a required field, {@code -32602} (400), whose message and
 *         {@code data.supported} still name the revision this server speaks;</li>
 *     <li>{@code MCP-Protocol-Version} missing or different from the {@code _meta} version, whatever
 *         that version is — {@code -32020};</li>
 *     <li>header and {@code _meta} agreeing on a version this server does not speak — {@code -32022};</li>
 *     <li>client capabilities missing or not an object — {@code -32602} (400);</li>
 *     <li>{@code Mcp-Method}, and for a named method {@code Mcp-Name}, missing or different from the
 *         body after decoding — {@code -32020};</li>
 *     <li>no id: a notification, accepted and never answered;</li>
 *     <li>a method outside the method table — {@code -32601} (404);</li>
 *     <li>{@code tasks/get}, {@code tasks/update} or {@code tasks/cancel} from a client that did not
 *         declare the tasks extension —
 *         {@code -32021}; a cursor on a list method, which this server never issues — {@code -32602}.</li>
 * </ol>
 * Pure: it reads the body and the headers and nothing else, and every refusal is an
 * {@link McpProtocolException} carrying its code and, where the code defines one, its data.
 */
public final class McpRequestValidator {

    private static final String JSONRPC_VERSION = "2.0";

    private static final String FIELD_JSONRPC = "jsonrpc";
    private static final String FIELD_ID = "id";
    private static final String FIELD_METHOD = "method";
    private static final String FIELD_PARAMS = "params";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_URI = "uri";
    private static final String FIELD_TASK_ID = "taskId";
    private static final String FIELD_CURSOR = "cursor";
    private static final String FIELD_SUPPORTED = "supported";
    private static final String FIELD_REQUESTED = "requested";
    private static final String FIELD_REQUIRED_CAPABILITIES = "requiredCapabilities";
    private static final String FIELD_EXTENSIONS = "extensions";

    private static final String METHOD_TOOLS_CALL = "tools/call";
    private static final String METHOD_PROMPTS_GET = "prompts/get";
    private static final String METHOD_RESOURCES_READ = "resources/read";
    private static final String METHOD_TASKS_GET = "tasks/get";
    private static final String METHOD_TASKS_UPDATE = "tasks/update";
    private static final String METHOD_TASKS_CANCEL = "tasks/cancel";

    /**
     * The task methods the tasks extension keeps. Only these name a task and need the extension:
     * {@code tasks/list} and {@code tasks/result} were removed from it, name nothing, and are unknown
     * methods like any other (404), not a header mismatch over a taskId they never carry.
     */
    private static final Set<String> TASK_METHODS = Set.of(METHOD_TASKS_GET, METHOD_TASKS_UPDATE, METHOD_TASKS_CANCEL);

    /** The param a named method repeats in {@code Mcp-Name}; the three task methods repeat their {@code taskId}. */
    private static final Map<String, String> NAME_FIELDS = Map.of(
            METHOD_TOOLS_CALL, FIELD_NAME,
            METHOD_PROMPTS_GET, FIELD_NAME,
            METHOD_RESOURCES_READ, FIELD_URI,
            METHOD_TASKS_GET, FIELD_TASK_ID,
            METHOD_TASKS_UPDATE, FIELD_TASK_ID,
            METHOD_TASKS_CANCEL, FIELD_TASK_ID);

    /**
     * The methods that take a pagination {@code cursor}. This server answers each of them in one page
     * and never issues a {@code nextCursor}, so a cursor on any of them is one it did not hand out.
     */
    private static final Set<String> PAGINATED_LIST_METHODS = Set.of(
            "tools/list", "prompts/list", "resources/list", "resources/templates/list", "skills/list");

    private static final String SUPPORTED_SENTENCE = String.join(", ", McpProtocolVersions.SUPPORTED);

    private static final String NOT_AN_OBJECT = "A JSON-RPC request must be one object; batching does not exist";
    private static final String BAD_SHAPE =
            "A JSON-RPC request must declare jsonrpc 2.0, a string method, and a string or integer id";
    private static final String PARAMS_NOT_AN_OBJECT = "MCP params must be an object";
    private static final String NO_META_VERSION =
            "This server speaks MCP " + SUPPORTED_SENTENCE + " only; send per-request _meta (see server/discover)";
    private static final String UNSUPPORTED_VERSION =
            "Unsupported MCP protocol version: %s. This server speaks MCP " + SUPPORTED_SENTENCE + " only";
    private static final String VERSION_HEADER_MISMATCH =
            "The " + McpTransportHeaders.PROTOCOL_VERSION_HEADER + " header (%s) must repeat the version in _meta (%s)";
    private static final String METHOD_HEADER_MISMATCH =
            "The " + McpTransportHeaders.METHOD_HEADER + " header (%s) must repeat the request's method (%s)";
    private static final String NAME_HEADER_MISMATCH =
            "The " + McpTransportHeaders.NAME_HEADER + " header (%s) must repeat params.%s (%s) for %s";
    private static final String METHOD_NOT_FOUND = "Method not found: %s";
    private static final String MISSING_TASKS_EXTENSION =
            "%s needs the " + McpClientCapabilities.TASKS_EXTENSION
                    + " extension; declare it in the client capabilities in _meta";
    private static final String INVALID_CURSOR = "Invalid cursor for %s: this server answers it in one page"
            + " and never issues a nextCursor, so omit the cursor";

    private final Set<String> methods;

    /**
     * @param methods the methods the server answers: its method table's keys
     */
    public McpRequestValidator(Set<String> methods) {
        this.methods = Set.copyOf(methods);
    }

    /**
     * @param body    the request body as parsed, or null when there was none
     * @param headers the transport headers
     * @return the validated request; {@link McpRequestContext#isNotification()} when it carried no id
     * @throws McpProtocolException the first step it fails, with the code and data that step answers
     */
    public McpRequestContext validate(JsonNode body, McpTransportHeaders headers) {
        Objects.requireNonNull(headers, "headers");
        ObjectNode request = requireObject(body);
        JsonNode id = requireJsonRpcShape(request);
        String method = request.get(FIELD_METHOD).asString();
        ObjectNode params = requireParamsObject(request);
        JsonNode meta = params == null ? null : params.get(McpMetaKeys.FIELD_META);
        String version = requireMetaVersion(meta, headers);
        requireVersionHeader(version, headers);
        requireSupportedVersion(version);
        McpClientCapabilities capabilities = McpClientCapabilities.parse(meta.get(McpMetaKeys.CLIENT_CAPABILITIES));
        requireMethodHeaders(method, params, headers);

        McpRequestContext context = new McpRequestContext(
                version, capabilities, clientInfo(meta), id, method, params);
        if (context.isNotification()) {
            return context;
        }
        requireKnownMethod(method);
        requireDeclaredExtension(method, capabilities);
        refuseCursor(method, params);
        return context;
    }

    /**
     * The id an error answer echoes: the request's own when it is a string or an integer, and null when
     * there is no well-formed one to echo.
     */
    public static JsonNode idOf(JsonNode body) {
        if (body == null || !body.isObject()) {
            return null;
        }
        JsonNode id = body.get(FIELD_ID);
        return isWellTypedId(id) ? id : null;
    }

    /** Step 1. */
    private static ObjectNode requireObject(JsonNode body) {
        if (!(body instanceof ObjectNode request)) {
            throw new McpProtocolException(McpErrorCode.INVALID_REQUEST, NOT_AN_OBJECT);
        }
        return request;
    }

    /** Step 2. Only an absent id makes a notification; a present id must be a string or an integer. */
    private static JsonNode requireJsonRpcShape(ObjectNode request) {
        JsonNode version = request.get(FIELD_JSONRPC);
        JsonNode method = request.get(FIELD_METHOD);
        JsonNode id = request.get(FIELD_ID);
        boolean versionOk = version != null && version.isString() && JSONRPC_VERSION.equals(version.asString());
        boolean methodOk = method != null && method.isString() && !method.asString().isBlank();
        boolean idOk = id == null || isWellTypedId(id);
        if (!versionOk || !methodOk || !idOk) {
            throw new McpProtocolException(McpErrorCode.INVALID_REQUEST, BAD_SHAPE);
        }
        return id;
    }

    private static boolean isWellTypedId(JsonNode id) {
        return id != null && (id.isString() || id.isIntegralNumber());
    }

    /** Step 3. An absent params reads as none; a present one must be an object, a JSON null included. */
    private static ObjectNode requireParamsObject(ObjectNode request) {
        if (!request.has(FIELD_PARAMS)) {
            return null;
        }
        if (!(request.get(FIELD_PARAMS) instanceof ObjectNode params)) {
            throw new McpProtocolException(McpErrorCode.INVALID_META, PARAMS_NOT_AN_OBJECT);
        }
        return params;
    }

    /**
     * Step 4. The version is a required {@code _meta} field, so a request without one is malformed
     * ({@code -32602}, 400). A version that is not a string is no version. The refusal still carries
     * {@code {supported, requested}}: a handshake-era client has nowhere else to learn the revision.
     */
    private static String requireMetaVersion(JsonNode meta, McpTransportHeaders headers) {
        JsonNode declared = meta == null || !meta.isObject() ? null : meta.get(McpMetaKeys.PROTOCOL_VERSION);
        if (declared == null || !declared.isString()) {
            throw new McpProtocolException(McpErrorCode.INVALID_META, NO_META_VERSION,
                    versionData(headers.protocolVersion()));
        }
        return declared.asString();
    }

    /** Step 6. Reached only once the header repeats the version, so the version is the client's choice. */
    private static void requireSupportedVersion(String version) {
        if (!McpProtocolVersions.isSupported(version)) {
            throw new McpProtocolException(McpErrorCode.UNSUPPORTED_VERSION, UNSUPPORTED_VERSION.formatted(version),
                    versionData(version));
        }
    }

    /** {@code {supported, requested}}: what a client needs to pick a revision this server speaks. */
    private static ObjectNode versionData(String requested) {
        ObjectNode data = McpJson.createObject();
        ArrayNode supported = data.putArray(FIELD_SUPPORTED);
        McpProtocolVersions.SUPPORTED.forEach(supported::add);
        if (requested == null) {
            data.putNull(FIELD_REQUESTED);
        } else {
            data.put(FIELD_REQUESTED, requested);
        }
        return data;
    }

    /**
     * Step 5. Compared before the version is judged: a header that does not repeat {@code _meta} is a
     * mismatch whatever either says, even when the header names a supported revision.
     */
    private static void requireVersionHeader(String version, McpTransportHeaders headers) {
        if (!version.equals(headers.protocolVersion())) {
            throw new McpProtocolException(McpErrorCode.HEADER_MISMATCH,
                    VERSION_HEADER_MISMATCH.formatted(headers.protocolVersion(), version));
        }
    }

    /** Step 8. Both headers are compared after decoding a {@code =?base64?…?=} value. */
    private static void requireMethodHeaders(String method, ObjectNode params, McpTransportHeaders headers) {
        String methodHeader = McpHeaderValues.decode(headers.method(), McpTransportHeaders.METHOD_HEADER);
        if (!method.equals(methodHeader)) {
            throw new McpProtocolException(McpErrorCode.HEADER_MISMATCH,
                    METHOD_HEADER_MISMATCH.formatted(methodHeader, method));
        }
        String nameField = NAME_FIELDS.get(method);
        if (nameField == null) {
            return;
        }
        String expected = stringParam(params, nameField);
        String nameHeader = McpHeaderValues.decode(headers.name(), McpTransportHeaders.NAME_HEADER);
        if (nameHeader == null || !nameHeader.equals(expected)) {
            throw new McpProtocolException(McpErrorCode.HEADER_MISMATCH,
                    NAME_HEADER_MISMATCH.formatted(nameHeader, nameField, expected, method));
        }
    }

    private static String stringParam(ObjectNode params, String field) {
        JsonNode value = params == null ? null : params.get(field);
        return value != null && value.isString() ? value.asString() : null;
    }

    private static ObjectNode clientInfo(JsonNode meta) {
        return meta.get(McpMetaKeys.CLIENT_INFO) instanceof ObjectNode info ? info : null;
    }

    /** Step 10. */
    private void requireKnownMethod(String method) {
        if (!methods.contains(method)) {
            throw new McpProtocolException(McpErrorCode.METHOD_NOT_FOUND, METHOD_NOT_FOUND.formatted(method));
        }
    }

    /** Step 11. Tasks are gated on the client's declaration; skills are served to anyone. */
    private static void requireDeclaredExtension(String method, McpClientCapabilities capabilities) {
        if (!TASK_METHODS.contains(method)
                || capabilities.extensions().contains(McpClientCapabilities.TASKS_EXTENSION)) {
            return;
        }
        ObjectNode data = McpJson.createObject();
        data.putObject(FIELD_REQUIRED_CAPABILITIES).putObject(FIELD_EXTENSIONS)
                .putObject(McpClientCapabilities.TASKS_EXTENSION);
        throw new McpProtocolException(McpErrorCode.MISSING_CAPABILITY,
                MISSING_TASKS_EXTENSION.formatted(method), data);
    }

    /**
     * A cursor on a list method is one this server did not hand out; the specification asks for
     * {@code -32602}, and answering the first page instead would let a client loop on a page it already
     * holds. An absent, null or empty one reads as omitted.
     */
    private static void refuseCursor(String method, ObjectNode params) {
        if (!PAGINATED_LIST_METHODS.contains(method) || params == null) {
            return;
        }
        JsonNode cursor = params.get(FIELD_CURSOR);
        if (cursor == null || cursor.isNull() || (cursor.isString() && cursor.asString().isEmpty())) {
            return;
        }
        throw new McpProtocolException(McpErrorCode.INVALID_PARAMS, INVALID_CURSOR.formatted(method));
    }
}
