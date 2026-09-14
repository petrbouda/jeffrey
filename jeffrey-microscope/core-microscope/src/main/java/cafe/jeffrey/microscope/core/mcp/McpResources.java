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
import cafe.jeffrey.profile.mcp.McpResourceLink;
import cafe.jeffrey.profile.mcp.McpResourceLinker;
import cafe.jeffrey.profile.mcp.McpResourceNotFoundException;
import cafe.jeffrey.profile.mcp.McpResourceProvider;
import cafe.jeffrey.profile.mcp.McpToolProvider;
import cafe.jeffrey.profile.mcp.McpToolSpec;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
public class McpResources implements McpResourceProvider, McpResourceLinker {

    private static final String SCHEME = "jeffrey://";
    private static final String PROFILES_URI = SCHEME + "profiles";
    private static final String PROFILES_QUERY_PREFIX = PROFILES_URI + "?";
    private static final String PROFILES_TEMPLATE = PROFILES_URI + "{?cursor,limit}";
    private static final String CURSOR_ARGUMENT = "cursor";
    private static final String LIMIT_ARGUMENT = "limit";
    private static final Set<String> CATALOGUE_ARGUMENTS = Set.of(CURSOR_ARGUMENT, LIMIT_ARGUMENT);
    private static final String PROFILE_PREFIX = SCHEME + "profile/";

    private static final String SUMMARY_SEGMENT = "summary";
    private static final String FLAMEGRAPH_SEGMENT = "flamegraph";
    private static final String EVIDENCE_SEGMENT = "evidence";

    private static final String SUMMARY_TEMPLATE = PROFILE_PREFIX + "{profileId}/" + SUMMARY_SEGMENT;
    private static final String FLAMEGRAPH_TEMPLATE = PROFILE_PREFIX + "{profileId}/" + FLAMEGRAPH_SEGMENT + "/{eventType}";
    private static final String EVIDENCE_TEMPLATE = PROFILE_PREFIX + "{profileId}/" + EVIDENCE_SEGMENT;

    private static final String PROFILES_LIST_TOOL = "profiles_list";
    private static final String PROFILE_SUMMARY_TOOL = "profiles_summary";
    private static final String PROFILE_EVIDENCE_TOOL = "profiles_evidence";
    private static final String FLAMEGRAPH_EXPORT_TOOL = "flamegraph_export";

    private static final String PROFILE_ID_ARGUMENT = "profileId";
    private static final String EVENT_TYPE_ARGUMENT = "eventType";

    /** Everything {@code flamegraph_export} accepts that changes the tree the template would return. */
    private static final Set<String> FLAMEGRAPH_NARROWING_ARGUMENTS = Set.of(
            "thresholdPct", "startMs", "endMs", "threadMode", "useWeight", "search", "excludeIdle", "excludeNonJava");

    private final McpToolProvider toolset;
    private final Set<String> availableTools;
    private final McpServerInfo serverInfo;
    private final Supplier<String> diagnostics;

    public McpResources(McpToolProvider toolset) {
        this(toolset, new ExternalMcpProperties(true, true, true, Set.of()));
    }

    public McpResources(McpToolProvider toolset, ExternalMcpProperties properties) {
        this(toolset, properties, null);
    }

