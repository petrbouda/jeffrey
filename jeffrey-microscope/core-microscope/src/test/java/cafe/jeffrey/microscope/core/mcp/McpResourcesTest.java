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

import cafe.jeffrey.microscope.core.mcp.tools.ProfileDocumentFixtures;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileFindings;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileSchema;
import cafe.jeffrey.microscope.mcp.protocol.CompositeToolset;
import cafe.jeffrey.microscope.mcp.protocol.McpCacheHint;
import cafe.jeffrey.microscope.mcp.protocol.McpResource;
import cafe.jeffrey.microscope.mcp.protocol.McpResourceLink;
import cafe.jeffrey.microscope.mcp.protocol.McpResourceNotFoundException;
import cafe.jeffrey.microscope.mcp.protocol.McpResourceProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolProvider;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a resource runs the tool that would have answered the equivalent call, so what matters here
 * is that a URI is routed to the right tool with the right arguments — and that one this server does
 * not serve is refused rather than resolved to the nearest thing.
 */
class McpResourcesTest {

    private static final String SCHEMA_TEMPLATE = "jeffrey://profile/{profileId}/schema";
    private static final String FINDINGS_TEMPLATE = "jeffrey://profile/{profileId}/findings";

    private final RecordingProfileTools profileTools = new RecordingProfileTools();
    private final RecordingFlamegraphTools flamegraphTools = new RecordingFlamegraphTools();

    /**
     * The real toolset over stub tool classes, so a read goes through the dispatch a live call would.
     */
    private final RecordingDocuments documents = new RecordingDocuments();

    private final McpResources resources = new McpResources(new CompositeToolset(List.of(
            new ReflectiveToolset(profileTools, "profiles"),
            new ReflectiveToolset(flamegraphTools, "flamegraph"))),
            documents::documents, McpTestProperties.of(true, true, true, Set.of()), null, false);

    @Nested
    class Catalogue {

        @Test
        void offersTheProfileListAsAConcreteResource() {
            List<McpResource> offered = resources.resources();

            assertEquals(2, offered.size());
            assertEquals("jeffrey://profiles", offered.getFirst().uri());
            assertTrue(offered.getFirst().description().contains("first 100 matching profiles"));
        }

        /**
         * Per-profile resources are templates rather than concrete: one entry per event type of every
         * profile would be a list nobody reads and almost none of which anybody wants.
         */
        @Test
        void offersThePerProfileResourcesAsTemplates() {
            List<String> uris = resources.templates().stream().map(McpResource::uri).toList();

            assertEquals(
                    List.of("jeffrey://profiles{?cursor,limit}",
                            "jeffrey://profile/{profileId}/summary",
                            "jeffrey://profile/{profileId}/flamegraph/{eventType}"),
                    uris);
        }

        @Test
        void readsTheCatalogueThroughTheListTool() {
            McpResourceProvider.Contents contents = resources.read("jeffrey://profiles");

            assertEquals("listed", contents.text());
            assertEquals(McpResource.TEXT_MARKDOWN, contents.mimeType());
        }
    }

    @Test
    void offersServerDiagnosticsEvenWithoutProfileTools() {
        McpResources limited = new McpResources(new ReflectiveToolset(flamegraphTools, "flamegraph"),
                documents::documents, McpTestProperties.of(true, true, true, Set.of()), null, false);
        assertEquals(List.of("jeffrey://server"), limited.resources().stream().map(McpResource::uri).toList());
        McpResourceProvider.Contents info = limited.read("jeffrey://server");
        assertEquals(McpResource.APPLICATION_JSON, info.mimeType());
        assertTrue(info.text().contains("effectiveFamilies"));
    }

    @Test
    void routesCatalogueContinuationToTheSameListTool() {
        resources.read("jeffrey://profiles?cursor=next%2Dpage&limit=17");
        assertEquals("next-page", profileTools.cursor);
        assertEquals(17, profileTools.limit);
    }

    @Test
    void refusesUnknownDuplicateAndMalformedCatalogueParameters() {
        for (String uri : List.of("jeffrey://profiles?offset=1", "jeffrey://profiles?cursor=a&cursor=b",
                "jeffrey://profiles?limit=no", "jeffrey://profiles?cursor", "jeffrey://profiles?cursor=%ZZ")) {
            assertThrows(IllegalArgumentException.class, () -> resources.read(uri), uri);
        }
    }

