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

import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpToolAnnotations;
import cafe.jeffrey.microscope.mcp.protocol.McpToolArguments;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.ToolDispatchException;
import org.springframework.ai.tool.annotation.Tool;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.util.List;

/**
 * The same {@link Tool}-annotated class as {@link ReflectiveToolset}, but with the target chosen per
 * call from a {@code profileId} argument the toolset adds to every schema.
 * <p>
 * This is what lets one MCP server serve every profile. The alternative — a server URL per profile —
 * makes the client re-register whenever the reader looks at a different recording, which is the normal
 * case when comparing two runs. The tool classes are unchanged: they are constructed against one
 * profile as before, only later.
 *
 * @param <T> the {@code @Tool} class this toolset exposes
 */
public final class ProfileScopedToolset<T> implements McpToolProvider {

    public static final String PROFILE_ID_ARGUMENT = "profileId";

    private static final String PROFILE_ID_DESCRIPTION =
            "Id of the profile to work on, as listed by profiles_list.";
    private static final String EXPECTED_STRING = "Expected a string";

    private final ToolMethodIndex index;
    private final ScopedTargetResolver<T> targetResolver;

    /**
     * @param targetType         the {@code @Tool} class; indexed once, not per call
     * @param prefix             the tool-name prefix, e.g. {@code jfr}
     * @param targetResolver     builds the tool object for one profile id, for one invocation
     * @param defaultAnnotations what this family does to the world. Every profile-scoped family reads,
     *                           so the default stands unless a single method declares otherwise with
     *                           {@link McpToolHints} — the compute tools, which build an index or a report
     */
    private ProfileScopedToolset(
            Class<T> targetType,
            String prefix,
            ScopedTargetResolver<T> targetResolver,
            McpToolAnnotations defaultAnnotations) {
        this.index = new ToolMethodIndex(
                targetType,
                prefix,
                List.of(new ToolMethodIndex.SyntheticParam(PROFILE_ID_ARGUMENT, PROFILE_ID_DESCRIPTION)),
                defaultAnnotations);
        this.targetResolver = targetResolver;
    }

    /**
     * A profile-scoped family whose resolved target owns resources for exactly one invocation.
     */
    public static <T> ProfileScopedToolset<T> leased(
            Class<T> targetType,
            String prefix,
            ScopedTargetResolver<T> targetResolver) {
        return leased(targetType, prefix, targetResolver, McpToolAnnotations.READ_ONLY);
    }

    /**
     * A leased family with annotations other than the read-only default.
     */
    public static <T> ProfileScopedToolset<T> leased(
            Class<T> targetType,
            String prefix,
            ScopedTargetResolver<T> targetResolver,
            McpToolAnnotations defaultAnnotations) {
        return new ProfileScopedToolset<>(targetType, prefix, targetResolver, defaultAnnotations);
    }

    @Override
    public List<McpToolSpec> specs() {
        return index.specs();
    }

    @Override
    public McpToolOutcome call(String toolName, JsonNode arguments, McpCallContext context) {
        Method method = index.method(toolName);
        String profileId = readProfileId(arguments);
        // Bound before the profile is resolved, so that an argument the schema does not accept is
        // refused as the argument error it is whether or not the profile exists. Resolving first made
        // the answer depend on which mistake was noticed: a bad enum against a missing profile came
        // back as "profile not found", which sends the caller after the wrong one of its two errors.
        Object[] args = index.bindArguments(method, arguments, context);
        try (ScopedTarget<T> scoped = targetResolver.resolve(profileId)) {
            return ToolInvocation.invoke(toolName, method, scoped.target(), args, context);
        }
    }

    private static String readProfileId(JsonNode arguments) {
        McpToolArguments.requireObject(arguments);
        JsonNode node = arguments == null ? null : arguments.get(PROFILE_ID_ARGUMENT);
        if (node == null || node.isNull()) {
            throw ToolDispatchException.missingArgument(PROFILE_ID_ARGUMENT, PROFILE_ID_DESCRIPTION);
        }
        if (!node.isString()) {
            throw ToolDispatchException.invalidArgument(PROFILE_ID_ARGUMENT, EXPECTED_STRING);
        }
        String profileId = node.asString();
        if (profileId == null || profileId.isBlank()) {
            throw ToolDispatchException.missingArgument(PROFILE_ID_ARGUMENT, PROFILE_ID_DESCRIPTION);
        }
        return profileId;
    }

    @FunctionalInterface
    public interface ScopedTargetResolver<T> {

        ScopedTarget<T> resolve(String profileId);
    }

    public interface ScopedTarget<T> extends AutoCloseable {

        T target();

        @Override
        void close();
    }
}