    public McpResources(McpToolProvider toolset, ExternalMcpProperties properties, Supplier<String> diagnostics) {
        this.diagnostics = diagnostics;
        this.toolset = toolset;
        this.serverInfo = new McpServerInfo(properties, toolset);
        this.availableTools = toolset.specs().stream()
                .map(McpToolSpec::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Tool-backed resources require an advertised tool; server diagnostics are always available.
     * <p>
     * Reading a resource runs a tool, so a resource whose tool the family filter left out could be
     * listed and then fail on every read — the client is told the profile catalogue exists and then
     * that {@code profiles_list} does not. Narrowing the advertisement is the honest half of that: a
     * server configured down to one family offers only the tool-backed resources that family can serve.
     */
    @Override
    public List<McpResource> resources() {
        List<McpResource> resources = new ArrayList<>();
        if (availableTools.contains(PROFILES_LIST_TOOL)) {
            resources.add(new McpResource(
                    PROFILES_URI,
                    "Analysed profiles — first page",
                    "The first 100 matching profiles at most, further bounded by response size. "
                            + "Returned/total counts and a continuation URI describe this page; follow "
                            + "the continuation until hasMore=false to traverse the live catalogue.",
                    McpResource.TEXT_MARKDOWN));
        }
        resources.add(new McpResource(McpServerInfo.URI, "Jeffrey server",
                "Build version, selected preset, effective tool families and supported MCP capabilities.",
                McpResource.APPLICATION_JSON));
        if (diagnostics != null) {
            resources.add(new McpResource(McpDiagnostics.URI, "MCP runtime diagnostics",
                    "Profile readiness, bounded Hub reachability and aggregate tool latency/output sizes; no arguments or result contents.",
                    McpResource.APPLICATION_JSON));
        }
        return List.copyOf(resources);
    }

    @Override
    public List<McpResource> templates() {
        List<McpResource> templates = new ArrayList<>();
        if (availableTools.contains(PROFILES_LIST_TOOL)) {
            templates.add(new McpResource(PROFILES_TEMPLATE, "Analysed profiles — continuation",
                    "The next complete page of the unfiltered profile catalogue. Pass the cursor from the preceding page; limit is optional.",
                    McpResource.TEXT_MARKDOWN));
        }
        if (availableTools.contains(PROFILE_SUMMARY_TOOL)) {
            templates.add(new McpResource(
                    SUMMARY_TEMPLATE,
                    "Profile summary",
                    "What one profile is, what it can answer, every event type it recorded, and its "
                            + "auto-analysis findings where they have been computed.",
                    McpResource.APPLICATION_JSON));
        }
        if (availableTools.contains(FLAMEGRAPH_EXPORT_TOOL)) {
            templates.add(new McpResource(
                    FLAMEGRAPH_TEMPLATE,
                    "Flamegraph export",
                    "The call tree of one event type as Markdown, with the reading instructions for "
                            + "that event type. Use jdk.ExecutionSample for on-CPU time, "
                            + "jdk.ObjectAllocationSample for allocation.",
                    McpResource.TEXT_MARKDOWN));
        }
        if (availableTools.contains(PROFILE_EVIDENCE_TOOL)) {
            templates.add(new McpResource(EVIDENCE_TEMPLATE, "Profile evidence snapshot",
                    "Current profile/recording identity, filters, units, denominators, versioned findings and capability gaps. Save the response to preserve it.",
                    McpResource.APPLICATION_JSON));
        }
        return List.copyOf(templates);
    }

    @Override
    public Contents read(String uri) {
        if (McpDiagnostics.URI.equals(uri) && diagnostics != null) {
            return new Contents(uri, McpResource.APPLICATION_JSON, diagnostics.get());
        }
        if (McpServerInfo.URI.equals(uri)) {
            return new Contents(uri, McpResource.APPLICATION_JSON, serverInfo.json());
        }
        if (uri != null && uri.startsWith(PROFILES_QUERY_PREFIX)) {
            return new Contents(uri, McpResource.TEXT_MARKDOWN,
                    call(PROFILES_LIST_TOOL, catalogueArguments(uri)));
        }
        if (PROFILES_URI.equals(uri)) {
            return new Contents(uri, McpResource.TEXT_MARKDOWN, call(PROFILES_LIST_TOOL, Json.createObject()));
        }
        if (uri == null || !uri.startsWith(PROFILE_PREFIX)) {
            throw new McpResourceNotFoundException(unknown(uri));
        }

        String[] segments = uri.substring(PROFILE_PREFIX.length()).split("/");
        // A profile id followed by "summary", or by "flamegraph" and an event type. Anything else is
        // not a URI this server offers, and guessing which it meant would answer the wrong question.
        if (segments.length == 2 && EVIDENCE_SEGMENT.equals(segments[1])) {
            return new Contents(uri, McpResource.APPLICATION_JSON,
                    call(PROFILE_EVIDENCE_TOOL, Json.createObject().put(PROFILE_ID_ARGUMENT, decode(segments[0]))));
        }
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
        throw new McpResourceNotFoundException(unknown(uri));
    }

    private ObjectNode catalogueArguments(String uri) {
        ObjectNode arguments = Json.createObject();
        for (String parameter : uri.substring(PROFILES_QUERY_PREFIX.length()).split("&", -1)) {
            String[] pair = parameter.split("=", 2);
            if (pair.length != 2) {
                throw new IllegalArgumentException(
                        "Each profile catalogue parameter must be name=value: " + uri);
            }
            String key = decode(pair[0]);
            if (!CATALOGUE_ARGUMENTS.contains(key) || arguments.has(key)) {
                // The catalogue resource exists; it was the query string that was wrong, which is an
                // argument the caller can fix rather than a subject that is not there.
                throw new IllegalArgumentException("The profile catalogue accepts only "
                        + CURSOR_ARGUMENT + " and " + LIMIT_ARGUMENT + ", each at most once: " + uri);
            }
            String value = decode(pair[1]);
            if (key.equals(LIMIT_ARGUMENT)) {
                try {
                    arguments.put(key, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Resource limit must be an integer", e);
                }
            } else {
                arguments.put(key, value);
            }
        }
        return arguments;
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

    /**
     * The resource that holds the same answer as the tool that just ran.
     * <p>
     * Three tools have an exact template counterpart, and this is the one place that knows which, so a
     * link can never name a URI {@link #read} would refuse. A tool result scrolls out of the
     * conversation; a resource a client has attached stays, which is the whole reason the templates
     * exist. Anything else links to nothing rather than to something approximate.
     */
    @Override
    public List<McpResourceLink> linksFor(String toolName, JsonNode arguments) {
        if (arguments == null || !availableTools.contains(toolName)) {
            return List.of();
        }
        String profileId = arguments.path(PROFILE_ID_ARGUMENT).asString();
        if (profileId.isEmpty()) {
            return List.of();
        }
        String encodedProfile = encode(profileId);
        if (PROFILE_SUMMARY_TOOL.equals(toolName)) {
            return List.of(new McpResourceLink(
                    PROFILE_PREFIX + encodedProfile + "/" + SUMMARY_SEGMENT,
                    "Profile summary",
                    "The same summary as a resource, so it can be attached rather than re-read.",
                    McpResource.APPLICATION_JSON));
        }
        if (PROFILE_EVIDENCE_TOOL.equals(toolName)) {
            return List.of(new McpResourceLink(
                    PROFILE_PREFIX + encodedProfile + "/" + EVIDENCE_SEGMENT,
                    "Profile evidence snapshot",
                    "The same evidence snapshot as a resource; attach it to keep these figures.",
                    McpResource.APPLICATION_JSON));
        }
        if (FLAMEGRAPH_EXPORT_TOOL.equals(toolName)) {
            String eventType = arguments.path(EVENT_TYPE_ARGUMENT).asString();
            // The template takes an event type and nothing else, so it can only stand for an unnarrowed
            // export. Linking a filtered one would offer a different call tree under the same name,
            // which is worse than offering no link at all.
            if (eventType.isEmpty() || narrowed(arguments)) {
                return List.of();
            }
            return List.of(new McpResourceLink(
                    PROFILE_PREFIX + encodedProfile + "/" + FLAMEGRAPH_SEGMENT + "/" + encode(eventType),
                    "Flamegraph export: " + eventType,
                    "The same call tree as a resource, at this event type's default settings.",
                    McpResource.TEXT_MARKDOWN));
        }
        return List.of();
    }

    /** Whether a flamegraph call asked for anything the bare template cannot express. */
    private static boolean narrowed(JsonNode arguments) {
        return FLAMEGRAPH_NARROWING_ARGUMENTS.stream()
                .anyMatch(argument -> arguments.has(argument) && !arguments.path(argument).isNull());
    }

    /**
     * A path segment as a URI carries it. {@link #decode} is the other half; an event type carries dots
     * and a profile id could carry anything, so a link that did not encode could not be read back.
     */
    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * What a client is told about a URI this server does not serve, naming the ones it does. Thrown as
     * {@link McpResourceNotFoundException} so the envelope answers {@code -32002}: the specification
     * reserves that code for a {@code resources/read} whose subject is not there, and a client can then
     * tell a URI it should stop asking for from an argument it merely spelled wrong. The
     * diagnostics resource is named only when this instance actually has one: an endpoint built
     * without diagnostics would otherwise advertise, in its refusal, a URI that same refusal is
     * about to be sent for.
     */
    private String unknown(String uri) {
        String diagnosticsUri = diagnostics == null ? "" : McpDiagnostics.URI + ", ";
        return "No resource at '" + uri + "'. This server serves " + PROFILES_URI + ", "
                + PROFILES_TEMPLATE + ", " + McpServerInfo.URI + ", " + diagnosticsUri
                + EVIDENCE_TEMPLATE + ", " + SUMMARY_TEMPLATE + " and " + FLAMEGRAPH_TEMPLATE + ".";
    }
}
