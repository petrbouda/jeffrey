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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.JeffreyVersion;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generic MCP Streamable-HTTP (JSON-RPC 2.0) endpoint exposing Jeffrey's reflective analysis tools to
 * an external client. This base owns the protocol envelope — {@code initialize}, {@code ping},
 * {@code tools/list}, {@code tools/call}, prompts, resources, notifications, and the success/error
 * response shape — so a controller only declares its own request mapping and the
 * {@link McpServerFeatures} it serves.
 * <p>
 * A subclass keeps its own {@code @RestController}/{@code @RequestMapping}/{@code @PostMapping} and
 * delegates to {@link #dispatch(JsonNode, String, McpServerFeatures)}. Every provider inside the
 * features is invoked lazily, only for the methods that need it.
 */
public abstract class AbstractMcpStreamableHttpController {

    private static final String JSONRPC_VERSION = "2.0";

    /** The newest revision this server implements, and what an unrecognised one is answered with. */
    private static final String DEFAULT_PROTOCOL_VERSION = "2025-11-25";

    /**
     * The revisions this server implements, oldest first. The envelope has not changed across them in
     * any way Jeffrey uses — the additions between them (icons, tasks, elicitation, authorization
     * discovery) are all optional and none are things this server sends — so an older client is
     * served as it asks; anything else is answered with the default.
     * <p>
     * They are all <em>handshake</em> revisions: a session opens with {@code initialize}. From
     * {@code 2026-07-28} the protocol works differently — every request carries its own version in
     * {@code _meta}, {@code server/discover} is mandatory, and sessions are gone — and this server
     * implements none of that. Ordered, because the set is also the sentence a refusal carries.
     */
    private static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Collections.unmodifiableSet(
            new LinkedHashSet<>(List.of("2024-11-05", "2025-03-26", "2025-06-18", DEFAULT_PROTOCOL_VERSION)));
    private static final String SERVER_NAME = "jeffrey";
    private static final String SERVER_VERSION = JeffreyVersion.resolveJeffreyVersion();
    public static final String STRUCTURED_RESULTS_VERSION = "2025-06-18";
    private static final String FIELD_OUTPUT_SCHEMA = "outputSchema";
    private static final String FIELD_STRUCTURED_CONTENT = "structuredContent";

    private static final String METHOD_INITIALIZE = "initialize";
    private static final String METHOD_TOOLS_LIST = "tools/list";
    private static final String METHOD_TOOLS_CALL = "tools/call";
    private static final String METHOD_PING = "ping";
    private static final String METHOD_PROMPTS_LIST = "prompts/list";
    private static final String METHOD_PROMPTS_GET = "prompts/get";
    private static final String METHOD_RESOURCES_LIST = "resources/list";
    private static final String METHOD_RESOURCES_TEMPLATES_LIST = "resources/templates/list";
    private static final String METHOD_RESOURCES_READ = "resources/read";
    private static final String METHOD_COMPLETION_COMPLETE = "completion/complete";

    private static final String FIELD_JSONRPC = "jsonrpc";
    private static final String FIELD_ID = "id";
    private static final String FIELD_METHOD = "method";
    private static final String FIELD_PARAMS = "params";
    private static final String FIELD_RESULT = "result";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_REQUIRED = "required";
    private static final String FIELD_URI = "uri";
    private static final String FIELD_URI_TEMPLATE = "uriTemplate";
    private static final String FIELD_MIME_TYPE = "mimeType";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_ROLE = "role";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_CONTENTS = "contents";
    private static final String FIELD_MESSAGES = "messages";
    private static final String FIELD_PROTOCOL_VERSION = "protocolVersion";
    private static final String FIELD_CAPABILITIES = "capabilities";
    private static final String FIELD_SERVER_INFO = "serverInfo";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_TOOLS = "tools";
    private static final String FIELD_PROMPTS = "prompts";
    private static final String FIELD_RESOURCES = "resources";
    private static final String FIELD_RESOURCE_TEMPLATES = "resourceTemplates";
    private static final String FIELD_INPUT_SCHEMA = "inputSchema";
    private static final String FIELD_ANNOTATIONS = "annotations";
    private static final String FIELD_LIST_CHANGED = "listChanged";
    private static final String FIELD_SUBSCRIBE = "subscribe";
    private static final String FIELD_IS_ERROR = "isError";
    private static final String FIELD_READ_ONLY_HINT = "readOnlyHint";
    private static final String FIELD_DESTRUCTIVE_HINT = "destructiveHint";
    private static final String FIELD_IDEMPOTENT_HINT = "idempotentHint";
    private static final String FIELD_OPEN_WORLD_HINT = "openWorldHint";

    private static final String FIELD_CURSOR = "cursor";
    private static final String FIELD_INSTRUCTIONS = "instructions";
    private static final String FIELD_COMPLETIONS = "completions";
    private static final String FIELD_COMPLETION = "completion";
    private static final String FIELD_VALUES = "values";
    private static final String FIELD_TOTAL = "total";
    private static final String FIELD_HAS_MORE = "hasMore";
    private static final String FIELD_REF = "ref";
    private static final String FIELD_ARGUMENT = "argument";
    private static final String FIELD_VALUE = "value";

    private static final String CONTENT_TYPE_RESOURCE_LINK = "resource_link";

    /**
     * The first revision that removed JSON-RPC batching. A client that negotiated this or anything
     * newer is not entitled to send a batch, and accepting one would let it keep a habit the
     * specification dropped.
     */
    private static final String BATCHING_REMOVED_VERSION = "2025-06-18";

    /**
     * The methods that take a pagination {@code cursor}. This server answers each of them in one page
     * and never issues a {@code nextCursor}, so a cursor on any of them is one it did not hand out.
     */
    private static final Set<String> PAGINATED_LIST_METHODS = Set.of(
            METHOD_TOOLS_LIST, METHOD_PROMPTS_LIST, METHOD_RESOURCES_LIST, METHOD_RESOURCES_TEMPLATES_LIST);

    private static final String CONTENT_TYPE_TEXT = "text";
    private static final String ROLE_USER = "user";
    private static final String TOOL_ERROR_PREFIX = "Error: ";

    /**
     * What a client is told when a tool or a request failed for a reason it can do nothing about. The
     * exception's own words — a helpful-NPE sentence naming a field, a database driver's message — are
     * for whoever reads the server log, where they are written in full; the client only needs to know
     * that its request was not what went wrong.
     */
    public static final String INTERNAL_FAILURE_MESSAGE =
            "The tool failed inside Jeffrey; the server log has the detail";

    /**
     * Plain JSON-RPC codes, and a refused protocol version deliberately leaves through
     * {@link #ERROR_INVALID_REQUEST} rather than through {@code -32022}
     * ({@code UnsupportedProtocolVersionError}, from {@code 2026-07-28}) even though that code
     * describes the refusal better.
     * <p>
     * A client that speaks both eras decides what this server is from the <em>body</em> of the 400 it
     * gets back: a recognised modern error means "modern server, retry with a version it listed", and
     * anything else means "older server, fall back to {@code initialize}". This server only has the
     * handshake, so it must read as the second. Answering {@code -32022} would send a client that
     * would otherwise work into a retry loop over versions none of which this server implements.
     * Upgrade the code only together with the rest of the modern protocol.
     */
    private static final int ERROR_INVALID_REQUEST = -32600;
    private static final int ERROR_METHOD_NOT_FOUND = -32601;
    private static final int ERROR_INVALID_PARAMS = -32602;
    private static final int ERROR_INTERNAL = -32603;

    /** The MCP code for a {@code resources/read} whose subject does not exist. */
    private static final int ERROR_RESOURCE_NOT_FOUND = -32002;

    /** The supported revisions as a client-readable list, built once. */
    private static final String SUPPORTED_VERSIONS_SENTENCE = String.join(", ", SUPPORTED_PROTOCOL_VERSIONS);

    private final McpToolMetrics metrics = new McpToolMetrics();

    protected McpToolMetrics toolMetrics() {
        return metrics;
    }

    private final Logger log = LoggerFactory.getLogger(getClass());

    public static String serverVersion() {
        return SERVER_VERSION;
    }

    public static List<String> supportedProtocolVersions() {
        return List.copyOf(SUPPORTED_PROTOCOL_VERSIONS);
    }

    /**
     * Routes a JSON-RPC body — one request, or a batch of them — against everything the endpoint
     * offers. Each provider is resolved only for the methods that need it, so a failure building the
     * toolset cannot stop the endpoint from answering {@code initialize}.
     * <p>
     * A batch is answered with an array holding one response per request that carried an id, in the
     * order they arrived, and with {@code 202} when every element was a notification. The two older
     * protocol revisions this server accepts both require batching, so a batch that were treated as one
     * malformed request would be silently accepted and never answered.
     *
     * @param protocolVersionHeader the {@code MCP-Protocol-Version} header, or null when the caller
     *                              does not read headers. A version this server does not implement is
     *                              refused here rather than half-served below.
     */
    protected ResponseEntity<JsonNode> dispatch(
            JsonNode request, String protocolVersionHeader, McpServerFeatures features) {

        if (protocolVersionHeader != null
                && !protocolVersionHeader.isBlank()
                && !SUPPORTED_PROTOCOL_VERSIONS.contains(protocolVersionHeader)) {
            // Routine rather than wrong: a client that speaks both eras opens with the newest version
            // it has, and reads the refusal as "this server is older" before falling back to
            // `initialize`. Logged so the fallback is visible when a client fails for some other
            // reason, not because anything here needs attention.
            log.info("An MCP client asked for a protocol revision this server does not implement,"
                            + " and will fall back to initialize: version={} supported={}",
                    protocolVersionHeader, SUPPORTED_VERSIONS_SENTENCE);
            return ResponseEntity.badRequest().body(error(
                    null,
                    ERROR_INVALID_REQUEST,
                    "Unsupported MCP protocol version: " + protocolVersionHeader
                            + ". This server implements " + SUPPORTED_VERSIONS_SENTENCE));
        }

        if (request == null || (!request.isObject() && !request.isArray())) {
            return ResponseEntity.badRequest()
                    .body(error(null, ERROR_INVALID_REQUEST, "A JSON-RPC request must be an object or a batch"));
        }
        if (request.isArray()) {
            if (atLeast(protocolVersionHeader, BATCHING_REMOVED_VERSION)) {
                return ResponseEntity.badRequest().body(error(null, ERROR_INVALID_REQUEST,
                        "JSON-RPC batching was removed in MCP " + BATCHING_REMOVED_VERSION
                                + "; send one request per POST"));
            }
            return dispatchBatch(request, features, supportsStructured(protocolVersionHeader));
        }
        JsonNode response = dispatchOne(request, features, supportsStructured(protocolVersionHeader));
        if (response == null) {
            return ResponseEntity.accepted().build();
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Answers every request in a batch. An empty array is not a batch of nothing but a malformed body,
     * which is what the specification calls it too.
     */
    private ResponseEntity<JsonNode> dispatchBatch(JsonNode batch, McpServerFeatures features, boolean structured) {
        if (batch.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(error(null, ERROR_INVALID_REQUEST, "A JSON-RPC batch must not be empty"));
        }
        ArrayNode responses = Json.createArray();
        for (JsonNode element : batch) {
            if (!element.isObject()) {
                responses.add(error(null, ERROR_INVALID_REQUEST, "A JSON-RPC request must be an object"));
                continue;
            }
            JsonNode response = dispatchOne(element, features, structured);
            if (response != null) {
                responses.add(response);
            }
        }
        // Every element was a notification: there is nothing to send back, and an empty array is not a
        // valid batch response.
        if (responses.isEmpty()) {
            return ResponseEntity.accepted().build();
        }
        return ResponseEntity.ok(responses);
    }

    /**
     * Routes one request object.
     *
     * @return the response to send, or null when the request was a notification and needs none
     */
    private JsonNode dispatchOne(JsonNode request, McpServerFeatures features, boolean structured) {
        JsonNode id = request.get(FIELD_ID);
        if (id != null && !id.isString() && !id.isIntegralNumber()) {
            return error(null, ERROR_INVALID_REQUEST, "A JSON-RPC id must be a string or integer");
        }
        JsonNode version = request.get(FIELD_JSONRPC);
        JsonNode methodNode = request.get(FIELD_METHOD);
        if (version == null || !version.isString() || !JSONRPC_VERSION.equals(version.asString())
                || methodNode == null || !methodNode.isString() || methodNode.asString().isBlank()) {
            return error(id, ERROR_INVALID_REQUEST, "A JSON-RPC request must declare jsonrpc 2.0 and a string method");
        }
        String method = methodNode.asString();
        // Only an absent id makes a notification; its method name does not.
        if (id == null) {
            return null;
        }
        JsonNode params = request.get(FIELD_PARAMS);
        if (params != null && !params.isObject()) {
            return error(id, ERROR_INVALID_PARAMS, "MCP params must be an object");
        }

        try {
            if (PAGINATED_LIST_METHODS.contains(method)) {
                refuseCursor(method, params);
            }
            return switch (method) {
                case METHOD_INITIALIZE -> initializeResult(id, request, features);
                case METHOD_PING -> success(id, Json.createObject());
                case METHOD_TOOLS_LIST -> toolsList(id, features.tools().get(), structured);
                case METHOD_TOOLS_CALL -> toolsCall(id, features.tools().get(), request.path(FIELD_PARAMS),
                        structured, features.resourceLinks().get());
                case METHOD_PROMPTS_LIST -> promptsList(id, features.prompts().get());
                case METHOD_PROMPTS_GET -> promptsGet(id, features.prompts().get(), request.path(FIELD_PARAMS));
                case METHOD_RESOURCES_LIST -> resourcesList(id, features.resources().get());
                case METHOD_RESOURCES_TEMPLATES_LIST -> resourceTemplatesList(id, features.resources().get());
                case METHOD_RESOURCES_READ ->
                        resourcesRead(id, features.resources().get(), request.path(FIELD_PARAMS));
                case METHOD_COMPLETION_COMPLETE ->
                        completionComplete(id, features.completions().get(), request.path(FIELD_PARAMS));
                default -> error(id, ERROR_METHOD_NOT_FOUND, "Method not found: " + method);
            };
        } catch (McpResourceNotFoundException e) {
            log.warn("MCP resource not found: method={} message={}", method, e.getMessage());
            return error(id, ERROR_RESOURCE_NOT_FOUND, describe(e));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid MCP request: method={} message={}", method, e.getMessage());
            return error(id, ERROR_INVALID_PARAMS, describe(e));
        } catch (Exception e) {
            log.error("MCP request failed: method={} message={}", method, e.getMessage(), e);
            return error(id, ERROR_INTERNAL, clientMessage(e));
        }
    }

    /**
     * Refuses a pagination cursor on a method this server never paginates. No answer of its carries a
     * {@code nextCursor}, so no cursor a client sends can be one it was given; the specification asks
     * for {@code -32602} on a cursor the server does not recognise, and answering the first page
     * instead would let a client loop on a page it already holds. A blank one reads as omitted.
     */
    private static void refuseCursor(String method, JsonNode params) {
        JsonNode cursor = params == null ? null : params.get(FIELD_CURSOR);
        if (cursor == null || cursor.isNull() || (cursor.isString() && cursor.asString().isEmpty())) {
            return;
        }
        throw new IllegalArgumentException("Invalid cursor for " + method + ": this server answers it in "
                + "one page and never issues a nextCursor, so omit the cursor");
    }

    private static boolean supportsStructured(String protocolVersionHeader) {
        return atLeast(protocolVersionHeader, STRUCTURED_RESULTS_VERSION);
    }

    /**
     * Whether the revision the client declared is the given one or newer.
     * <p>
     * No session state is kept here, so the header is all there is. Without one, the HTTP compatibility
     * default is {@code 2025-03-26}, which predates every revision this asks about — so an absent
     * header reads as "older", which is the conservative answer for both callers: no structured
     * results, and batching still allowed. The comparison is lexical, which is sound because every
     * revision is an ISO date and anything this server does not implement was already refused above.
     */
    private static boolean atLeast(String protocolVersionHeader, String revision) {
        return protocolVersionHeader != null && !protocolVersionHeader.isBlank()
                && protocolVersionHeader.compareTo(revision) >= 0;
    }

    private JsonNode initializeResult(JsonNode id, JsonNode request, McpServerFeatures features) {
        String requestedProtocol = request.path(FIELD_PARAMS).path(FIELD_PROTOCOL_VERSION).asString();
        // Agreeing to whatever the client names is not negotiation — it promises a version this server
        // may not speak. An unrecognised one is answered with what it does speak, and the client decides.
        String protocolVersion = SUPPORTED_PROTOCOL_VERSIONS.contains(requestedProtocol)
                ? requestedProtocol
                : DEFAULT_PROTOCOL_VERSION;
        ObjectNode result = Json.createObject();
        result.put(FIELD_PROTOCOL_VERSION, protocolVersion);
        ObjectNode capabilities = result.putObject(FIELD_CAPABILITIES);
        capabilities.putObject(FIELD_TOOLS).put(FIELD_LIST_CHANGED, false);
        capabilities.putObject(FIELD_PROMPTS).put(FIELD_LIST_CHANGED, false);
        capabilities.putObject(FIELD_RESOURCES).put(FIELD_SUBSCRIBE, false).put(FIELD_LIST_CHANGED, false);
        // Declared only when this endpoint actually has a provider. A capability that is advertised and
        // then answers nothing is worse than one that was never offered: a client builds a picker on it.
        if (features.completions().get() != McpCompletionProvider.NONE) {
            capabilities.putObject(FIELD_COMPLETIONS);
        }
        ObjectNode serverInfo = result.putObject(FIELD_SERVER_INFO);
        serverInfo.put(FIELD_NAME, SERVER_NAME);
        serverInfo.put(FIELD_VERSION, SERVER_VERSION);
        // How to use a server with a hundred-odd tools, handed over before the first call rather than
        // left for the client to infer from a tool list.
        String instructions = features.instructions().get();
        if (instructions != null && !instructions.isBlank()) {
            result.put(FIELD_INSTRUCTIONS, instructions);
        }
        return success(id, result);
    }

    private JsonNode toolsList(JsonNode id, McpToolProvider toolset, boolean structured) {
        ObjectNode result = Json.createObject();
        ArrayNode tools = result.putArray(FIELD_TOOLS);
        for (McpToolSpec spec : toolset.specs()) {
            ObjectNode tool = tools.addObject();
            tool.put(FIELD_NAME, spec.name());
            if (spec.title() != null && !spec.title().isBlank()) {
                tool.put(FIELD_TITLE, spec.title());
            }
            tool.put(FIELD_DESCRIPTION, spec.description());
            tool.set(FIELD_INPUT_SCHEMA, spec.inputSchema());
            if (structured && spec.outputSchema() != null) {
                tool.set(FIELD_OUTPUT_SCHEMA, spec.outputSchema());
            }
            ObjectNode annotations = tool.putObject(FIELD_ANNOTATIONS);
            annotations.put(FIELD_READ_ONLY_HINT, spec.annotations().readOnly());
            annotations.put(FIELD_DESTRUCTIVE_HINT, spec.annotations().destructive());
            annotations.put(FIELD_IDEMPOTENT_HINT, spec.annotations().idempotent());
            annotations.put(FIELD_OPEN_WORLD_HINT, spec.annotations().openWorld());
        }
        return success(id, result);
    }

    /**
     * Runs one tool and wraps what it said.
     * <p>
     * A tool that ran and failed is an answer: it comes back inside the result with {@code isError} set,
     * because the model is meant to read it and try something else. A call that never reached a tool —
     * an unknown name, an argument that does not fit the schema — is not, and leaves through the
     * JSON-RPC error channel instead, so a client can tell "your analysis found nothing" apart from
     * "that tool does not exist".
     */
    private JsonNode toolsCall(
            JsonNode id, McpToolProvider toolset, JsonNode params, boolean structured, McpResourceLinker linker) {

        String toolName = params.path(FIELD_NAME).asString();
        JsonNode arguments = params.get(FIELD_ARGUMENTS);

        boolean advertised = toolset.specs().stream().anyMatch(spec -> spec.name().equals(toolName));
        boolean dispatched = true;
        // Not Measuring: the duration has to be recorded in the finally even when the call threw, and
        // Measuring.s hands back an Elapsed only on the success path.
        long started = System.nanoTime();
        ObjectNode result = Json.createObject();
        ArrayNode content = result.putArray(FIELD_CONTENT);
        try {
            McpToolResult output = toolset.callResult(toolName, arguments);
            content.addObject().put(FIELD_TYPE, CONTENT_TYPE_TEXT).put(FIELD_TEXT, output.text());
            appendResourceLinks(content, linker, toolName, arguments);
            if (structured && output.hasStructuredContent()) {
                result.set(FIELD_STRUCTURED_CONTENT, output.structuredContent());
            }
            result.put(FIELD_IS_ERROR, false);
        } catch (ToolDispatchException e) {
            dispatched = false;
            // Rethrown so the envelope answers -32602: the call never reached a tool.
            throw e;
        } catch (Exception e) {
            Throwable failure = unwrapToolFailure(e);
            if (callerActionable(failure)) {
                log.warn("MCP tool call failed: tool={} message={}", toolName, describe(failure));
            } else {
                // The client gets the fixed sentence, so this is the only place the detail survives.
                log.error("MCP tool call failed inside the server: tool={} message={}",
                        toolName, describe(failure), e);
            }
            content.addObject().put(FIELD_TYPE, CONTENT_TYPE_TEXT)
                    .put(FIELD_TEXT, TOOL_ERROR_PREFIX + clientMessage(failure));
            result.put(FIELD_IS_ERROR, true);
        } finally {
            if (advertised && dispatched) {
                metrics.record(toolName, System.nanoTime() - started, Json.toByteArray(result).length,
                        result.path(FIELD_IS_ERROR).asBoolean());
            }
        }
        return success(id, result);
    }

    /**
     * Answers {@code completion/complete} for one argument of a resource template or a prompt.
     * <p>
     * A reference this server does not recognise is {@code -32602} rather than an empty list: an empty
     * completion means "nothing matches what you typed", and a client cannot tell that apart from
     * "you asked about something that is not here" unless the second one is an error.
     */
    private JsonNode completionComplete(JsonNode id, McpCompletionProvider provider, JsonNode params) {
        JsonNode reference = params.path(FIELD_REF);
        String type = reference.path(FIELD_TYPE).asString();
        String name = reference.has(FIELD_URI)
                ? reference.path(FIELD_URI).asString()
                : reference.path(FIELD_NAME).asString();
        if (type.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException(
                    "A completion reference must carry a type and either a uri or a name");
        }
        if (!McpCompletionRef.TYPE_RESOURCE.equals(type) && !McpCompletionRef.TYPE_PROMPT.equals(type)) {
            throw new IllegalArgumentException("Unknown completion reference type: " + type);
        }
        JsonNode argument = params.path(FIELD_ARGUMENT);
        String argumentName = argument.path(FIELD_NAME).asString();
        if (argumentName.isBlank()) {
            throw new IllegalArgumentException("A completion request must name the argument it completes");
        }
        McpCompletion completion = provider.complete(
                new McpCompletionRef(type, name), argumentName, argument.path(FIELD_VALUE).asString());

        ObjectNode result = Json.createObject();
        ObjectNode node = result.putObject(FIELD_COMPLETION);
        ArrayNode values = node.putArray(FIELD_VALUES);
        for (String value : completion.values()) {
            values.add(value);
        }
        node.put(FIELD_TOTAL, completion.total());
        node.put(FIELD_HAS_MORE, completion.hasMore());
        return success(id, result);
    }

    /**
     * Adds the links for a call that has just succeeded.
     * <p>
     * After the text, never instead of it: a client that ignores resource links must still get the
     * whole answer. A failure building them is swallowed for the same reason — the tool ran and
     * answered, and turning that into {@code isError} because a convenience could not be produced
     * would lose a result the model was waiting for.
     */
    private void appendResourceLinks(
            ArrayNode content, McpResourceLinker linker, String toolName, JsonNode arguments) {

        List<McpResourceLink> links;
        try {
            links = linker.linksFor(toolName, arguments);
        } catch (RuntimeException e) {
            log.warn("Could not build resource links for a successful call: tool={} message={}",
                    toolName, describe(e));
            return;
        }
        for (McpResourceLink link : links) {
            ObjectNode block = content.addObject();
            block.put(FIELD_TYPE, CONTENT_TYPE_RESOURCE_LINK);
            block.put(FIELD_URI, link.uri());
            block.put(FIELD_NAME, link.name());
            if (link.description() != null) {
                block.put(FIELD_DESCRIPTION, link.description());
            }
            if (link.mimeType() != null) {
                block.put(FIELD_MIME_TYPE, link.mimeType());
            }
        }
    }

    private JsonNode promptsList(JsonNode id, McpPromptProvider provider) {
        ObjectNode result = Json.createObject();
        ArrayNode prompts = result.putArray(FIELD_PROMPTS);
        for (McpPrompt prompt : provider.prompts()) {
            ObjectNode node = prompts.addObject();
            node.put(FIELD_NAME, prompt.name());
            node.put(FIELD_TITLE, prompt.title());
            node.put(FIELD_DESCRIPTION, prompt.description());
            ArrayNode arguments = node.putArray(FIELD_ARGUMENTS);
            for (McpPrompt.Argument argument : prompt.arguments()) {
                arguments.addObject()
                        .put(FIELD_NAME, argument.name())
                        .put(FIELD_DESCRIPTION, argument.description())
                        .put(FIELD_REQUIRED, argument.required());
            }
        }
        return success(id, result);
    }

    private JsonNode promptsGet(JsonNode id, McpPromptProvider provider, JsonNode params) {
        McpPrompt prompt = provider.prompt(params.path(FIELD_NAME).asString());
        ObjectNode result = Json.createObject();
        result.put(FIELD_DESCRIPTION, prompt.description());
        ObjectNode message = result.putArray(FIELD_MESSAGES).addObject();
        message.put(FIELD_ROLE, ROLE_USER);
        message.putObject(FIELD_CONTENT).put(FIELD_TYPE, CONTENT_TYPE_TEXT).put(FIELD_TEXT, prompt.render(params.get(FIELD_ARGUMENTS)));
        return success(id, result);
    }

    private JsonNode resourcesList(JsonNode id, McpResourceProvider provider) {
        return success(id, resourceArray(FIELD_RESOURCES, provider.resources()));
    }

    private JsonNode resourceTemplatesList(JsonNode id, McpResourceProvider provider) {
        ObjectNode result = Json.createObject();
        ArrayNode templates = result.putArray(FIELD_RESOURCE_TEMPLATES);
        for (McpResource resource : provider.templates()) {
            // The key is uriTemplate rather than uri: a template carries placeholders and is not
            // itself fetchable, and a client that reads it as a uri will try anyway.
            templates.addObject()
                    .put(FIELD_URI_TEMPLATE, resource.uri())
                    .put(FIELD_NAME, resource.name())
                    .put(FIELD_DESCRIPTION, resource.description())
                    .put(FIELD_MIME_TYPE, resource.mimeType());
        }
        return success(id, result);
    }

    /**
     * Reads one resource, answering a failure with the code the specification reserves for it.
     * <p>
     * A resource is read by running a tool, and a tool that fails is wrapped by {@link ToolInvocation}
     * before it gets here. Under a {@code tools/call} that wrapper is the answer — the model reads it
     * inside the result — but under {@code resources/read} there is no result to put it in, so the
     * cause is read back out and classified: a profile or event type that does not exist is
     * {@code -32002}, an argument the tool refused (a cursor it cannot parse) is {@code -32602}, and
     * only a failure that is neither stays the {@code -32603} it would otherwise have been. Without
     * this, every one of them was "Internal error", and a client could not tell a profile it should
     * stop asking for from a server it should stop trusting.
     */
    private JsonNode resourcesRead(JsonNode id, McpResourceProvider provider, JsonNode params) {
        String uri = params.path(FIELD_URI).asString();
        McpResourceProvider.Contents contents;
        try {
            contents = provider.read(uri);
        } catch (RuntimeException e) {
            throw classifyResourceFailure(e, uri);
        }
        ObjectNode result = Json.createObject();
        result.putArray(FIELD_CONTENTS).addObject()
                .put(FIELD_URI, contents.uri())
                .put(FIELD_MIME_TYPE, contents.mimeType())
                .put(FIELD_TEXT, contents.text());
        return success(id, result);
    }

    /**
     * The exception {@link #dispatchOne} should see for a failed resource read: the cause underneath a
     * tool-execution wrapper when there is one, translated to {@link McpResourceNotFoundException} when
     * it is a not-found error of Jeffrey's own, and otherwise left as it is so the existing mapping —
     * {@link IllegalArgumentException} to {@code -32602}, anything else to {@code -32603} — applies
     * to the failure itself rather than to the wrapper around it.
     */
    private RuntimeException classifyResourceFailure(RuntimeException failure, String uri) {
        Throwable cause = unwrapToolFailure(failure);
        if (cause instanceof McpResourceNotFoundException notFound) {
            return notFound;
        }
        if (cause instanceof JeffreyException jeffrey && jeffrey.isClientError()
                && jeffrey.getCode() != null && jeffrey.getCode().isNotFound()) {
            return new McpResourceNotFoundException(describe(jeffrey), jeffrey);
        }
        if (cause instanceof IllegalArgumentException invalid) {
            return invalid;
        }
        if (cause instanceof JeffreyException jeffrey && jeffrey.isClientError()) {
            // Named by Jeffrey as the caller's mistake, but not a missing subject: an argument the
            // tool refused reads as invalid params rather than as a fault of the server's.
            return new ToolDispatchException(describe(jeffrey));
        }
        if (cause != failure && cause instanceof RuntimeException runtime) {
            log.warn("Resource read failed underneath its tool: uri={} message={}", uri, describe(runtime));
            return runtime;
        }
        return failure;
    }

    /** The exception a tool threw, when the failure is the wrapper {@link ToolInvocation} puts around it. */
    private static Throwable unwrapToolFailure(Throwable failure) {
        if (failure instanceof ToolInvocationException invocation) {
            return invocation.getCause();
        }
        return failure;
    }

    /**
     * The words a client may be given for a failure: the exception's own when it is one the caller
     * can act on, and {@link #INTERNAL_FAILURE_MESSAGE} for everything else. A tool's failure is
     * read through the wrapper {@link ToolInvocation} puts around it, so the judgement is made on
     * what the tool threw rather than on the wrapper's type.
     */
    private static String clientMessage(Throwable failure) {
        Throwable cause = unwrapToolFailure(failure);
        return callerActionable(cause) ? describe(cause) : INTERNAL_FAILURE_MESSAGE;
    }

    /**
     * Whether a failure is about the request rather than the server: an argument a tool refused, a
     * refusal a tool wrote for the model, a resource that is not there, or a condition Jeffrey names
     * as a client error — a profile that does not exist, a feature this recording did not enable. Each
     * of those is a sentence the caller can act on. A {@code JeffreyException} carrying a code is not
     * enough on its own: an internal one carries a code too, and the paths that reach here with one
     * name host file paths. Everything else — a null where a value was expected,
     * a driver that gave up — is not, and its words would only tell an outsider how the server is built.
     */
    private static boolean callerActionable(Throwable failure) {
        return failure instanceof IllegalArgumentException
                || failure instanceof ToolExecutionException
                || failure instanceof McpResourceNotFoundException
                || (failure instanceof JeffreyException jeffrey && jeffrey.isClientError());
    }

    /**
     * An exception's message, or its type when it has none. An answer that reads "Error: null" tells the
     * model nothing, and a client reads the word as data.
     */
    private static String describe(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static ObjectNode resourceArray(String field, List<McpResource> resources) {
        ObjectNode result = Json.createObject();
        ArrayNode array = result.putArray(field);
        for (McpResource resource : resources) {
            array.addObject()
                    .put(FIELD_URI, resource.uri())
                    .put(FIELD_NAME, resource.name())
                    .put(FIELD_DESCRIPTION, resource.description())
                    .put(FIELD_MIME_TYPE, resource.mimeType());
        }
        return result;
    }

    protected final ResponseEntity<JsonNode> parseErrorResponse() {
        return ResponseEntity.badRequest().body(error(null, -32700, "Parse error: invalid JSON"));
    }

    private JsonNode success(JsonNode id, JsonNode result) {
        ObjectNode response = Json.createObject();
        response.put(FIELD_JSONRPC, JSONRPC_VERSION);
        response.set(FIELD_ID, id);
        response.set(FIELD_RESULT, result);
        return response;
    }

    private JsonNode error(JsonNode id, int code, String message) {
        ObjectNode response = Json.createObject();
        response.put(FIELD_JSONRPC, JSONRPC_VERSION);
        response.set(FIELD_ID, id);
        ObjectNode error = response.putObject(FIELD_ERROR);
        error.put(FIELD_CODE, code);
        error.put(FIELD_MESSAGE, message == null ? "" : message);
        return response;
    }
}
