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
package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.profile.mcp.McpResource;
import cafe.jeffrey.profile.mcp.McpResourceProvider;
import cafe.jeffrey.profile.mcp.McpToolProvider;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.node.ObjectNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The parts of a profile a client can fetch by URI instead of by tool call.
 * <p>
 * MCP separates what a model decides to do from what a person attaches. A flamegraph is both: a model
 * asks for one mid-reasoning, and a reader wants to pin one into the conversation and refer back to
 * it. As a tool it costs a call and a turn; as a resource the client fetches it directly and can show
 * it in its own UI, and it stays attached rather than scrolling away.
 * <p>
 * The catalogue is a concrete resource because it exists without arguments. Everything per-profile is
 * a template: listing a flamegraph resource for every event type of every profile would be a list
 * nobody reads and almost none of which anybody wants.
 * <p>
 * Reading one runs the same tool that would have answered the call, so a resource and a tool never
 * disagree about what a profile holds.
 */
public class McpResources implements McpResourceProvider {

    private static final String SCHEME = "jeffrey://";
    private static final String PROFILES_URI = SCHEME + "profiles";
    private static final String PROFILE_PREFIX = SCHEME + "profile/";

    private static final String SUMMARY_TEMPLATE = PROFILE_PREFIX + "{profileId}/summary";
    private static final String FLAMEGRAPH_TEMPLATE = PROFILE_PREFIX + "{profileId}/flamegraph/{eventType}";

    private static final String SUMMARY_SEGMENT = "summary";
    private static final String FLAMEGRAPH_SEGMENT = "flamegraph";

    private static final String PROFILES_LIST_TOOL = "profiles_list";
    private static final String PROFILE_SUMMARY_TOOL = "profiles_summary";
    private static final String FLAMEGRAPH_EXPORT_TOOL = "flamegraph_export";

    private static final String PROFILE_ID_ARGUMENT = "profileId";
    private static final String EVENT_TYPE_ARGUMENT = "eventType";

    private final McpToolProvider toolset;

    public McpResources(McpToolProvider toolset) {
        this.toolset = toolset;
    }

    @Override
    public List<McpResource> resources() {
        return List.of(new McpResource(
                PROFILES_URI,
                "Analysed profiles",
                "Every profile in this Jeffrey installation, with what each one is and when it was "
                        + "recorded. The starting point: every other resource takes a profile id from here.",
                McpResource.TEXT_MARKDOWN));
    }

    @Override
    public List<McpResource> templates() {
        return List.of(
                new McpResource(
                        SUMMARY_TEMPLATE,
                        "Profile summary",
                        "What one profile is, what it can answer, every event type it recorded, and its "
                                + "auto-analysis findings where they have been computed.",
                        McpResource.APPLICATION_JSON),
                new McpResource(
                        FLAMEGRAPH_TEMPLATE,
                        "Flamegraph export",
                        "The call tree of one event type as Markdown, with the reading instructions for "
                                + "that event type. Use jdk.ExecutionSample for on-CPU time, "
                                + "jdk.ObjectAllocationSample for allocation.",
                        McpResource.TEXT_MARKDOWN));
    }

    @Override
    public Contents read(String uri) {
        if (PROFILES_URI.equals(uri)) {
            return new Contents(uri, McpResource.TEXT_MARKDOWN, call(PROFILES_LIST_TOOL, Json.createObject()));
        }
        if (uri == null || !uri.startsWith(PROFILE_PREFIX)) {
            throw new IllegalArgumentException(unknown(uri));
        }

        String[] segments = uri.substring(PROFILE_PREFIX.length()).split("/");
        // A profile id followed by "summary", or by "flamegraph" and an event type. Anything else is
        // not a URI this server offers, and guessing which it meant would answer the wrong question.
        if (segments.length == 2 && SUMMARY_SEGMENT.equals(segments[1])) {
            ObjectNode arguments = Json.createObject().put(PROFILE_ID_ARGUMENT, decode(segments[0]));
            return new Contents(uri, McpResource.APPLICATION_JSON, call(PROFILE_SUMMARY_TOOL, arguments));
        }
        if (segments.length == 3 && FLAMEGRAPH_SEGMENT.equals(segments[1])) {
            ObjectNode arguments = Json.createObject()
                    .put(PROFILE_ID_ARGUMENT, decode(segments[0]))
                    .put(EVENT_TYPE_ARGUMENT, decode(segments[2]));
            return new Contents(uri, McpResource.TEXT_MARKDOWN, call(FLAMEGRAPH_EXPORT_TOOL, arguments));
        }
        throw new IllegalArgumentException(unknown(uri));
    }

    private String call(String toolName, ObjectNode arguments) {
        return toolset.call(toolName, arguments);
    }

    /**
     * An event type carries dots and a profile id could carry anything, so a client is entitled to
     * percent-encode either.
     */
    private static String decode(String segment) {
        return URLDecoder.decode(segment, StandardCharsets.UTF_8);
    }

    private static String unknown(String uri) {
        return "No resource at '" + uri + "'. This server serves " + PROFILES_URI + ", "
                + SUMMARY_TEMPLATE + " and " + FLAMEGRAPH_TEMPLATE + ".";
    }
}
