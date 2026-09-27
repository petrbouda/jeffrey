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
import cafe.jeffrey.microscope.mcp.protocol.McpCompletionRef;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpPrompt;
import cafe.jeffrey.microscope.mcp.protocol.McpPromptProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two arguments nobody can type from memory: {@code profileId} and {@code baselineProfileId}. A
 * profile id is a UUIDv7, so a client that cannot offer them is a client whose user has to run
 * {@code profiles_list} and copy from its output.
 */
class McpCompletionsTest {

    private static final McpCompletionRef SUMMARY_TEMPLATE =
            new McpCompletionRef(McpCompletionRef.TYPE_RESOURCE, "jeffrey://profile/{profileId}/summary");
    private static final McpCompletionRef PROMPT =
            new McpCompletionRef(McpCompletionRef.TYPE_PROMPT, "analyze-jfr");
    private static final McpCompletionRef COMPARE_PROMPT =
            new McpCompletionRef(McpCompletionRef.TYPE_PROMPT, "compare-jfr");
    private static final McpCompletionRef ARGUMENTLESS_PROMPT =
            new McpCompletionRef(McpCompletionRef.TYPE_PROMPT, "report");

    private static final McpPrompt.Argument PROFILE_ID =
            new McpPrompt.Argument("profileId", "The profile", false);
    private static final McpPrompt.Argument BASELINE_PROFILE_ID =
            new McpPrompt.Argument("baselineProfileId", "The baseline", false);

    /** Three prompts of the shapes the registry serves: one profile, two, and none at all. */
    private static final McpPromptProvider PROMPTS = new StubPrompts(List.of(
            new McpPrompt("analyze-jfr", "Analyze", "One profile", List.of(PROFILE_ID), "body"),
            new McpPrompt("compare-jfr", "Compare", "Two profiles", List.of(PROFILE_ID, BASELINE_PROFILE_ID), "body"),
            new McpPrompt("report", "Report", "No profile", List.of(), "body")));

    private static final ExternalMcpProperties ALL_FAMILIES =
            McpTestProperties.of(true, true, true, Set.of());

    private final CatalogueTools catalogue = new CatalogueTools();

    private McpCompletions completions(ExternalMcpProperties properties) {
        McpToolProvider toolset = new ReflectiveToolset(catalogue, "profiles");
        return new McpCompletions(() -> toolset, PROMPTS, properties);
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
            assertFalse(completions(McpTestProperties.of(true, true, true, Set.of("heap", "operations"))).isAvailable());
        }

