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

package cafe.jeffrey.profile.mcp;

import org.springframework.ai.tool.annotation.Tool;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

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

    private final ToolMethodIndex index;
    private final ScopedTargetResolver<T> targetResolver;

    /**
     * @param targetType     the {@code @Tool} class; indexed once, not per call
     * @param prefix         the tool-name prefix, e.g. {@code jfr}
     * @param targetResolver builds the tool object for one profile id
     */
    public ProfileScopedToolset(Class<T> targetType, String prefix, Function<String, T> targetResolver) {
        this(targetType, prefix, targetResolver, McpToolAnnotations.READ_ONLY);
    }

    /**
     * @param defaultAnnotations what this family does to the world. Every profile-scoped family reads,
     *                           so the default stands unless a single method declares otherwise with
     *                           {@link McpToolHints} — the compute tools, which build an index or a report.
     */
    public ProfileScopedToolset(
            Class<T> targetType,
            String prefix,
            Function<String, T> targetResolver,
            McpToolAnnotations defaultAnnotations) {
        this(targetType, prefix, unscoped(targetResolver), defaultAnnotations);
    }

    private static <T> ScopedTargetResolver<T> unscoped(Function<String, T> targetResolver) {
        return profileId -> new ScopedTarget<>() {
            @Override
            public T target() {
                return targetResolver.apply(profileId);
            }

            @Override
            public void close() {
            }
        };
    }

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
    public McpToolResult callResult(String toolName, JsonNode arguments) {
        Method method = index.method(toolName);
        String profileId = readProfileId(arguments);
        // Bound before the profile is resolved, so that an argument the schema does not accept is
        // refused as the protocol error it is whether or not the profile exists. Resolving first made
        // the answer depend on which mistake was noticed: a bad enum against a missing profile came
        // back as "profile not found", which sends the caller after the wrong one of its two errors.
        Object[] args = index.bindArguments(method, arguments);
        try (ScopedTarget<T> scoped = targetResolver.resolve(profileId)) {
            return ToolInvocation.invoke(toolName, method, scoped.target(), args);
        }
    }

    private static String readProfileId(JsonNode arguments) {
        ToolMethodIndex.validateArgumentsObject(arguments);
        JsonNode node = arguments == null ? null : arguments.get(PROFILE_ID_ARGUMENT);
        if (node == null || node.isNull()) {
            throw new ToolDispatchException(PROFILE_ID_ARGUMENT + " is required");
        }
        if (!node.isString()) {
            throw new ToolDispatchException(PROFILE_ID_ARGUMENT + " must be a string");
        }
        String profileId = node.asString();
        if (profileId == null || profileId.isBlank()) {
            throw new ToolDispatchException(PROFILE_ID_ARGUMENT + " is required");
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