    @Nested
    class Routing {

        @Test
        void readsAProfileSummaryThroughTheSummaryTool() {
            McpResourceProvider.Contents contents = resources.read("jeffrey://profile/p-1/summary");

            assertEquals("p-1", profileTools.summarisedProfileId);
            assertEquals(McpResource.APPLICATION_JSON, contents.mimeType());
        }

        @Test
        void readsAFlamegraphThroughTheExportTool() {
            resources.read("jeffrey://profile/p-1/flamegraph/jdk.ExecutionSample");

            assertEquals("p-1", flamegraphTools.exportedProfileId);
            assertEquals("jdk.ExecutionSample", flamegraphTools.exportedEventType);
        }

        /**
         * An event type carries dots and a profile id could carry anything, so a client is entitled to
         * percent-encode either.
         */
        @Test
        void decodesAPercentEncodedSegment()  {
            resources.read("jeffrey://profile/p%2F1/flamegraph/jdk.ObjectAllocationSample");

            assertEquals("p/1", flamegraphTools.exportedProfileId);
        }
    }

    /**
     * Guessing which resource a near-miss meant would answer a question nobody asked, so every shape
     * this server does not serve is refused with the ones it does.
     * <p>
     * A URI that is not served is a <em>missing subject</em> ({@link McpResourceNotFoundException}),
     * which the envelope answers with {@code -32602} and the sentence naming what is served.
     */
    @Nested
    class Refusals {

        @Test
        void refusesAUriWithADifferentScheme() {
            assertRefused("https://example.test/profiles");
        }

        @Test
        void refusesAProfileUriWithNoView() {
            assertRefused("jeffrey://profile/p-1");
        }

        @Test
        void refusesAnUnknownViewName() {
            assertRefused("jeffrey://profile/p-1/histogram");
        }

        @Test
        void refusesAFlamegraphUriWithNoEventType() {
            assertRefused("jeffrey://profile/p-1/flamegraph");
        }

        @Test
        void refusesNull() {
            assertRefused(null);
        }

        private void assertRefused(String uri) {
            McpResourceNotFoundException thrown =
                    assertThrows(McpResourceNotFoundException.class, () -> resources.read(uri));

            assertTrue(thrown.getMessage().contains("jeffrey://profiles"), thrown.getMessage());
        }
    }

    /**
     * The tool-to-resource mapping, which lives here because this is the only class that knows which
     * URIs exist. A link naming a URI {@code read} would refuse is the failure worth testing for.
     */
    @Nested
    class Links {

        @Test
        void linksASummaryToItsResource() {
            List<McpResourceLink> links = resources.linksFor(
                    "profiles_summary", Json.createObject().put("profileId", "p-1"));

            assertEquals(1, links.size());
            assertEquals("jeffrey://profile/p-1/summary", links.getFirst().uri());
            resources.read(links.getFirst().uri());
        }

        @Test
        void linksAnUnnarrowedFlamegraphToItsResource() {
            List<McpResourceLink> links = resources.linksFor("flamegraph_export",
                    Json.createObject().put("profileId", "p-1").put("eventType", "jdk.ExecutionSample"));

            assertEquals("jeffrey://profile/p-1/flamegraph/jdk.ExecutionSample", links.getFirst().uri());
            resources.read(links.getFirst().uri());
        }

        /**
         * The template takes an event type and nothing else, so it cannot stand for a filtered export.
         * Offering it anyway would put a different call tree behind the same name.
         */
        @Test
        void doesNotLinkAFlamegraphThatWasNarrowed() {
            assertTrue(resources.linksFor("flamegraph_export", Json.createObject()
                    .put("profileId", "p-1")
                    .put("eventType", "jdk.ExecutionSample")
                    .put("thresholdPct", 5)).isEmpty());
        }

        /** A window, given on the recording's epoch-millisecond clock, narrows the tree like any filter. */
        @ParameterizedTest
        @ValueSource(strings = {"startEpochMs", "endEpochMs"})
        void doesNotLinkAWindowedFlamegraph(String bound) {
            assertTrue(resources.linksFor("flamegraph_export", Json.createObject()
                    .put("profileId", "p-1")
                    .put("eventType", "jdk.ExecutionSample")
                    .put(bound, 1_772_366_400_000L)).isEmpty());
        }