        /**
         * Availability must not build the toolset: it is read while answering {@code initialize}, which
         * has to answer even when assembling the toolset would fail.
         */
        @Test
        void decidesAvailabilityWithoutResolvingTheToolset() {
            McpCompletions unresolvable = new McpCompletions(() -> {
                throw new IllegalStateException("the toolset was resolved");
            }, PROMPTS, ALL_FAMILIES);

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

        /** {@code compare-jfr} reads its second profile from this argument, filled the same way. */
        @Test
        void completesBaselineProfileIdFromTheSameCatalogue() {
            McpCompletion completion = completions(ALL_FAMILIES).complete(COMPARE_PROMPT, "baselineProfileId", "01a");

            assertEquals(List.of("01a-one", "01a-two"), completion.values());
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
         * Four prompts declare no argument at all. Offering ids for one would invite the client to
         * send an argument {@code prompts/get} then has no use for.
         */
        @Test
        void completesNothingForAPromptThatDeclaresNoArguments() {
            assertTrue(completions(ALL_FAMILIES).complete(ARGUMENTLESS_PROMPT, "profileId", "").values().isEmpty());
        }

        /** A single-profile prompt has no baseline to complete, only the profile it declares. */
        @Test
        void completesOnlyTheArgumentsAPromptDeclares() {
            assertTrue(completions(ALL_FAMILIES).complete(PROMPT, "baselineProfileId", "").values().isEmpty());
        }

        @Test
        void completesNothingForAPromptThisServerDoesNotServe() {
            McpCompletionRef unknown = new McpCompletionRef(McpCompletionRef.TYPE_PROMPT, "no-such-skill");

            assertTrue(completions(ALL_FAMILIES).complete(unknown, "profileId", "").values().isEmpty());
        }

        /** The per-profile templates carry {@code profileId} in their path, never a baseline. */
        @Test
        void completesNoBaselineOnATemplate() {
            assertTrue(completions(ALL_FAMILIES).complete(SUMMARY_TEMPLATE, "baselineProfileId", "").values().isEmpty());
        }

        /**
         * A completion is offered while somebody types. Failing the request would put an error in the
         * client's face for a convenience that merely could not be provided.
         */
        @Test
        void answersEmptyWhenTheCatalogueCannotBeRead() {
            McpToolProvider failing = new ReflectiveToolset(new FailingCatalogueTools(), "profiles");

            assertEquals(McpCompletion.EMPTY,
                    new McpCompletions(() -> failing, PROMPTS, ALL_FAMILIES).complete(SUMMARY_TEMPLATE, "profileId", ""));
        }

        /** A catalogue answer past the result limit is the tool's failure: nothing to offer, not its rows. */
        @Test
        void answersEmptyWhenTheCatalogueAnswerIsPastTheResultLimit() {
            McpToolProvider oversized = new ReflectiveToolset(new OversizedCatalogueTools(), "profiles");

            assertEquals(McpCompletion.EMPTY,
                    new McpCompletions(() -> oversized, PROMPTS, ALL_FAMILIES).complete(SUMMARY_TEMPLATE, "profileId", ""));
        }
    }

    /** The prompt registry reduced to a fixed list. */
    private record StubPrompts(List<McpPrompt> all) implements McpPromptProvider {

        @Override
        public List<McpPrompt> prompts() {
            return all;
        }

        @Override
        public McpPrompt prompt(String name) {
            return all.stream()
                    .filter(prompt -> prompt.name().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No prompt named '" + name + "'"));
        }
    }

    record CatalogueRow(String profileId) {
    }

    record Catalogue(List<CatalogueRow> profiles) {
    }

    /** Stands in for the real catalogue, answering with the structured shape completions read. */
    public static class CatalogueTools {

        @Tool(description = "List analysed profiles")
        @McpOutputSchema(Catalogue.class)
        public McpToolResult list(
                @ToolParam(required = false, description = "search") String search,
                @ToolParam(required = false, description = "limit") Integer limit,
                @ToolParam(required = false, description = "cursor") String cursor) {

            return McpToolResult.of("listed", new Catalogue(List.of("01a-one", "01a-two", "02b-three").stream()
                    .map(CatalogueRow::new)
                    .toList()));
        }
    }

    /** A catalogue whose answer serialises past the result limit. */
    public static class OversizedCatalogueTools {

        @Tool(description = "List analysed profiles")
        @McpOutputSchema(Catalogue.class)
        public McpToolResult list(
                @ToolParam(required = false, description = "search") String search,
                @ToolParam(required = false, description = "limit") Integer limit,
                @ToolParam(required = false, description = "cursor") String cursor) {

            return McpToolResult.of("listed", new Catalogue(List.of(
                    new CatalogueRow("01a-" + "x".repeat(McpToolOutput.MAX_CHARS)))));
        }
    }

    public static class FailingCatalogueTools {

        @Tool(description = "List analysed profiles")
        @McpOutputSchema(Catalogue.class)
        public McpToolResult list(
                @ToolParam(required = false, description = "search") String search,
                @ToolParam(required = false, description = "limit") Integer limit,
                @ToolParam(required = false, description = "cursor") String cursor) {

            throw new IllegalStateException("the catalogue is unavailable");
        }
    }
}
