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

import cafe.jeffrey.microscope.mcp.protocol.McpCompletion;
import cafe.jeffrey.microscope.mcp.protocol.McpCompletionProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpCompletionRef;
import cafe.jeffrey.microscope.mcp.protocol.McpPrompt;
import cafe.jeffrey.microscope.mcp.protocol.McpPromptProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.shared.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Completes the two arguments nobody can type from memory: {@code profileId} and
 * {@code baselineProfileId}.
 * <p>
 * A profile id is a UUIDv7. It appears in a resource template, in the six prompts that declare it, and
 * as the first argument of almost every tool; {@code baselineProfileId} is the same kind of value,
 * carried by the one prompt and the handful of tools that compare two profiles. Neither can be produced
 * except by reading it out of {@code profiles_list} first. That is exactly what {@code
 * completion/complete} is for, and it is why this class completes those two arguments and no other: an
 * event type is {@code jdk.ExecutionSample}, a name a model already knows and a reader can type, so
 * completing it would cost a tool call per keystroke to suggest something nobody was stuck on.
 * <p>
 * The candidates come from running {@code profiles_list} and reading its <em>structured</em> answer,
 * for the same reason {@link McpResources} reads a resource by running the tool that would have
 * answered it: one source, so a completion cannot offer an id the catalogue does not have.
 */
public class McpCompletions implements McpCompletionProvider {

    private static final Logger LOG = LoggerFactory.getLogger(McpCompletions.class);

    private static final String PROFILES_LIST_TOOL = "profiles_list";
    private static final String PROFILE_ID_ARGUMENT = "profileId";
    private static final String BASELINE_PROFILE_ID_ARGUMENT = "baselineProfileId";

    private static final String PROFILES_FIELD = "profiles";
    private static final String LIMIT_ARGUMENT = "limit";

    /**
     * How much of the catalogue one completion reads. Above the hundred a response may carry, so a
     * prefix that matches more than fits still reports an honest {@code total}, and well under the
     * thousand {@code profiles_list} allows, because this runs while somebody is typing.
     */
    private static final int SCAN_LIMIT = 500;

    /** The family {@code profiles_list} belongs to, as the family filter names it. */
    private static final String PROFILES_FAMILY = "profiles";

    private final Supplier<McpToolProvider> toolset;
    private final McpPromptProvider prompts;
    private final ExternalMcpProperties properties;

    /**
     * @param prompts the prompts this endpoint serves, read for the arguments each one declares
     */
    public McpCompletions(
            Supplier<McpToolProvider> toolset, McpPromptProvider prompts, ExternalMcpProperties properties) {
        this.toolset = toolset;
        this.prompts = prompts;
        this.properties = properties;
    }

    /**
     * Whether the capability is worth declaring, answered from configuration alone.
     * <p>
     * Deliberately not by asking the toolset whether it advertises {@code profiles_list}: this is read
     * while answering {@code server/discover}, and {@code server/discover} must answer even when
     * assembling the toolset would fail. That is why the toolset arrives as a supplier and is resolved
     * only when a completion is actually requested.
     */
    public boolean isAvailable() {
        return properties.advertises(PROFILES_FAMILY);
    }

    @Override
    public McpCompletion complete(McpCompletionRef ref, String argumentName, String value) {
        if (!isAvailable() || !isProfileIdArgument(argumentName) || !completes(ref, argumentName)) {
            return McpCompletion.EMPTY;
        }
        String prefix = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        List<String> matching = new ArrayList<>();
        for (JsonNode profile : profiles()) {
            String profileId = profile.path(PROFILE_ID_ARGUMENT).asString();
            if (!profileId.isEmpty() && profileId.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matching.add(profileId);
            }
        }
        return McpCompletion.of(matching);
    }

    /** {@code baselineProfileId} names a different argument, but it is filled from the same catalogue. */
    private static boolean isProfileIdArgument(String argumentName) {
        return PROFILE_ID_ARGUMENT.equals(argumentName) || BASELINE_PROFILE_ID_ARGUMENT.equals(argumentName);
    }

    /**
     * Whether this reference declares the argument being completed. A prompt is asked which arguments
     * it takes: the single-profile skills declare {@code profileId}, {@code compare-jfr} declares both,
     * and the four that produce a profile, reach a hub or are pure guidance declare none, so a client
     * is never offered ids for an argument the prompt has no use for. A resource template declares
     * what its path carries — {@code profileId} on the per-profile templates, nothing on the catalogue.
     */
    private boolean completes(McpCompletionRef ref, String argumentName) {
        if (ref.isPrompt()) {
            return declares(ref.name(), argumentName);
        }
        return ref.isResource() && ref.name().contains("{" + argumentName + "}");
    }

    /** Whether the named prompt declares the argument; a prompt this server does not serve declares nothing. */
    private boolean declares(String promptName, String argumentName) {
        try {
            return prompts.prompt(promptName).arguments().stream()
                    .map(McpPrompt.Argument::name)
                    .anyMatch(argumentName::equals);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * The catalogue rows, or none when the tool could not answer. A completion is a convenience offered
     * while somebody types: failing the request would turn a picker that cannot suggest anything into
     * an error in the client's face, so the failure is logged and the list comes back empty.
     */
    private List<JsonNode> profiles() {
        try {
            ObjectNode arguments = Json.createObject().put(LIMIT_ARGUMENT, SCAN_LIMIT);
            McpToolResult result = toolset.get().callResult(PROFILES_LIST_TOOL, arguments);
            ObjectNode structured = result.structuredContent();
            if (structured == null) {
                return List.of();
            }
            List<JsonNode> rows = new ArrayList<>();
            structured.path(PROFILES_FIELD).forEach(rows::add);
            return rows;
        } catch (RuntimeException e) {
            LOG.warn("Could not complete {}: tool={} message={}",
                    PROFILE_ID_ARGUMENT, PROFILES_LIST_TOOL, e.getMessage());
            return List.of();
        }
    }
}
