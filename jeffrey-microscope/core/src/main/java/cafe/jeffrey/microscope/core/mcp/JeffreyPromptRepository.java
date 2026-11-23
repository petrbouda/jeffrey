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
import cafe.jeffrey.microscope.mcp.protocol.McpPrompt;
import cafe.jeffrey.microscope.mcp.protocol.McpPromptProvider;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.feature.McpSyncPrompt;
import io.modelcontextprotocol.modern.server.feature.McpSyncPromptRepository;
import io.modelcontextprotocol.modern.server.feature.PromptsPage;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * Serves the modern server's {@code prompts/list} and {@code prompts/get} from Jeffrey's
 * {@link McpPromptProvider} ({@code McpPromptRegistry} — the skill bodies), replacing the SDK's
 * in-memory prompt repository. Each prompt renders to a single user message, the skill body with its
 * {@code $ARGUMENTS}/{@code $<name>} placeholders filled from the supplied arguments. Argument
 * completion is not wired here.
 */
public final class JeffreyPromptRepository implements McpSyncPromptRepository {

    private final McpPromptProvider prompts;

    public JeffreyPromptRepository(McpPromptProvider prompts) {
        this.prompts = prompts;
    }

    @Override
    public PromptsPage list(McpRequestContext context, String cursor) {
        return PromptsPage.of(prompts.prompts().stream().map(JeffreyPromptRepository::toPrompt).toList());
    }

    @Override
    public McpSyncPrompt find(McpRequestContext context, String name) {
        McpPrompt prompt;
        try {
            prompt = prompts.prompt(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return McpSyncPrompt.of(toPrompt(prompt), (requestContext, request) -> get(prompt, request));
    }

    private McpSyncResponse<McpSchema.GetPromptOutcome> get(McpPrompt prompt, McpSchema.GetPromptRequest request) {
        try {
            String body = prompt.render(suppliedArguments(request.arguments()));
            McpSchema.GetPromptResult result = McpSchema.GetPromptResult.builder(List.of(
                    new McpSchema.PromptMessage(McpSchema.Role.USER, McpSchema.TextContent.builder(body).build())))
                    .description(prompt.description())
                    .build();
            return McpSyncResponse.result(result);
        } catch (IllegalArgumentException e) {
            throw McpException.invalidParams(e.getMessage());
        }
    }

    private static ObjectNode suppliedArguments(Map<String, String> arguments) {
        ObjectNode supplied = Json.createObject();
        if (arguments != null) {
            arguments.forEach(supplied::put);
        }
        return supplied;
    }

    private static McpSchema.Prompt toPrompt(McpPrompt prompt) {
        List<McpSchema.PromptArgument> arguments = prompt.arguments().stream()
                .map(argument -> new McpSchema.PromptArgument(
                        argument.name(), null, argument.description(), argument.required()))
                .toList();
        return McpSchema.Prompt.builder(prompt.name())
                .title(prompt.title())
                .description(prompt.description())
                .arguments(arguments)
                .build();
    }
}
