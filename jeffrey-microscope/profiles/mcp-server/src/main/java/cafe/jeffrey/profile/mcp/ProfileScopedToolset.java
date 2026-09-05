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
import java.util.Set;
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

    private final String prefix;
    private final ToolMethodIndex index;
    private final Function<String, T> targetResolver;

    /**
     * @param targetType     the {@code @Tool} class; indexed once, not per call
     * @param prefix         the tool-name prefix, e.g. {@code jfr}
     * @param targetResolver builds the tool object for one profile id
     */
    public ProfileScopedToolset(Class<T> targetType, String prefix, Function<String, T> targetResolver) {
        this(targetType, prefix, targetResolver, Set.of());
    }

    /**
     * @param excludedMethods {@code @Tool} methods of {@code targetType} to leave out — a family that
     *                        is offered read-only omits its write tools rather than advertising ones
     *                        that always refuse
     */
    public ProfileScopedToolset(
            Class<T> targetType,
            String prefix,
            Function<String, T> targetResolver,
            Set<String> excludedMethods) {
        this(targetType, prefix, targetResolver, excludedMethods, McpToolAnnotations.READ_ONLY);
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
            Set<String> excludedMethods,
            McpToolAnnotations defaultAnnotations) {
        this.prefix = prefix;
        this.index = new ToolMethodIndex(
                targetType,
                prefix,
                List.of(new ToolMethodIndex.SyntheticParam(PROFILE_ID_ARGUMENT, PROFILE_ID_DESCRIPTION)),
                excludedMethods,
                defaultAnnotations);
        this.targetResolver = targetResolver;
    }

    @Override
    public List<McpToolSpec> specs() {
        return index.specs();
    }

    @Override
    public String call(String toolName, JsonNode arguments) {
        Method method = index.method(toolName);
        String profileId = readProfileId(arguments);
        T target = targetResolver.apply(profileId);
        return ToolInvocation.invoke(toolName, method, target, index.bindArguments(method, arguments));
    }

    /**
     * The prefix these tools carry, so a caller can name the family it is registering.
     */
    public String prefix() {
        return prefix;
    }

    private static String readProfileId(JsonNode arguments) {
        JsonNode node = arguments == null ? null : arguments.get(PROFILE_ID_ARGUMENT);
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException(PROFILE_ID_ARGUMENT + " is required");
        }
        String profileId = node.asString();
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException(PROFILE_ID_ARGUMENT + " is required");
        }
        return profileId;
    }
}
