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

import cafe.jeffrey.profile.mcp.CompositeToolset;
import cafe.jeffrey.profile.mcp.McpResource;
import cafe.jeffrey.profile.mcp.McpResourceProvider;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
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

            assertEquals(1, offered.size());
            assertEquals("jeffrey://profiles", offered.getFirst().uri());
        }

        /**
         * Per-profile resources are templates rather than concrete: one entry per event type of every
         * profile would be a list nobody reads and almost none of which anybody wants.
         */
        @Test
        void offersThePerProfileResourcesAsTemplates() {
            List<String> uris = resources.templates().stream().map(McpResource::uri).toList();

            assertEquals(
                    List.of("jeffrey://profile/{profileId}/summary",
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
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> resources.read(uri));

            assertTrue(thrown.getMessage().contains("jeffrey://profiles"), thrown.getMessage());
        }
    }

    /**
     * The two tools a resource read can reach, recording what they were asked for.
     */
    public static class RecordingProfileTools {

        private String summarisedProfileId;

        @Tool(description = "Every analysed profile")
        public String list() {
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
