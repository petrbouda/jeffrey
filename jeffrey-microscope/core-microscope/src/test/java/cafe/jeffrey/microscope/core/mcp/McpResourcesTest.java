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

import cafe.jeffrey.profile.mcp.CompositeToolset;
import cafe.jeffrey.profile.mcp.McpResource;
import cafe.jeffrey.profile.mcp.McpResourceLink;
import cafe.jeffrey.profile.mcp.McpResourceNotFoundException;
import cafe.jeffrey.profile.mcp.McpResourceProvider;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a resource runs the tool that would have answered the equivalent call, so what matters here
 * is that a URI is routed to the right tool with the right arguments — and that one this server does
 * not serve is refused rather than resolved to the nearest thing.
 */
class McpResourcesTest {

    private final RecordingProfileTools profileTools = new RecordingProfileTools();
    private final RecordingFlamegraphTools flamegraphTools = new RecordingFlamegraphTools();

    /**
     * The real toolset over stub tool classes, so a read goes through the dispatch a live call would.
     */
    private final McpResources resources = new McpResources(new CompositeToolset(List.of(
            new ReflectiveToolset(profileTools, "profiles"),
            new ReflectiveToolset(flamegraphTools, "flamegraph"))));

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
        McpResources limited = new McpResources(new ReflectiveToolset(flamegraphTools, "flamegraph"));
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
     * A URI that is not served is a <em>missing subject</em>, which the envelope answers with
     * {@code -32002}, not the {@code -32602} an unparseable argument gets. A client can then tell a URI
     * it should stop asking for from one it merely spelled wrong.
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
