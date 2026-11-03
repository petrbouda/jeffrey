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

package cafe.jeffrey.microscope.core;

import cafe.jeffrey.microscope.core.mcp.tools.UiLinkRoutes;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Talks to a booted Microscope over HTTP the way its two clients do: an agent through
 * {@code POST /api/mcp}, the UI through {@code /api/internal/**}. Every structured answer is checked
 * against the {@code outputSchema} the server advertises for its tool, as a client that validates
 * them would.
 */
final class MicroscopeClient {

    private static final String MCP_PATH = "/api/mcp";
    private static final String TOOLS_LIST = "tools/list";
    private static final String UI_LINK = "uiLink";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String ACCEPT = "Accept";
    private static final String JSON = "application/json";
    private static final String MCP_ACCEPT = "application/json, text/event-stream";
    private static final int HTTP_OK = 200;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private final String base;
    private final Map<String, JsonNode> advertised = new LinkedHashMap<>();

    MicroscopeClient(int port) {
        this.base = "http://localhost:" + port;
    }

    /** A tool's structured answer, which must not be a tool error and must match its advertised schema. */
    JsonNode answer(String tool, ObjectNode arguments) {
        JsonNode result = result(tool, arguments);
        assertFalse(result.path("isError").asBoolean(), () -> tool + " answered an error: " + result);
        return result.path("structuredContent");
    }

    /**
     * A tool's result as it comes — an answer or a tool error. An answer must still match the
     * schema the tool advertises.
     */
    JsonNode result(String tool, ObjectNode arguments) {
        JsonNode result = call(tool, arguments);
        if (!result.path("isError").asBoolean() && advertisedTool(tool).has("outputSchema")) {
            JsonNode structured = result.path("structuredContent");
            assertTrue(structured.isObject(), () -> tool + " answered no structuredContent: " + result);
            McpSchemaConformance.assertConforms(structured, advertisedTool(tool).path("outputSchema"));
        }
        return result;
    }

    /** Every page an answer links, its rows' links included, is one the UI serves. */
    static void assertLinksResolve(JsonNode node) {
        if (node.isObject()) {
            JsonNode link = node.path(UI_LINK);
            if (link.isString()) {
                UiLinkRoutes.assertResolves(link.asString());
            }
            node.properties().forEach(field -> assertLinksResolve(field.getValue()));
        } else if (node.isArray()) {
            node.forEach(MicroscopeClient::assertLinksResolve);
        }
    }

    /** Every tool the server advertises, by name, as {@code tools/list} describes it. */
    Map<String, JsonNode> tools() {
        if (advertised.isEmpty()) {
            for (JsonNode listed : rpc(McpTestRequests.request(TOOLS_LIST)).path("tools")) {
                advertised.put(listed.path("name").asString(), listed);
            }
        }
        return advertised;
    }

    /** The text of a tool error, which the call must answer. */
    String refusal(String tool, ObjectNode arguments) {
        JsonNode result = call(tool, arguments);
        assertTrue(result.path("isError").asBoolean(), () -> tool + " answered no error: " + result);
        return result.path("content").get(0).path("text").asString();
    }

    /** A UI read, which must answer 200 with JSON. */
    JsonNode get(String path) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri(path)).GET());
        assertEquals(HTTP_OK, response.statusCode(), () -> "GET " + path + ": " + response.body());
        return Json.readTree(response.body());
    }

    /** A UI change, answered as it comes. */
    HttpResponse<String> delete(String path) {
        return send(HttpRequest.newBuilder(uri(path)).DELETE());
    }

    private JsonNode call(String tool, ObjectNode arguments) {
        return rpc(McpTestRequests.toolCall(tool, arguments));
    }

    private JsonNode advertisedTool(String tool) {
        JsonNode listed = tools().get(tool);
        assertTrue(listed != null, () -> tool + " is not advertised");
        return listed;
    }

    private JsonNode rpc(McpTestRequests.Request request) {
        HttpRequest.Builder http = HttpRequest.newBuilder(uri(MCP_PATH))
                .header(CONTENT_TYPE, JSON)
                .header(ACCEPT, MCP_ACCEPT)
                .POST(HttpRequest.BodyPublishers.ofString(request.json()));
        request.httpHeaders().forEach(http::header);
        HttpResponse<String> response = send(http);
        assertEquals(HTTP_OK, response.statusCode(), () -> "POST " + MCP_PATH + ": " + response.body());
        JsonNode body = Json.readTree(response.body());
        assertFalse(body.has("error"), () -> "JSON-RPC error: " + body);
        return body.path("result");
    }

    private URI uri(String path) {
        return URI.create(base + path);
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return HTTP.send(request.timeout(TIMEOUT).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling Microscope", e);
        }
    }
}