        /**
         * A summary is not a call tree and a full export is a deeper one than the template returns, so
         * neither may be offered as "the same call tree"; the standard tree, asked for by name, is.
         */
        @ParameterizedTest
        @ValueSource(strings = {"summary", "full", "FULL"})
        void doesNotLinkAFlamegraphAtAnotherDetail(String detail) {
            assertTrue(resources.linksFor("flamegraph_export", Json.createObject()
                    .put("profileId", "p-1")
                    .put("eventType", "jdk.ExecutionSample")
                    .put("detail", detail)).isEmpty());
        }

        @Test
        void linksAFlamegraphAskedForAtStandardDetail() {
            List<McpResourceLink> links = resources.linksFor("flamegraph_export", Json.createObject()
                    .put("profileId", "p-1")
                    .put("eventType", "jdk.ExecutionSample")
                    .put("detail", "standard"));

            assertEquals("jeffrey://profile/p-1/flamegraph/jdk.ExecutionSample", links.getFirst().uri());
        }

        @Test
        void encodesASegmentSoTheLinkCanBeReadBack() {
            List<McpResourceLink> links = resources.linksFor(
                    "profiles_summary", Json.createObject().put("profileId", "p/1"));

            assertEquals("jeffrey://profile/p%2F1/summary", links.getFirst().uri());
            resources.read(links.getFirst().uri());
            assertEquals("p/1", profileTools.summarisedProfileId);
        }

        @Test
        void linksNothingForAToolWithNoResourceCounterpart() {
            assertTrue(resources.linksFor("jvm_gc", Json.createObject().put("profileId", "p-1")).isEmpty());
            assertTrue(resources.linksFor("profiles_summary", null).isEmpty());
            assertTrue(resources.linksFor("profiles_summary", Json.createObject()).isEmpty());
        }
    }

    /**
     * The two resources that are not one tool's answer: the profile database's schema, and the findings
     * the profile already holds. Each is offered only where the family whose tools read the same data
     * is served, answers JSON that fits its record's generated schema, and is read through the
     * profile's documents rather than by running a tool.
     */
    @Nested
    class ProfileDocuments {

        private final McpResources served = resourcesFor(
                new ReflectiveToolset(new SummaryTools(), "profiles"),
                new ReflectiveToolset(new SqlTools(), "jfr"),
                new ReflectiveToolset(new JvmTools(), "jvm"));

        @Test
        void advertisesTheSchemaOnlyWhereTheJfrFamilyIs() {
            assertTrue(templateUris(served).contains(SCHEMA_TEMPLATE));
            assertFalse(templateUris(resourcesFor(new ReflectiveToolset(new JvmTools(), "jvm")))
                    .contains(SCHEMA_TEMPLATE));
        }

        @Test
        void advertisesTheFindingsOnlyWhereTheJvmFamilyIs() {
            assertTrue(templateUris(served).contains(FINDINGS_TEMPLATE));
            assertFalse(templateUris(resourcesFor(new ReflectiveToolset(new SqlTools(), "jfr")))
                    .contains(FINDINGS_TEMPLATE));
        }

        @Test
        void describesBothAsJson() {
            for (McpResource template : served.templates()) {
                if (Set.of(SCHEMA_TEMPLATE, FINDINGS_TEMPLATE).contains(template.uri())) {
                    assertEquals(McpResource.APPLICATION_JSON, template.mimeType(), template.uri());
                }
            }
        }

        @Test
        void readsTheSchemaAsJsonThatFitsItsRecord() {
            McpResourceProvider.Contents contents = served.read("jeffrey://profile/p%201/schema");

            assertEquals(List.of("p 1"), documents.schemaReads);
            assertEquals(McpResource.APPLICATION_JSON, contents.mimeType());
            assertEquals(McpCacheHint.DYNAMIC, contents.cacheHint());
            JsonNode schema = Json.readTree(contents.text());
            McpSchemaConformance.assertConforms(schema, McpSchemaGenerator.schemaOf(ProfileSchema.class));
            assertEquals("p 1", schema.get("profileId").asString());
        }

        @Test
        void readsTheFindingsAsJsonThatFitsItsRecord() {
            McpResourceProvider.Contents contents = served.read("jeffrey://profile/p-1/findings");

            assertEquals(List.of("p-1"), documents.findingsReads);
            assertEquals(McpResource.APPLICATION_JSON, contents.mimeType());
            assertEquals(McpCacheHint.DYNAMIC, contents.cacheHint());
            JsonNode findings = Json.readTree(contents.text());
            McpSchemaConformance.assertConforms(findings, McpSchemaGenerator.schemaOf(ProfileFindings.class));
            assertEquals("NOT_COMPUTED", findings.get("status").asString());
        }

