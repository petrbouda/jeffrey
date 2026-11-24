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
import cafe.jeffrey.microscope.mcp.server.McpClientCapabilities;
import cafe.jeffrey.microscope.mcp.server.McpProtocolException;
import cafe.jeffrey.microscope.mcp.server.McpSkill;
import cafe.jeffrey.microscope.mcp.server.McpSkillProvider;
import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.McpAsyncResponse;
import io.modelcontextprotocol.modern.server.McpFeature;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serves the {@code io.modelcontextprotocol/skills} extension on the modern server as a custom
 * {@link McpFeature}: {@code skills/list} and {@code skills/get}, backed by the one
 * {@link McpSkillProvider} ({@code McpSkillCatalogue}). The extension is declared at
 * {@code server/discover} exactly when a skill is served, and the methods answer every client whether or
 * not it declared the extension — the same contract the hand-rolled dispatcher had.
 * <p>
 * The skill files themselves are read over {@code resources/read} under {@code skill://}, which belongs
 * to the resources feature, not here.
 */
public final class SkillsFeature implements McpFeature {

    private static final String METHOD_SKILLS_LIST = "skills/list";
    private static final String METHOD_SKILLS_GET = "skills/get";
    private static final String FIELD_URI = "uri";
    private static final String RESULT_TYPE_COMPLETE = "complete";
    private static final String MISSING_SKILL_URI = "skills/get must name the uri of the skill's SKILL.md";

    private final McpSkillProvider skills;

    public SkillsFeature(McpSkillProvider skills) {
        this.skills = skills;
    }

    @Override
    public Set<String> methods() {
        return Set.of(METHOD_SKILLS_LIST, METHOD_SKILLS_GET);
    }

    @Override
    public Mono<? extends McpAsyncResponse<? extends McpSchema.Result>> handle(
            McpRequestContext context, Object params) {
        McpSchema.Result result = switch (context.method()) {
            case METHOD_SKILLS_LIST -> new SkillsListResult(skills.skills().stream().map(McpSkill::toJson).toList());
            case METHOD_SKILLS_GET -> new SkillGetResult(lookup(params));
            default -> throw new McpException(McpSchema.ErrorCodes.METHOD_NOT_FOUND, context.method());
        };
        return Mono.just(McpAsyncResponse.result(result));
    }

    @Override
    public void capabilities(McpSchema.ServerCapabilities.Builder builder) {
        if (skills.servesAny()) {
            builder.extension(McpClientCapabilities.SKILLS_EXTENSION, Map.of());
        }
    }

    private ObjectNode lookup(Object params) {
        JsonNode node = Json.toTree(params);
        String uri = node == null ? "" : node.path(FIELD_URI).asString("");
        if (uri.isBlank()) {
            throw McpException.invalidParams(MISSING_SKILL_URI);
        }
        try {
            return skills.skill(uri).toJson();
        } catch (McpProtocolException e) {
            // An unknown skill URI is -32602, the code the specification shares with an unknown resource.
            throw McpException.invalidParams(e.getMessage());
        }
    }

    /** {@code {skills: [...]}}; resultType/meta are interface methods, not components, so they stay off the wire. */
    public record SkillsListResult(List<ObjectNode> skills) implements McpSchema.Result {

        @Override
        public String resultType() {
            return RESULT_TYPE_COMPLETE;
        }

        @Override
        public Map<String, Object> meta() {
            return null;
        }
    }

    /** {@code {skill: {...}}}. */
    public record SkillGetResult(ObjectNode skill) implements McpSchema.Result {

        @Override
        public String resultType() {
            return RESULT_TYPE_COMPLETE;
        }

        @Override
        public Map<String, Object> meta() {
            return null;
        }
    }
}
