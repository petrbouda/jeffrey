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

import cafe.jeffrey.microscope.mcp.protocol.McpResource;
import cafe.jeffrey.microscope.mcp.protocol.McpResourceNotFoundException;
import cafe.jeffrey.microscope.mcp.protocol.McpResourceProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpSkill;
import cafe.jeffrey.microscope.mcp.protocol.McpSkillFile;
import cafe.jeffrey.microscope.mcp.protocol.McpSkillProvider;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.McpSyncResponse;
import io.modelcontextprotocol.modern.server.feature.McpSyncResource;
import io.modelcontextprotocol.modern.server.feature.McpSyncResourceRepository;
import io.modelcontextprotocol.modern.server.feature.ResourceTemplatesPage;
import io.modelcontextprotocol.modern.server.feature.ResourcesPage;

import java.util.List;
import java.util.Optional;

/**
 * Serves the modern server's {@code resources/list}, {@code resources/templates/list} and
 * {@code resources/read} from Jeffrey's {@link McpResources} ({@code jeffrey://}) and the skills'
 * {@code skill://} files, replacing the SDK's in-memory resource repository. Like the tool repository,
 * this wraps the existing hand-written provider rather than rewriting onto {@code @McpResource}.
 * <p>
 * {@code find} returns a reader for any URI; the actual routing and the unknown-URI refusal live in
 * {@link McpResources#read} (and the skill lookup), exactly as the hand-rolled dispatcher did. Argument
 * completion is not wired here (the {@code completions} capability is intentionally not advertised).
 */
public final class JeffreyResourceRepository implements McpSyncResourceRepository {

    private static final String UNKNOWN_SKILL_FILE = "No skill file is served at %s; skills/list names every file";

    private final McpResourceProvider resources;
    private final McpSkillProvider skills;

    public JeffreyResourceRepository(McpResourceProvider resources, McpSkillProvider skills) {
        this.resources = resources;
        this.skills = skills;
    }

    @Override
    public ResourcesPage list(McpRequestContext context, String cursor) {
        return ResourcesPage.of(resources.resources().stream().map(JeffreyResourceRepository::toResource).toList());
    }

    @Override
    public ResourceTemplatesPage listTemplates(McpRequestContext context, String cursor) {
        return ResourceTemplatesPage.of(resources.templates().stream().map(JeffreyResourceRepository::toTemplate).toList());
    }

    @Override
    public McpSyncResource find(McpRequestContext context, String uri) {
        return McpSyncResource.of((requestContext, request) -> read(request.uri()));
    }

    private McpSyncResponse<McpSchema.ReadResourceOutcome> read(String uri) {
        try {
            Contents contents = contentsOf(uri);
            McpSchema.ReadResourceResult result = McpSchema.ReadResourceResult.builder(List.of(
                    new McpSchema.TextResourceContents(contents.uri(), contents.mimeType(), contents.text(), null)))
                    .build();
            return McpSyncResponse.result(result);
        } catch (McpResourceNotFoundException | IllegalArgumentException e) {
            // An unknown or malformed URI is -32602, the code 2026-07-28 uses for a missing resource.
            throw McpException.invalidParams(e.getMessage());
        }
    }

    /** A skill file is answered before the resource provider is asked; a {@code skill://} miss is -32602 at once. */
    private Contents contentsOf(String uri) {
        Optional<McpSkillFile> file = skills.file(uri);
        if (file.isPresent()) {
            return new Contents(file.get().uri(), file.get().mimeType(), file.get().text());
        }
        if (uri != null && uri.startsWith(McpSkill.SCHEME)) {
            throw new McpResourceNotFoundException(UNKNOWN_SKILL_FILE.formatted(uri));
        }
        McpResourceProvider.Contents contents = resources.read(uri);
        return new Contents(contents.uri(), contents.mimeType(), contents.text());
    }

    private static McpSchema.Resource toResource(McpResource resource) {
        return McpSchema.Resource.builder(resource.uri(), resource.name())
                .description(resource.description())
                .mimeType(resource.mimeType())
                .build();
    }

    private static McpSchema.ResourceTemplate toTemplate(McpResource template) {
        return McpSchema.ResourceTemplate.builder(template.uri(), template.name())
                .description(template.description())
                .mimeType(template.mimeType())
                .build();
    }

    private record Contents(String uri, String mimeType, String text) {
    }
}
