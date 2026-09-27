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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The MCP Streamable-HTTP (JSON-RPC 2.0) envelope, with no transport underneath it: it takes the parsed
 * body and the transport headers and answers with an HTTP status and a JSON body. It owns request
 * validation, the method table, {@code server/discover}, tools, prompts, resources, completions, tasks,
 * skills, and the success/error response shape, so a server only maps its own transport onto
 * {@link #dispatch(JsonNode, McpTransportHeaders, McpServerFeatures)} and says what it serves.
 * <p>
 * It speaks {@code 2026-07-28} only: stateless, every request carrying its own revision and client
 * capabilities in {@code params._meta}. There is no {@code initialize}, no {@code ping} and no
 * batching; a client from the handshake era sends no {@code _meta} and is refused as malformed
 * ({@code -32602}, 400) with a message and {@code data.supported} naming the revision it would have to
 * speak.
 * <p>
 * What is the server's rather than the protocol's — its identity, the most a result may carry, how its
 * own exceptions read, and who counts its tool calls — comes in the {@link McpDispatcherSettings}.
 * Every provider inside the features is invoked lazily, only for the methods that need it.
 */
public final class McpDispatcher {

    private static final String JSONRPC_VERSION = "2.0";

    private static final String METHOD_SERVER_DISCOVER = "server/discover";
    private static final String METHOD_TOOLS_LIST = "tools/list";
    private static final String METHOD_TOOLS_CALL = "tools/call";
    private static final String METHOD_PROMPTS_LIST = "prompts/list";
    private static final String METHOD_PROMPTS_GET = "prompts/get";
    private static final String METHOD_RESOURCES_LIST = "resources/list";
    private static final String METHOD_RESOURCES_TEMPLATES_LIST = "resources/templates/list";
    private static final String METHOD_RESOURCES_READ = "resources/read";
    private static final String METHOD_COMPLETION_COMPLETE = "completion/complete";
    private static final String METHOD_TASKS_GET = "tasks/get";
    private static final String METHOD_TASKS_UPDATE = "tasks/update";
    private static final String METHOD_TASKS_CANCEL = "tasks/cancel";
    private static final String METHOD_SKILLS_LIST = "skills/list";
    private static final String METHOD_SKILLS_GET = "skills/get";

    private static final String FIELD_JSONRPC = "jsonrpc";
    private static final String FIELD_ID = "id";
    private static final String FIELD_RESULT = "result";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_DATA = "data";
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
    private static final String FIELD_SUPPORTED_VERSIONS = "supportedVersions";
    private static final String FIELD_CAPABILITIES = "capabilities";
    private static final String FIELD_EXTENSIONS = "extensions";
    private static final String FIELD_TOOLS = "tools";
    private static final String FIELD_PROMPTS = "prompts";
    private static final String FIELD_RESOURCES = "resources";
    private static final String FIELD_RESOURCE_TEMPLATES = "resourceTemplates";
    private static final String FIELD_INPUT_SCHEMA = "inputSchema";
    private static final String FIELD_OUTPUT_SCHEMA = "outputSchema";
    private static final String FIELD_STRUCTURED_CONTENT = "structuredContent";
    private static final String FIELD_ANNOTATIONS = "annotations";
    private static final String FIELD_META = "_meta";
    private static final String FIELD_LIST_CHANGED = "listChanged";
    private static final String FIELD_SUBSCRIBE = "subscribe";
    private static final String FIELD_IS_ERROR = "isError";
    private static final String FIELD_READ_ONLY_HINT = "readOnlyHint";
    private static final String FIELD_DESTRUCTIVE_HINT = "destructiveHint";
    private static final String FIELD_IDEMPOTENT_HINT = "idempotentHint";
    private static final String FIELD_OPEN_WORLD_HINT = "openWorldHint";

    private static final String FIELD_INSTRUCTIONS = "instructions";
    private static final String FIELD_COMPLETIONS = "completions";
    private static final String FIELD_COMPLETION = "completion";
    private static final String FIELD_VALUES = "values";
    private static final String FIELD_TOTAL = "total";
    private static final String FIELD_HAS_MORE = "hasMore";
    private static final String FIELD_REF = "ref";
    private static final String FIELD_ARGUMENT = "argument";
    private static final String FIELD_VALUE = "value";
    private static final String FIELD_TASK_ID = "taskId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_STATUS_MESSAGE = "statusMessage";
    private static final String FIELD_SKILLS = "skills";
    private static final String FIELD_SKILL = "skill";

    private static final String CONTENT_TYPE_RESOURCE_LINK = "resource_link";
    private static final String CONTENT_TYPE_TEXT = "text";
    private static final String ROLE_USER = "user";
    private static final String TOOL_ERROR_PREFIX = "Error: ";

    private static final String PARSE_ERROR_MESSAGE = "Parse error: invalid JSON";
    private static final String UNDECLARED_TASKS =
            "The tool deferred to a task for a client that did not declare the tasks extension: ";
    private static final String MISSING_TASK_ID = "A tasks request must name its taskId";
    private static final String MISSING_SKILL_URI = "skills/get must name the uri of the skill's SKILL.md";
    private static final String UNKNOWN_SKILL_FILE = "No skill file is served at %s; skills/list names every file";

    /** The refusals a client from another revision gets: missing {@code _meta}, or a version it does not speak. */
    private static final Set<McpErrorCode> INCOMPATIBLE_CLIENT_REFUSALS =
            Set.of(McpErrorCode.INVALID_META, McpErrorCode.UNSUPPORTED_VERSION);

    private static final Logger LOG = LoggerFactory.getLogger(McpDispatcher.class);

    private final McpDispatcherSettings settings;
    private final McpFailurePolicy failures;
    private final McpResults results;

    /**
     * Every method this server answers. A method outside it is {@code -32601} with a 404, which is what
     * {@code initialize}, {@code ping} and {@code logging/setLevel} now get. The {@code tasks/*} methods
     * are answered only for a client that declared the tasks extension ({@link McpRequestValidator}
     * refuses the others with {@code -32021}). The {@code skills/*} methods are answered for every
     * client, whether or not it declared the skills extension; an endpoint serving no skills lists none.
     */
    private final Map<String, McpMethodHandler> methods = Map.ofEntries(
            Map.entry(METHOD_SERVER_DISCOVER, this::discover),
            Map.entry(METHOD_TOOLS_LIST, this::toolsList),
            Map.entry(METHOD_TOOLS_CALL, this::toolsCall),
            Map.entry(METHOD_PROMPTS_LIST, this::promptsList),
            Map.entry(METHOD_PROMPTS_GET, this::promptsGet),
            Map.entry(METHOD_RESOURCES_LIST, this::resourcesList),
            Map.entry(METHOD_RESOURCES_TEMPLATES_LIST, this::resourceTemplatesList),
            Map.entry(METHOD_RESOURCES_READ, this::resourcesRead),
            Map.entry(METHOD_COMPLETION_COMPLETE, this::completionComplete),
            Map.entry(METHOD_TASKS_GET, this::tasksGet),
            Map.entry(METHOD_TASKS_UPDATE, this::tasksUpdate),
            Map.entry(METHOD_TASKS_CANCEL, this::tasksCancel),
            Map.entry(METHOD_SKILLS_LIST, this::skillsList),
            Map.entry(METHOD_SKILLS_GET, this::skillsGet));

    private final McpRequestValidator validator = new McpRequestValidator(methods.keySet());

    public McpDispatcher(McpDispatcherSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.failures = settings.failures();
        this.results = new McpResults(settings.server());
    }

    /**
     * Validates one request and answers it from the method table. The HTTP status of a refusal comes
     * from its code ({@link McpErrorCode#httpStatus()}); a notification is accepted with {@code 202}
     * and no body. Each provider is resolved only by the handler that needs it, so a failure building
     * the toolset cannot stop the endpoint answering {@code server/discover}.
     *
     * @param headers the transport headers the request arrived with
     */
    public McpResponse dispatch(JsonNode body, McpTransportHeaders headers, McpServerFeatures features) {

        McpRequestContext request;
        try {
            request = validator.validate(body, Objects.requireNonNullElse(headers, McpTransportHeaders.NONE));
        } catch (McpProtocolException e) {
            logRefusal(e);
            return failure(McpRequestValidator.idOf(body), e.code(), e.getMessage(), e.data());
        }
        if (request.isNotification()) {
            return McpResponse.ACCEPTED;
        }

        JsonNode id = request.id();
        String method = request.method();
        try {
            return McpResponse.ok(success(id, methods.get(method).handle(request, features)));
        } catch (McpProtocolException e) {
            LOG.warn("Invalid MCP request: method={} message={}", method, e.getMessage());
            return failure(id, e.code(), e.getMessage(), e.data());
        } catch (McpResourceNotFoundException e) {
            LOG.warn("MCP resource not found: method={} message={}", method, e.getMessage());
            return failure(id, McpErrorCode.INVALID_PARAMS, describe(e), null);
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid MCP request: method={} message={}", method, e.getMessage());
            return failure(id, McpErrorCode.INVALID_PARAMS, describe(e), null);
        } catch (Exception e) {
            LOG.error("MCP request failed: method={} message={}", method, e.getMessage(), e);
            return failure(id, McpErrorCode.INTERNAL, clientMessage(e), null);
        }
    }

    /**
     * A client from the handshake era, or one on a revision this server does not speak, is routine
     * rather than wrong, and it cannot fall forward on its own, so its refusal is logged where someone
     * wondering why a client never connects will find it. Every other refusal names the fix in its
     * answer and is only worth a debug line.
     */
    private void logRefusal(McpProtocolException refusal) {
        if (INCOMPATIBLE_CLIENT_REFUSALS.contains(refusal.code())) {
            LOG.info("An MCP client sent a request this server cannot speak: code={} message={}",
                    refusal.code().code(), refusal.getMessage());
            return;
        }
        LOG.debug("An MCP request was refused before dispatch: code={} message={}",
                refusal.code().code(), refusal.getMessage());
    }

    /**
     * What this server is, before anything else is asked. Never resolves the toolset — the invariant
     * that used to protect {@code initialize}: a client must learn what the server speaks even when the
     * toolset cannot be assembled.
     */
    private ObjectNode discover(McpRequestContext request, McpServerFeatures features) {
        ObjectNode result = McpJson.createObject();
        ArrayNode versions = result.putArray(FIELD_SUPPORTED_VERSIONS);
        McpProtocolVersions.SUPPORTED.forEach(versions::add);
        ObjectNode capabilities = result.putObject(FIELD_CAPABILITIES);
        capabilities.putObject(FIELD_TOOLS).put(FIELD_LIST_CHANGED, false);
        capabilities.putObject(FIELD_PROMPTS).put(FIELD_LIST_CHANGED, false);
        capabilities.putObject(FIELD_RESOURCES).put(FIELD_SUBSCRIBE, false).put(FIELD_LIST_CHANGED, false);
        // Declared only when this endpoint actually has a provider. A capability that is advertised and
        // then answers nothing is worse than one that was never offered: a client builds a picker on it.
        if (features.completions().get() != McpCompletionProvider.NONE) {
            capabilities.putObject(FIELD_COMPLETIONS);
        }
        ObjectNode extensions = capabilities.putObject(FIELD_EXTENSIONS);
        if (features.tasks().get() != McpTaskProvider.NONE) {
            extensions.putObject(McpClientCapabilities.TASKS_EXTENSION);
        }
        if (features.skills().get() != McpSkillProvider.NONE) {
            extensions.putObject(McpClientCapabilities.SKILLS_EXTENSION);
        }
        // How to use a server with a hundred-odd tools, handed over before the first call rather than
        // left for the client to infer from a tool list.
        String instructions = features.instructions().get();
        if (instructions != null && !instructions.isBlank()) {
            result.put(FIELD_INSTRUCTIONS, instructions);
        }
        return results.complete(result, McpCacheHint.STATIC);
    }

    /** Every tool with its schemas; {@code outputSchema} whenever the tool declares one. */
    private ObjectNode toolsList(McpRequestContext request, McpServerFeatures features) {
        ObjectNode result = McpJson.createObject();
        ArrayNode tools = result.putArray(FIELD_TOOLS);
        for (McpToolSpec spec : features.tools().get().specs()) {
            ObjectNode tool = tools.addObject();
            tool.put(FIELD_NAME, spec.name());
            if (spec.title() != null && !spec.title().isBlank()) {
                tool.put(FIELD_TITLE, spec.title());
            }
            tool.put(FIELD_DESCRIPTION, spec.description());
            tool.set(FIELD_INPUT_SCHEMA, spec.inputSchema());
            if (spec.outputSchema() != null) {
                tool.set(FIELD_OUTPUT_SCHEMA, spec.outputSchema());
            }
            ObjectNode annotations = tool.putObject(FIELD_ANNOTATIONS);
            annotations.put(FIELD_READ_ONLY_HINT, spec.annotations().readOnly());
            annotations.put(FIELD_DESTRUCTIVE_HINT, spec.annotations().destructive());
            annotations.put(FIELD_IDEMPOTENT_HINT, spec.annotations().idempotent());
            annotations.put(FIELD_OPEN_WORLD_HINT, spec.annotations().openWorld());
            if (!spec.meta().isEmpty()) {
                ObjectNode meta = tool.putObject(FIELD_META);
                spec.meta().forEach(meta::set);
            }
        }
        return results.complete(result, McpCacheHint.STATIC);
    }

    /**
     * Runs one tool and renders what it answered: a result, a question for the user, or a task.
     * <p>
     * Anything the model can correct is an answer: a tool that ran and failed, and an argument that is
     * missing, mistyped or not one of the allowed values, all come back inside the result with
     * {@code isError} set, as the specification asks for input-validation errors — the model reads the
     * sentence and fixes the call, where a JSON-RPC error is something many clients never show it.
     * Only a call with nothing to answer stays a protocol error ({@code -32602}): a tool this server
     * does not advertise ({@link UnknownToolException}), arguments that are not an object at all, and
     * answers ({@code inputResponses}) that are malformed.
     * <p>
     * A tool that asks a client that cannot answer, or defers for one that cannot follow a task, was
     * told it could do neither: that is a bug in the tool, reported as an internal failure.
     * <p>
     * The text block is capped here at {@link McpDispatcherSettings#maxResultChars()}, whatever the tool
     * did, so one tool that skips the helper cannot hand a client a result it spills to a file; structured
     * content past the same limit is refused as the tool's error.
     */
    private ObjectNode toolsCall(McpRequestContext request, McpServerFeatures features) {
        ObjectNode params = request.params();
        String toolName = params.path(FIELD_NAME).asString();
        JsonNode arguments = params.get(FIELD_ARGUMENTS);
        // A malformed call, not a mistake in one value: refused before dispatch, as -32602.
        McpToolArguments.requireObject(arguments);
        McpCallContext context = request.callContext();
        McpToolProvider toolset = features.tools().get();

        boolean advertised = toolset.specs().stream().anyMatch(spec -> spec.name().equals(toolName));
        boolean dispatched = true;
        // The duration is taken in the finally, on every path out -- an Error included -- so no helper
        // that hands back an elapsed time only on success will do.
        long started = System.nanoTime();
        ObjectNode result = null;
        try {
            result = render(toolset.call(toolName, arguments, context), request, features, toolName, arguments);
        } catch (UnknownToolException e) {
            dispatched = false;
            // Rethrown so the envelope answers -32602: there is no tool whose result could carry it.
            throw e;
        } catch (Exception e) {
            result = toolError(toolName, e);
        } finally {
            if (advertised && dispatched) {
                recordCall(toolName, System.nanoTime() - started, result);
            }
        }
        return result;
    }

    /**
     * Reports one call by the result it produced. A call that ended in an {@link Error} produced none: it
     * is reported as an errored call of no bytes.
     */
    private void recordCall(String toolName, long durationNanos, ObjectNode result) {
        if (result == null) {
            settings.toolCalls().called(new McpToolCall(toolName, durationNanos, 0, true));
            return;
        }
        settings.toolCalls().called(new McpToolCall(toolName, durationNanos, McpJson.toByteArray(result).length,
                result.path(FIELD_IS_ERROR).asBoolean()));
    }

    private ObjectNode render(McpToolOutcome outcome, McpRequestContext request, McpServerFeatures features,
                              String toolName, JsonNode arguments) {
        return switch (outcome) {
            case McpToolResult output -> toolResult(output, features.resourceLinks().get(), toolName, arguments);
            case McpToolOutcome.InputRequired questions ->
                    results.inputRequired(questions, request.capabilities());
            case McpToolOutcome.Deferred deferred -> task(deferred, request, features);
        };
    }

    /**
     * A task's answer, read back later with no call to link it to. Structured content past the limit is
     * the tool's failure, rendered as {@code tools/call} renders one, so a client following a task reads
     * the sentence it would have read by waiting.
     */
    private ObjectNode taskAnswer(McpToolResult output) {
        if (exceedsLimit(output)) {
            LOG.warn("MCP task answer refused: message={}", McpToolResult.OVERSIZED_STRUCTURED_CONTENT);
            return failureResult(new IllegalArgumentException(McpToolResult.OVERSIZED_STRUCTURED_CONTENT));
        }
        return toolResult(output, McpResourceLinker.NONE, null, null);
    }

    /** Whether the structured content serialises past the result limit; a text-only result never does. */
    private boolean exceedsLimit(McpToolResult output) {
        return output.exceeds(settings.maxResultChars());
    }

    private ObjectNode toolResult(McpToolResult output, McpResourceLinker linker, String toolName, JsonNode arguments) {
        if (exceedsLimit(output)) {
            throw new IllegalArgumentException(McpToolResult.OVERSIZED_STRUCTURED_CONTENT);
        }
        ObjectNode result = McpJson.createObject();
        ArrayNode content = result.putArray(FIELD_CONTENT);
        content.addObject().put(FIELD_TYPE, CONTENT_TYPE_TEXT)
                .put(FIELD_TEXT, McpText.capped(output.text(), settings.maxResultChars()));
        appendResourceLinks(content, linker, toolName, arguments);
        if (output.hasStructuredContent()) {
            result.set(FIELD_STRUCTURED_CONTENT, output.structuredContent());
        }
        result.put(FIELD_IS_ERROR, false);
        return results.complete(result, McpCacheHint.NONE);
    }

    private ObjectNode task(McpToolOutcome.Deferred deferred, McpRequestContext request,
                            McpServerFeatures features) {
        if (!request.capabilities().extensions().contains(McpClientCapabilities.TASKS_EXTENSION)) {
            throw new IllegalStateException(UNDECLARED_TASKS + deferred.taskId());
        }
        return results.task(features.tasks().get().get(deferred.taskId()).toJson());
    }

    private ObjectNode toolError(String toolName, Exception e) {
        Throwable failure = failures.unwrap(e);
        if (callerActionable(failure)) {
            LOG.warn("MCP tool call failed: tool={} message={}", toolName, describe(failure));
        } else {
            // The client gets the fixed sentence, so this is the only place the detail survives.
            LOG.error("MCP tool call failed inside the server: tool={} message={}", toolName, describe(failure), e);
        }
        return failureResult(failure);
    }

    /**
     * The {@code isError} result for a tool that failed, in the words the caller may be given
     * ({@link #clientMessage}). Shared by {@code tools/call} and a task whose tool failed, so a client
     * following a task reads the same sentence it would have read by waiting.
     */
    private ObjectNode failureResult(Throwable failure) {
        ObjectNode result = McpJson.createObject();
        result.putArray(FIELD_CONTENT).addObject()
                .put(FIELD_TYPE, CONTENT_TYPE_TEXT)
                .put(FIELD_TEXT, TOOL_ERROR_PREFIX + clientMessage(failure));
        result.put(FIELD_IS_ERROR, true);
        return results.complete(result, McpCacheHint.NONE);
    }

    /**
     * One task as it stands: its fields and, once it has finished, the full {@code tools/call} result
     * it answered with. Never a cache hint — the task's {@code ttlMs} is its retention, and the
     * answer changes until the task finishes.
     */
    private ObjectNode tasksGet(McpRequestContext request, McpServerFeatures features) {
        McpTask task = features.tasks().get().get(taskId(request));
        ObjectNode result = task.toJson();
        attachOutcome(task, result);
        return results.complete(result, McpCacheHint.NONE);
    }

    /**
     * Adds what a finished task answered. A tool that failed is a completed task with {@code isError}
     * inside its result, exactly as {@code tools/call} renders it; only an answer that cannot be
     * rendered at all makes the task {@code failed}, carrying a JSON-RPC error instead of a result.
     */
    private void attachOutcome(McpTask task, ObjectNode fields) {
        try {
            switch (task.state()) {
                case McpTaskState.Completed completed -> fields.set(FIELD_RESULT, taskAnswer(completed.result()));
                case McpTaskState.ToolFailed failed -> fields.set(FIELD_RESULT, failureResult(failed.failure()));
                case McpTaskState.Working _, McpTaskState.Cancelled _ -> { }
            }
        } catch (RuntimeException e) {
            LOG.error("Cannot render the answer of an MCP task: taskId={} message={}",
                    task.taskId(), e.getMessage(), e);
            fields.remove(FIELD_RESULT);
            fields.remove(FIELD_STATUS_MESSAGE);
            fields.put(FIELD_STATUS, McpTaskStatus.FAILED.wireName());
            fields.set(FIELD_ERROR, errorObject(McpErrorCode.INTERNAL, failures.internalFailureMessage(), null));
        }
    }

    /**
     * Mid-flight input does not exist in this server yet, so an update only confirms the task is one
     * this endpoint follows; any {@code inputResponses} it carries are ignored.
     */
    private ObjectNode tasksUpdate(McpRequestContext request, McpServerFeatures features) {
        features.tasks().get().get(taskId(request));
        return results.complete(McpJson.createObject(), McpCacheHint.NONE);
    }

    /** Asks the work behind the task to stop and acknowledges; {@code tasks/get} reports when it has. */
    private ObjectNode tasksCancel(McpRequestContext request, McpServerFeatures features) {
        features.tasks().get().cancel(taskId(request));
        return results.complete(McpJson.createObject(), McpCacheHint.NONE);
    }

    /** The {@code taskId} a {@code tasks/*} request names; the validator has matched it to {@code Mcp-Name}. */
    private static String taskId(McpRequestContext request) {
        String taskId = request.params().path(FIELD_TASK_ID).asString();
        if (taskId.isBlank()) {
            throw new IllegalArgumentException(MISSING_TASK_ID);
        }
        return taskId;
    }

    /**
     * Every skill this endpoint serves, each entry with its complete manifest. One page, like every
     * other list here; the validator refuses a cursor.
     */
    private ObjectNode skillsList(McpRequestContext request, McpServerFeatures features) {
        ObjectNode result = McpJson.createObject();
        ArrayNode skills = result.putArray(FIELD_SKILLS);
        for (McpSkill skill : features.skills().get().skills()) {
            skills.add(skill.toJson());
        }
        return results.complete(result, McpCacheHint.STATIC);
    }

    /**
     * One skill's entry, named by the URI of its {@code SKILL.md}. A URI that names no served skill is
     * {@code -32602}, the code the specification shares with an unknown resource. No {@code Mcp-Name} is
     * demanded: the extension defines none for its methods.
     */
    private ObjectNode skillsGet(McpRequestContext request, McpServerFeatures features) {
        String uri = request.params().path(FIELD_URI).asString();
        if (uri.isBlank()) {
            throw new IllegalArgumentException(MISSING_SKILL_URI);
        }
        ObjectNode result = McpJson.createObject();
        result.set(FIELD_SKILL, features.skills().get().skill(uri).toJson());
        return results.complete(result, McpCacheHint.STATIC);
    }

    /**
     * Answers {@code completion/complete} for one argument of a resource template or a prompt.
     * <p>
     * A reference this server does not recognise is {@code -32602} rather than an empty list: an empty
     * completion means "nothing matches what you typed", and a client cannot tell that apart from
     * "you asked about something that is not here" unless the second one is an error.
     */
    private ObjectNode completionComplete(McpRequestContext request, McpServerFeatures features) {
        ObjectNode params = request.params();
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
        McpCompletion completion = features.completions().get().complete(
                new McpCompletionRef(type, name), argumentName, argument.path(FIELD_VALUE).asString());

        ObjectNode result = McpJson.createObject();
        ObjectNode node = result.putObject(FIELD_COMPLETION);
        ArrayNode values = node.putArray(FIELD_VALUES);
        for (String value : completion.values()) {
            values.add(value);
        }
        node.put(FIELD_TOTAL, completion.total());
        node.put(FIELD_HAS_MORE, completion.hasMore());
        return results.complete(result, McpCacheHint.NONE);
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
            LOG.warn("Could not build resource links for a successful call: tool={} message={}",
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

    private ObjectNode promptsList(McpRequestContext request, McpServerFeatures features) {
        ObjectNode result = McpJson.createObject();
        ArrayNode prompts = result.putArray(FIELD_PROMPTS);
        for (McpPrompt prompt : features.prompts().get().prompts()) {
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
        return results.complete(result, McpCacheHint.STATIC);
    }

    private ObjectNode promptsGet(McpRequestContext request, McpServerFeatures features) {
        ObjectNode params = request.params();
        McpPrompt prompt = features.prompts().get().prompt(params.path(FIELD_NAME).asString());
        ObjectNode result = McpJson.createObject();
        result.put(FIELD_DESCRIPTION, prompt.description());
        ObjectNode message = result.putArray(FIELD_MESSAGES).addObject();
        message.put(FIELD_ROLE, ROLE_USER);
        message.putObject(FIELD_CONTENT)
                .put(FIELD_TYPE, CONTENT_TYPE_TEXT)
                .put(FIELD_TEXT, prompt.render(params.get(FIELD_ARGUMENTS)));
        return results.complete(result, McpCacheHint.NONE);
    }

    private ObjectNode resourcesList(McpRequestContext request, McpServerFeatures features) {
        return results.complete(resourceArray(FIELD_RESOURCES, features.resources().get().resources()),
                McpCacheHint.STATIC);
    }

    private ObjectNode resourceTemplatesList(McpRequestContext request, McpServerFeatures features) {
        ObjectNode result = McpJson.createObject();
        ArrayNode templates = result.putArray(FIELD_RESOURCE_TEMPLATES);
        for (McpResource resource : features.resources().get().templates()) {
            // The key is uriTemplate rather than uri: a template carries placeholders and is not
            // itself fetchable, and a client that reads it as a uri will try anyway.
            templates.addObject()
                    .put(FIELD_URI_TEMPLATE, resource.uri())
                    .put(FIELD_NAME, resource.name())
                    .put(FIELD_DESCRIPTION, resource.description())
                    .put(FIELD_MIME_TYPE, resource.mimeType());
        }
        return results.complete(result, McpCacheHint.STATIC);
    }

    /**
     * Reads one resource, answering a failure with the code that fits it, and carrying the cache hint
     * the contents declare.
     * <p>
     * A resource may be read by running a tool, and a tool that fails may reach here inside a wrapper the
     * server puts around it ({@link McpFailurePolicy#unwrap}). Under a {@code tools/call} that failure is
     * the answer — the model reads it inside the result — but under {@code resources/read} there is no
     * result to put it in, so the cause is read back out and classified: a subject that does not exist,
     * and an argument the tool refused (a cursor it cannot parse), are both {@code -32602} with the tool's
     * own sentence ({@code 2026-07-28} forbids the older {@code -32002}), and only a failure that is
     * neither stays the {@code -32603} it would otherwise have been. Without this, every one of them was
     * "Internal error", and a client could not tell a subject it should stop asking for from a server it
     * should stop trusting.
     */
    private ObjectNode resourcesRead(McpRequestContext request, McpServerFeatures features) {
        String uri = request.params().path(FIELD_URI).asString();
        McpResourceProvider.Contents contents = skillFile(uri, features)
                .orElseGet(() -> readResource(uri, features));
        ObjectNode result = McpJson.createObject();
        result.putArray(FIELD_CONTENTS).addObject()
                .put(FIELD_URI, contents.uri())
                .put(FIELD_MIME_TYPE, contents.mimeType())
                .put(FIELD_TEXT, contents.text());
        return results.complete(result, contents.cacheHint());
    }

    /**
     * A file of a served skill, answered before the resources provider is asked: the skills' files are
     * readable but not listed there. A {@code skill://} URI no manifest lists is {@code -32602} at once,
     * without building the resources any other read needs.
     */
    private static Optional<McpResourceProvider.Contents> skillFile(String uri, McpServerFeatures features) {
        Optional<McpSkillFile> file = features.skills().get().file(uri);
        if (file.isEmpty() && uri.startsWith(McpSkill.SCHEME)) {
            throw new McpResourceNotFoundException(UNKNOWN_SKILL_FILE.formatted(uri));
        }
        return file.map(found -> new McpResourceProvider.Contents(
                found.uri(), found.mimeType(), found.text(), McpCacheHint.STATIC));
    }

    private McpResourceProvider.Contents readResource(String uri, McpServerFeatures features) {
        try {
            return features.resources().get().read(uri);
        } catch (RuntimeException e) {
            throw classifyResourceFailure(e, uri);
        }
    }

    /**
     * The exception {@link #dispatch} should see for a failed resource read: the cause underneath the
     * server's tool wrapper when there is one, translated to {@link McpResourceNotFoundException} when the
     * server names it a missing subject, and otherwise left as it is so the existing mapping —
     * {@link IllegalArgumentException} to {@code -32602}, anything else to {@code -32603} — applies
     * to the failure itself rather than to the wrapper around it.
     */
    private RuntimeException classifyResourceFailure(RuntimeException failure, String uri) {
        Throwable cause = failures.unwrap(failure);
        if (cause instanceof McpResourceNotFoundException notFound) {
            return notFound;
        }
        McpFailurePolicy.Kind kind = failures.classify(cause);
        if (kind == McpFailurePolicy.Kind.NOT_FOUND) {
            return new McpResourceNotFoundException(describe(cause), cause);
        }
        if (cause instanceof IllegalArgumentException invalid) {
            return invalid;
        }
        if (kind == McpFailurePolicy.Kind.CALLER_ERROR) {
            // Named by the server as the caller's mistake, but not a missing subject: an argument the
            // tool refused reads as invalid params rather than as a fault of the server's.
            return new ToolDispatchException(describe(cause));
        }
        if (cause != failure && cause instanceof RuntimeException runtime) {
            LOG.warn("Resource read failed underneath its tool: uri={} message={}", uri, describe(runtime));
            return runtime;
        }
        return failure;
    }

    /**
     * The words a client may be given for a failure: the exception's own when it is one the caller
     * can act on, and the server's internal-failure sentence for everything else. A tool's failure is
     * read through the wrapper the server puts around it, so the judgement is made on what the tool
     * threw rather than on the wrapper's type.
     */
    private String clientMessage(Throwable failure) {
        Throwable cause = failures.unwrap(failure);
        return callerActionable(cause) ? describe(cause) : failures.internalFailureMessage();
    }

    /**
     * Whether a failure is about the request rather than the server: an argument a tool refused, a
     * refusal a tool wrote for the model, a resource that is not there, or a condition the server names
     * as the caller's ({@link McpFailurePolicy#classify}). Each of those is a sentence the caller can act
     * on. Everything else — a null where a value was expected, a driver that gave up — is not, and its
     * words would only tell an outsider how the server is built.
     */
    private boolean callerActionable(Throwable failure) {
        return failure instanceof IllegalArgumentException
                || failure instanceof ToolExecutionException
                || failure instanceof McpResourceNotFoundException
                || failures.classify(failure) != McpFailurePolicy.Kind.UNRECOGNISED;
    }

    /**
     * An exception's message, or its type when it has none. An answer that reads "Error: null" tells the
     * model nothing, and a client reads the word as data.
     */
    private static String describe(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static ObjectNode resourceArray(String field, List<McpResource> resources) {
        ObjectNode result = McpJson.createObject();
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

    /** The answer to a body that is not JSON at all, which only the transport can notice. */
    public McpResponse parseError() {
        return failure(null, McpErrorCode.PARSE_ERROR, PARSE_ERROR_MESSAGE, null);
    }

    private static JsonNode success(JsonNode id, JsonNode result) {
        ObjectNode response = McpJson.createObject();
        response.put(FIELD_JSONRPC, JSONRPC_VERSION);
        response.set(FIELD_ID, id);
        response.set(FIELD_RESULT, result);
        return response;
    }

    /**
     * An error answer with the HTTP status its code carries.
     *
     * @param data the {@code error.data} the code defines, or null to send none
     */
    private static McpResponse failure(JsonNode id, McpErrorCode code, String message, JsonNode data) {
        ObjectNode response = McpJson.createObject();
        response.put(FIELD_JSONRPC, JSONRPC_VERSION);
        response.set(FIELD_ID, id);
        response.set(FIELD_ERROR, errorObject(code, message, data));
        return new McpResponse(code.httpStatus(), response);
    }

    /** A JSON-RPC error object: {@code code}, {@code message} and, when given, {@code data}. */
    private static ObjectNode errorObject(McpErrorCode code, String message, JsonNode data) {
        ObjectNode error = McpJson.createObject();
        error.put(FIELD_CODE, code.code());
        error.put(FIELD_MESSAGE, message == null ? "" : message);
        if (data != null) {
            error.set(FIELD_DATA, data);
        }
        return error;
    }
}
