/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.hub.core.mcp;

import cafe.jeffrey.profile.mcp.*;
import cafe.jeffrey.shared.common.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Opt-in direct Hub MCP endpoint for agent clients. No Microscope server is required. */
@RestController
@RequestMapping("/api/mcp")
@ConditionalOnProperty(name = "jeffrey.hub.mcp.enabled", havingValue = "true")
public class HubMcpController extends AbstractMcpStreamableHttpController {
    private final McpServerFeatures features;
    private final Set<String> allowedHosts;

    public HubMcpController(HubActivityService service,
            @Value("${jeffrey.hub.mcp.allowed-hosts:localhost,127.0.0.1,::1}") String allowedHosts) {
        this.allowedHosts = Arrays.stream(allowedHosts.split(",")).map(HubMcpController::host).collect(Collectors.toSet());
        var tools = new ReflectiveToolset(new HubActivityMcpTools(service), "hub");
        features = new McpServerFeatures(() -> tools, () -> new McpPromptProvider() {
            public List<McpPrompt> prompts() { return List.of(); }
            public McpPrompt prompt(String name) { throw new IllegalArgumentException("Unknown prompt"); }
        }, () -> new McpResourceProvider() {
            public List<McpResource> resources() { return List.of(); }
            public List<McpResource> templates() { return List.of(); }
            public Contents read(String uri) { throw new IllegalArgumentException("Unknown resource"); }
        });
    }

    @PostMapping
    public ResponseEntity<JsonNode> handle(@RequestBody JsonNode request, HttpServletRequest http) {
        if (!allowed(http)) {
            return refusal();
        }
        return dispatch(request, http.getHeader("MCP-Protocol-Version"), features);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<JsonNode> malformedJson(HttpServletRequest http) {
        return allowed(http) ? parseErrorResponse() : refusal();
    }

    private boolean allowed(HttpServletRequest http) {
        // This endpoint serves native agent clients. Browser-origin requests are not supported.
        return allowedHosts.contains(host(http.getServerName())) && http.getHeader("Origin") == null;
    }

    private static ResponseEntity<JsonNode> refusal() {
        return ResponseEntity.status(403).body(Json.createObject().put("error", "Untrusted host or browser Origin"));
    }

    private static String host(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("[") && normalized.endsWith("]")
                ? normalized.substring(1, normalized.length() - 1) : normalized;
    }
}