        /**
         * Listing is one half of the gate; a URI a client builds by hand must be refused the same way,
         * and the refusal must not offer the resource it is refusing.
         */
        @Test
        void refusesADocumentWhoseFamilyIsNotServed() {
            McpResources jvmOnly = resourcesFor(new ReflectiveToolset(new JvmTools(), "jvm"));
            McpResources jfrOnly = resourcesFor(new ReflectiveToolset(new SqlTools(), "jfr"));

            McpResourceNotFoundException schema = assertThrows(McpResourceNotFoundException.class,
                    () -> jvmOnly.read("jeffrey://profile/p-1/schema"));
            McpResourceNotFoundException findings = assertThrows(McpResourceNotFoundException.class,
                    () -> jfrOnly.read("jeffrey://profile/p-1/findings"));

            assertFalse(schema.getMessage().contains(SCHEMA_TEMPLATE), schema.getMessage());
            assertTrue(schema.getMessage().contains(FINDINGS_TEMPLATE), schema.getMessage());
            assertFalse(findings.getMessage().contains(FINDINGS_TEMPLATE), findings.getMessage());
            assertTrue(findings.getMessage().contains(SCHEMA_TEMPLATE), findings.getMessage());
            assertTrue(documents.schemaReads.isEmpty());
            assertTrue(documents.findingsReads.isEmpty());
        }

        @ParameterizedTest
        @ValueSource(strings = {"jeffrey://profile//schema", "jeffrey://profile/p-1/schema/events",
                "jeffrey://profile/p-1/findings/jvm", "jeffrey://profile/%20/findings", "jeffrey://profile/schema"})
        void refusesAMalformedDocumentUri(String uri) {
            McpResourceNotFoundException refused =
                    assertThrows(McpResourceNotFoundException.class, () -> served.read(uri));

            assertTrue(refused.getMessage().contains(SCHEMA_TEMPLATE), refused.getMessage());
            assertTrue(documents.schemaReads.isEmpty());
            assertTrue(documents.findingsReads.isEmpty());
        }

        /** A segment that is not valid percent-encoding is an argument the caller can fix: -32602 all the same. */
        @Test
        void refusesABadlyEncodedProfileId() {
            assertThrows(IllegalArgumentException.class, () -> served.read("jeffrey://profile/%ZZ/schema"));
        }

        /**
         * The refusal names every served URI as one list, whichever of the two documents are served:
         * commas between, "and" before the last, never a comma left behind by one that is not.
         */
        @Test
        void refusesWithAWellFormedListOfWhatIsServed() {
            String flamegraph = "jeffrey://profile/{profileId}/flamegraph/{eventType}";
            String summary = "jeffrey://profile/{profileId}/summary";
            Map<McpResources, String> expectedTails = Map.of(
                    served, summary + ", " + SCHEMA_TEMPLATE + ", " + FINDINGS_TEMPLATE + " and " + flamegraph + ".",
                    resourcesFor(new ReflectiveToolset(new SqlTools(), "jfr")),
                    summary + ", " + SCHEMA_TEMPLATE + " and " + flamegraph + ".",
                    resourcesFor(new ReflectiveToolset(new JvmTools(), "jvm")),
                    summary + ", " + FINDINGS_TEMPLATE + " and " + flamegraph + ".",
                    resourcesFor(new ReflectiveToolset(new SummaryTools(), "profiles")),
                    summary + " and " + flamegraph + ".");

            expectedTails.forEach((resources, tail) -> {
                String message = assertThrows(McpResourceNotFoundException.class,
                        () -> resources.read("jeffrey://nonsense")).getMessage();
                assertTrue(message.endsWith(tail), message);
                assertFalse(message.contains(", and"), message);
                assertFalse(message.contains(", ,"), message);
            });
        }

        @Test
        void linksTheSummaryAndTheEvidenceToTheFindings() {
            List<String> summary = linkUris(served.linksFor("profiles_summary", profile("p-1")));
            List<String> evidence = linkUris(served.linksFor("profiles_evidence", profile("p-1")));

            assertEquals(List.of("jeffrey://profile/p-1/summary", "jeffrey://profile/p-1/findings"), summary);
            assertEquals(List.of("jeffrey://profile/p-1/evidence", "jeffrey://profile/p-1/findings"), evidence);
            served.read(summary.get(1));
        }

