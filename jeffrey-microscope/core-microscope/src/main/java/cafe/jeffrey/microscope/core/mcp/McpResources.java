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
import cafe.jeffrey.microscope.mcp.protocol.McpResourceLink;
import cafe.jeffrey.microscope.mcp.protocol.McpResourceLinker;
import cafe.jeffrey.microscope.mcp.protocol.McpResourceNotFoundException;
import cafe.jeffrey.microscope.mcp.protocol.McpResourceProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.profile.mcp.McpToolNames;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
 * disagree about what a profile holds. The two exceptions are documents no single tool returns: the
 * profile database's schema, read through the catalogue reads the {@code jfr_} SQL tools render, and
 * the findings the profile already holds, merged from what {@code jvm_autoAnalysis} caches and what
 * {@code jvm_container} judges. Each is offered only where the family that reads the same data is
 * served, and the findings are read from the cache alone: a resource read never starts the analysis.
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
    private static final String SCHEMA_SEGMENT = "schema";
    private static final String FINDINGS_SEGMENT = "findings";

    private static final String SUMMARY_TEMPLATE = PROFILE_PREFIX + "{profileId}/" + SUMMARY_SEGMENT;
    private static final String FLAMEGRAPH_TEMPLATE = PROFILE_PREFIX + "{profileId}/" + FLAMEGRAPH_SEGMENT + "/{eventType}";
    private static final String EVIDENCE_TEMPLATE = PROFILE_PREFIX + "{profileId}/" + EVIDENCE_SEGMENT;
    private static final String SCHEMA_TEMPLATE = PROFILE_PREFIX + "{profileId}/" + SCHEMA_SEGMENT;
    private static final String FINDINGS_TEMPLATE = PROFILE_PREFIX + "{profileId}/" + FINDINGS_SEGMENT;

    private static final String PROFILES_LIST_TOOL = "profiles_list";
    private static final String PROFILE_SUMMARY_TOOL = "profiles_summary";
    private static final String PROFILE_EVIDENCE_TOOL = "profiles_evidence";
    private static final String FLAMEGRAPH_EXPORT_TOOL = "flamegraph_export";
    private static final String LIST_TABLES_TOOL = "jfr_listTables";
    private static final String DESCRIBE_TABLE_TOOL = "jfr_describeTable";

    /** The tools whose answer is one part of the schema document, so a call to either links to all of it. */
    private static final Set<String> SCHEMA_TOOLS = Set.of(LIST_TABLES_TOOL, DESCRIBE_TABLE_TOOL);

    /** The tools whose answer carries some of the profile's findings, so a call to either links to all of them. */
    private static final Set<String> FINDINGS_TOOLS = Set.of(PROFILE_SUMMARY_TOOL, PROFILE_EVIDENCE_TOOL);

    /** How the refusal joins the URIs it names: commas between, "and" before the last. */
    private static final String LIST_SEPARATOR = ", ";
    private static final String LAST_SEPARATOR = " and ";

    /** The profile id in a document URI, first of its two segments. */
    private static final int PROFILE_SEGMENT = 0;
    private static final int DOCUMENT_SEGMENT = 1;
    private static final int DOCUMENT_SEGMENTS = 2;

    private static final String PROFILE_ID_ARGUMENT = "profileId";
    private static final String EVENT_TYPE_ARGUMENT = "eventType";

    /** Everything {@code flamegraph_export} accepts that changes the tree the template would return. */
    private static final Set<String> FLAMEGRAPH_NARROWING_ARGUMENTS = Set.of(
            "thresholdPct", "startEpochMs", "endEpochMs", "threadMode", "useWeight", "search", "excludeIdle", "excludeNonJava");

    /**
     * {@code detail} narrows too, except at the level the template returns: a summary is not a call
     * tree at all, and a full export is a deeper one.
     */
    private static final String DETAIL_ARGUMENT = "detail";
    private static final String TEMPLATE_DETAIL = "standard";

    private final McpToolProvider toolset;
    private final Supplier<McpProfileDocuments> documents;
    private final Set<String> availableTools;
    private final Set<String> servedFamilies;
    private final McpServerInfo serverInfo;
    private final Supplier<String> diagnostics;

    /**
     * @param documents    the schema and findings readers, resolved only when one of the two is read
     * @param diagnostics  the runtime diagnostics document, read afresh on every read; null for none
     * @param servesSkills whether the endpoint serves the skills extension, as the server document says
     */
    public McpResources(McpToolProvider toolset, Supplier<McpProfileDocuments> documents,
                        ExternalMcpProperties properties, Supplier<String> diagnostics, boolean servesSkills) {
        this.diagnostics = diagnostics;
        this.toolset = toolset;
        this.documents = documents;
        this.serverInfo = new McpServerInfo(properties, toolset, servesSkills);
        this.availableTools = toolset.specs().stream()
                .map(McpToolSpec::name)
                .collect(Collectors.toUnmodifiableSet());
        this.servedFamilies = availableTools.stream()
                .map(McpToolNames::familyOf)
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
        if (servesSchema()) {
            templates.add(new McpResource(SCHEMA_TEMPLATE, "Profile database schema",
                    "Every table and view of the profile's SQL database, the events view included, with its "
                            + "columns and types, and every event type with its event count: what a query "
                            + "against this profile can read.",
                    McpResource.APPLICATION_JSON));
        }
        if (servesFindings()) {
            templates.add(new McpResource(FINDINGS_TEMPLATE, "Profile findings",
                    "The cached auto-analysis findings merged with the container throttling verdict, with "
                            + "severity counts and capability gaps. Reading it never runs the analysis: "
                            + "status NOT_COMPUTED names the call that does.",
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
        // A profile id followed by "summary", "evidence", "schema" or "findings", or by "flamegraph"
        // and an event type. Anything else is not a URI this server offers, and guessing which it
        // meant would answer the wrong question.
        if (segments.length == DOCUMENT_SEGMENTS && SCHEMA_SEGMENT.equals(segments[DOCUMENT_SEGMENT])
                && servesSchema()) {
            return document(uri, profileId(uri, segments), documents().schemaOf());
        }
        if (segments.length == DOCUMENT_SEGMENTS && FINDINGS_SEGMENT.equals(segments[DOCUMENT_SEGMENT])
                && servesFindings()) {
            return document(uri, profileId(uri, segments), documents().findingsOf());
        }
        if (segments.length == DOCUMENT_SEGMENTS && EVIDENCE_SEGMENT.equals(segments[DOCUMENT_SEGMENT])) {
            ObjectNode arguments = Json.createObject().put(PROFILE_ID_ARGUMENT, decode(segments[PROFILE_SEGMENT]));
            return new Contents(uri, McpResource.APPLICATION_JSON, call(PROFILE_EVIDENCE_TOOL, arguments));
        }
        if (segments.length == DOCUMENT_SEGMENTS && SUMMARY_SEGMENT.equals(segments[DOCUMENT_SEGMENT])) {
            ObjectNode arguments = Json.createObject().put(PROFILE_ID_ARGUMENT, decode(segments[PROFILE_SEGMENT]));
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
     * The schema is what the {@code jfr_} SQL tools read, so it is offered exactly where they are.
     */
    private boolean servesSchema() {
        return servedFamilies.contains(AdvertisedFamilies.JFR);
    }

    /**
     * The findings are what the {@code jvm_} family judges, and the call that computes a missing
     * analysis is {@code jvm_autoAnalysis}, so they are offered exactly where that family is.
     */
    private boolean servesFindings() {
        return servedFamilies.contains(AdvertisedFamilies.JVM);
    }

    private McpProfileDocuments documents() {
        return Objects.requireNonNull(documents.get(), "profile documents");
    }

    /**
     * One document, written as its record's JSON: the shape the record's generated schema describes.
     * Dynamic, like every profile read, since the profile can change or disappear.
     */
    private static Contents document(String uri, String profileId, Function<String, ? extends Record> read) {
        return new Contents(uri, McpResource.APPLICATION_JSON, Json.toString(read.apply(profileId)));
    }

    /**
     * The profile id a document URI names. A blank one names no profile, which is a URI this server
     * does not serve rather than a profile it cannot find.
     */
    private String profileId(String uri, String[] segments) {
        String profileId = decode(segments[PROFILE_SEGMENT]);
        if (profileId.isBlank()) {
            throw new McpResourceNotFoundException(unknown(uri));
        }
        return profileId;
    }

    /**
     * An event type carries dots and a profile id could carry anything, so a client is entitled to
     * percent-encode either.
     */
    private static String decode(String segment) {
        return URLDecoder.decode(segment, StandardCharsets.UTF_8);
    }

    /**
     * The resource that holds the same answer as the tool that just ran, and the one document that
     * holds the whole of what it answered a part of.
     * <p>
     * Three tools have an exact template counterpart, and this is the one place that knows which, so a
     * link can never name a URI {@link #read} would refuse. A tool result scrolls out of the
     * conversation; a resource a client has attached stays, which is the whole reason the templates
     * exist. Four more answer one part of a document: the summary and the evidence carry some of the
     * profile's findings, and the two SQL catalogue tools one table or the list of them, so each also
     * links to the whole document where it is served. Anything else links to nothing rather than to
     * something approximate.
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
        List<McpResourceLink> links = new ArrayList<>(sameAnswer(toolName, encodedProfile, arguments));
        if (FINDINGS_TOOLS.contains(toolName) && servesFindings()) {
            links.add(new McpResourceLink(
                    PROFILE_PREFIX + encodedProfile + "/" + FINDINGS_SEGMENT,
                    "Profile findings",
                    "Every finding this profile holds, with severity counts and capability gaps, as one "
                            + "resource; reading it never runs the analysis.",
                    McpResource.APPLICATION_JSON));
        }
        if (SCHEMA_TOOLS.contains(toolName) && servesSchema()) {
            links.add(new McpResourceLink(
                    schemaUri(profileId),
                    "Profile database schema",
                    "Every table and view with its columns, and every event type with its count, as one resource.",
                    McpResource.APPLICATION_JSON));
        }
        return List.copyOf(links);
    }

    /**
     * The URI of a profile's schema resource, with the id encoded as every link of this provider encodes
     * it. The one place it is built, so a tool naming the resource in its answer names exactly the URI
     * the resource links attach.
     */
    public static String schemaUri(String profileId) {
        return PROFILE_PREFIX + encode(profileId) + "/" + SCHEMA_SEGMENT;
    }

    /** The template that holds exactly what the tool answered, when there is one. */
    private static List<McpResourceLink> sameAnswer(String toolName, String encodedProfile, JsonNode arguments) {
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
        boolean narrowingArgument = FLAMEGRAPH_NARROWING_ARGUMENTS.stream()
                .anyMatch(argument -> arguments.has(argument) && !arguments.path(argument).isNull());
        JsonNode detailNode = arguments.path(DETAIL_ARGUMENT);
        String detail = detailNode.isString() ? detailNode.asString().strip() : "";
        return narrowingArgument || !(detail.isEmpty() || TEMPLATE_DETAIL.equalsIgnoreCase(detail));
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
     * {@link McpResourceNotFoundException}, which the envelope answers with {@code -32602} and this
     * sentence ({@code 2026-07-28} forbids the older {@code -32002}). The
     * diagnostics resource is named only when this instance actually has one: an endpoint built
     * without diagnostics would otherwise advertise, in its refusal, a URI that same refusal is
     * about to be sent for.
     */
    private String unknown(String uri) {
        List<String> served = new ArrayList<>(List.of(PROFILES_URI, PROFILES_TEMPLATE, McpServerInfo.URI));
        if (diagnostics != null) {
            served.add(McpDiagnostics.URI);
        }
        served.addAll(List.of(EVIDENCE_TEMPLATE, SUMMARY_TEMPLATE));
        if (servesSchema()) {
            served.add(SCHEMA_TEMPLATE);
        }
        if (servesFindings()) {
            served.add(FINDINGS_TEMPLATE);
        }
        return "No resource at '" + uri + "'. This server serves "
                + String.join(LIST_SEPARATOR, served) + LAST_SEPARATOR + FLAMEGRAPH_TEMPLATE + ".";
    }
}
