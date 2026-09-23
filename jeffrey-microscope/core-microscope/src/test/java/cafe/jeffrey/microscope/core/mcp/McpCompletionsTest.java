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

import cafe.jeffrey.profile.mcp.McpCompletion;
import cafe.jeffrey.profile.mcp.McpCompletionRef;
import cafe.jeffrey.profile.mcp.McpOutputSchema;
import cafe.jeffrey.profile.mcp.McpToolProvider;
import cafe.jeffrey.profile.mcp.McpToolResult;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one argument nobody can type from memory. A profile id is a UUIDv7, so a client that cannot
 * offer them is a client whose user has to run {@code profiles_list} and copy from its output.
 */
class McpCompletionsTest {

    private static final McpCompletionRef SUMMARY_TEMPLATE =
            new McpCompletionRef(McpCompletionRef.TYPE_RESOURCE, "jeffrey://profile/{profileId}/summary");
    private static final McpCompletionRef PROMPT =
            new McpCompletionRef(McpCompletionRef.TYPE_PROMPT, "analyze-jfr");

    private static final ExternalMcpProperties ALL_FAMILIES =
            new ExternalMcpProperties(true, true, true, Set.of());

    private final CatalogueTools catalogue = new CatalogueTools();

    private McpCompletions completions(ExternalMcpProperties properties) {
        McpToolProvider toolset = new ReflectiveToolset(catalogue, "profiles");
        return new McpCompletions(() -> toolset, properties);
    }

    private McpCompletion complete(String value) {
        return completions(ALL_FAMILIES).complete(SUMMARY_TEMPLATE, "profileId", value);
    }

    @Nested
    class Availability {

        @Test
        void offersCompletionsWhileTheCatalogueFamilyIsAdvertised() {
            assertTrue(completions(ALL_FAMILIES).isAvailable());
        }

        @Test
        void offersNoneWhenTheCatalogueFamilyIsNotSelected() {
            assertFalse(completions(new ExternalMcpProperties(true, true, true, Set.of("heap"))).isAvailable());
        }

        /**
         * Availability must not build the toolset: it is read while answering {@code initialize}, which
         * has to answer even when assembling the toolset would fail.
         */
        @Test
        void decidesAvailabilityWithoutResolvingTheToolset() {
            McpCompletions unresolvable = new McpCompletions(() -> {
                throw new IllegalStateException("the toolset was resolved");
            }, ALL_FAMILIES);

            assertTrue(unresolvable.isAvailable());
        }
    }

    @Nested
    class Completing {

        @Test
        void offersEveryProfileForAnEmptyValue() {
            assertEquals(3, complete("").values().size());
        }

        @Test
        void narrowsByPrefix() {
            McpCompletion completion = complete("01a");

            assertEquals(List.of("01a-one", "01a-two"), completion.values());
            assertEquals(2, completion.total());
            assertFalse(completion.hasMore());
        }

        @Test
        void ignoresCaseInWhatWasTypedSoFar() {
            assertEquals(2, complete("01A").values().size());
        }

        @Test
        void completesThePromptArgumentToo() {
            assertEquals(3, completions(ALL_FAMILIES).complete(PROMPT, "profileId", "").values().size());
        }
    }

    @Nested
    class Refusals {

        /** The only argument this server can complete; anything else is somebody else's business. */
        @Test
        void completesNoOtherArgument() {
            assertTrue(completions(ALL_FAMILIES).complete(SUMMARY_TEMPLATE, "eventType", "").values().isEmpty());
        }

        /** The catalogue resource takes no profile id, so there is nothing to complete on it. */
        @Test
        void completesNothingForATemplateWithoutAProfileId() {
            McpCompletionRef catalogueRef =
                    new McpCompletionRef(McpCompletionRef.TYPE_RESOURCE, "jeffrey://profiles{?cursor,limit}");

            assertTrue(completions(ALL_FAMILIES).complete(catalogueRef, "profileId", "").values().isEmpty());
        }

        /**
         * A completion is offered while somebody types. Failing the request would put an error in the
         * client's face for a convenience that merely could not be provided.
         */
        @Test
        void answersEmptyWhenTheCatalogueCannotBeRead() {
            McpToolProvider failing = new ReflectiveToolset(new FailingCatalogueTools(), "profiles");

            assertEquals(McpCompletion.EMPTY,
                    new McpCompletions(() -> failing, ALL_FAMILIES).complete(SUMMARY_TEMPLATE, "profileId", ""));
        }
    }

    /** Stands in for the real catalogue, answering with the structured shape completions read. */
    public static class CatalogueTools {

        private static final String SCHEMA = """
                {"type":"object","properties":{"profiles":{"type":"array"}},"required":["profiles"]}
                """;

        @Tool(description = "List analysed profiles")
        @McpOutputSchema(SCHEMA)
        public McpToolResult list(
                @ToolParam(required = false, description = "search") String search,
                @ToolParam(required = false, description = "limit") Integer limit,
                @ToolParam(required = false, description = "cursor") String cursor) {

            ObjectNode structured = Json.createObject();
            for (String id : List.of("01a-one", "01a-two", "02b-three")) {
                structured.withArrayProperty("profiles").addObject().put("profileId", id);
            }
            return new McpToolResult("listed", structured);
        }
    }

    public static class FailingCatalogueTools {

        @Tool(description = "List analysed profiles")
        @McpOutputSchema("""
                {"type":"object","properties":{"profiles":{"type":"array"}},"required":["profiles"]}
                """)
        public McpToolResult list(
                @ToolParam(required = false, description = "search") String search,
                @ToolParam(required = false, description = "limit") Integer limit,
                @ToolParam(required = false, description = "cursor") String cursor) {

            throw new IllegalStateException("the catalogue is unavailable");
        }
    }
}