        @Test
        void linksTheSqlCatalogueToTheSchema() {
            List<String> tables = linkUris(served.linksFor("jfr_listTables", profile("p/1")));
            List<String> describe = linkUris(served.linksFor("jfr_describeTable",
                    profile("p-1").put("tableName", "events")));

            assertEquals(List.of("jeffrey://profile/p%2F1/schema"), tables);
            assertEquals(List.of("jeffrey://profile/p-1/schema"), describe);
            served.read(tables.getFirst());
            assertEquals(List.of("p/1"), documents.schemaReads);
        }

        @Test
        void linksNoFindingsWhereTheJvmFamilyIsNotServed() {
            McpResources withoutJvm = resourcesFor(
                    new ReflectiveToolset(new SummaryTools(), "profiles"),
                    new ReflectiveToolset(new SqlTools(), "jfr"));

            assertEquals(List.of("jeffrey://profile/p-1/summary"),
                    linkUris(withoutJvm.linksFor("profiles_summary", profile("p-1"))));
        }

        private McpResources resourcesFor(McpToolProvider... families) {
            return new McpResources(new CompositeToolset(List.of(families)), documents::documents,
                    McpTestProperties.of(true, true, true, Set.of()), null, false);
        }

        private static List<String> templateUris(McpResources resources) {
            return resources.templates().stream().map(McpResource::uri).toList();
        }

        private static List<String> linkUris(List<McpResourceLink> links) {
            return links.stream().map(McpResourceLink::uri).toList();
        }

        private static ObjectNode profile(String profileId) {
            return Json.createObject().put("profileId", profileId);
        }
    }

    /** Hands out filled-in documents and records which profiles they were read for. */
    static final class RecordingDocuments {

        private final List<String> schemaReads = new ArrayList<>();
        private final List<String> findingsReads = new ArrayList<>();

        McpProfileDocuments documents() {
            return new McpProfileDocuments(
                    profileId -> {
                        schemaReads.add(profileId);
                        return ProfileDocumentFixtures.schema(profileId);
                    },
                    profileId -> {
                        findingsReads.add(profileId);
                        return ProfileDocumentFixtures.findings(profileId);
                    });
        }
    }

    public static class SummaryTools {

        @Tool(description = "One profile in summary")
        public String summary(@ToolParam(required = true, description = "which profile") String profileId) {
            return "{}";
        }

        @Tool(description = "One profile's evidence")
        public String evidence(@ToolParam(required = true, description = "which profile") String profileId) {
            return "{}";
        }
    }

    public static class SqlTools {

        @Tool(description = "The tables")
        public String listTables(@ToolParam(required = true, description = "which profile") String profileId) {
            return "- events (view)";
        }

        @Tool(description = "One table")
        public String describeTable(
                @ToolParam(required = true, description = "which profile") String profileId,
                @ToolParam(required = true, description = "which table") String tableName) {
            return "| column |";
        }
    }

    public static class JvmTools {

        @Tool(description = "The rule set")
        public String autoAnalysis(@ToolParam(required = true, description = "which profile") String profileId) {
            return "{}";
        }
    }

    /**
     * The two tools a resource read can reach, recording what they were asked for.
     */
    public static class RecordingProfileTools {

        private String summarisedProfileId;
        private String cursor;
        private Integer limit;

        @Tool(description = "Every analysed profile")
        public String list(
                @ToolParam(required = false, description = "cursor") String cursor,
                @ToolParam(required = false, description = "limit") Integer limit) {
            this.cursor = cursor;
            this.limit = limit;
            return "listed";
        }

        @Tool(description = "One profile in summary")
        public String summary(
                @ToolParam(required = true, description = "which profile") String profileId) {
            this.summarisedProfileId = profileId;
            return "{}";
        }
    }

    public static class RecordingFlamegraphTools {

        private String exportedProfileId;
        private String exportedEventType;

        @Tool(description = "One flamegraph as Markdown")
        public String export(
                @ToolParam(required = true, description = "which profile") String profileId,
                @ToolParam(required = true, description = "which event type") String eventType) {
            this.exportedProfileId = profileId;
            this.exportedEventType = eventType;
            return "# flamegraph";
        }
    }
}
