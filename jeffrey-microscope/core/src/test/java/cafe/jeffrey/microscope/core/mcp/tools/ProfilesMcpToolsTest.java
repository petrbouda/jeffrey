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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.McpTestProperties;
import cafe.jeffrey.microscope.mcp.protocol.McpCursor;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.core.profile.model.ProfileInfo;
import cafe.jeffrey.microscope.common.model.RecordingEventSource;
import cafe.jeffrey.microscope.core.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.mcp.server.JeffreyMcpServer;
import cafe.jeffrey.microscope.mcp.server.McpNextToolConformance;
import cafe.jeffrey.microscope.mcp.server.McpToolOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfilesMcpToolsTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    private static final JsonNode SCHEMA = McpSchemaGenerator.schemaOf(ProfilesMcpTools.ProfilePage.class);
    private static final String START_AGAIN = "omit cursor to start again";

    @Mock
    MicroscopeCoreRepositories coreRepositories;

    private ProfilesMcpTools tools;

    @BeforeEach
    void setUp() {
        tools = new ProfilesMcpTools(coreRepositories, EVERY_FAMILY);
        // The page links the recordings page, built off the request being served.
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static ProfileInfo profile(String id, String name) {
        return profile(id, name, true);
    }

    private static ProfileInfo profile(String id, boolean enabled) {
        return profile(id, "Profile " + id, enabled);
    }

    /**
     * @param enabled whether the profile has finished being built; false is a row whose recording is
     *                still being parsed
     */
    private static ProfileInfo profile(String id, String name, boolean enabled) {
        return new ProfileInfo(
                id, name, RecordingEventSource.JFR,
                START, START.plusSeconds(120), START, enabled, false, "rec-" + id);
    }

    @Nested
    class ListProfiles {

        /**
         * A profile row exists before its recording has been parsed, so the catalogue has to
         * distinguish a profile that can be analysed from one that merely has an id.
         */
        @Test
        void marksAProfileThatIsStillBeingBuilt() {
            when(coreRepositories.findAllProfiles())
                    .thenReturn(List.of(profile("p-1", true), profile("p-2", false)));

            String result = tools.list(null, null, null).text();

            assertTrue(result.contains("| yes |"), result);
            assertTrue(result.contains("| building |"), result);
            assertTrue(result.contains("still being parsed"));
            assertTrue(result.contains("recordings_status"), result);
        }

        /** The building note routes to recordings_status, so it goes when that family is withheld. */
        @Test
        void leavesOutTheBuildingNoteWhenTheRecordingsFamilyIsWithheld() {
            when(coreRepositories.findAllProfiles())
                    .thenReturn(List.of(profile("p-1", true), profile("p-2", false)));
            ProfilesMcpTools trimmed = new ProfilesMcpTools(coreRepositories, AdvertisedFamilies.of(
                    McpTestProperties.of(true, true, true, Set.of("profiles", "flamegraph"))));

            String result = trimmed.list(null, null, null).text();

            assertTrue(result.contains("| building |"), result);
            assertFalse(result.contains("recordings_status"), result);
        }

        @Test
        void listsEveryProfileWithItsId() {
            when(coreRepositories.findAllProfiles())
                    .thenReturn(List.of(profile("p-1", "Checkout run")));

            String result = tools.list(null, null, null).text();

            assertTrue(result.contains("p-1"));
            assertTrue(result.contains("Checkout run"));
        }

        /**
         * Each row links its own profile, so a profile that was only listed - a comparison's baseline -
         * is linked from the answer it came from rather than from the list's page or a URL guessed.
         */
        @Test
        void eachRowLinksItsProfile() {
            when(coreRepositories.findAllProfiles())
                    .thenReturn(List.of(profile("p-1", "Checkout run"), profile("p-2", true)));

            JsonNode data = tools.list(null, null, null).structuredContent();

            McpSchemaConformance.assertConforms(data, SCHEMA);
            for (JsonNode row : data.get("profiles")) {
                String uiLink = row.get("uiLink").asString();
                assertTrue(uiLink.endsWith("/profiles/" + row.get("profileId").asString()), uiLink);
                UiLinkRoutes.assertResolves(uiLink);
            }
        }

        /**
         * The table's ISO strings are for reading; the structured row carries the same instants as
         * UTC epoch milliseconds and the span in milliseconds, so a caller never parses a date.
         */
        @Test
        void carriesTimeAsEpochMillisecondsAndLeavesTheIsoStringsToTheText() {
            when(coreRepositories.findAllProfiles())
                    .thenReturn(List.of(profile("p-1", "Checkout run")));

            Page page = page(null, null, null);
            JsonNode row = page.data().path("profiles").get(0);

            assertEquals(120_000, row.path("durationMs").asLong());
            assertEquals(START.toEpochMilli(), row.path("recordedEpochMs").asLong());
            assertFalse(row.has("duration"), row.toString());
            assertFalse(row.has("recorded"), row.toString());
            assertTrue(page.text().contains("PT2M"), page.text());
            assertTrue(page.text().contains(START.toString()), page.text());
        }

        @Test
        void saysWhetherEachProfileIsReadyAsAnEnum() {
            when(coreRepositories.findAllProfiles())
                    .thenReturn(List.of(profile("p-2", false), profile("p-1", true)));

            JsonNode rows = page(null, null, null).data().path("profiles");

            assertEquals("BUILDING", rows.get(0).path("ready").asString());
            assertEquals("YES", rows.get(1).path("ready").asString());
        }

        /** Every shape the page takes is one the advertised schema admits, nulls included. */
        @Test
        void everyPageConformsToTheGeneratedSchema() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    new ProfileInfo("p-3", null, RecordingEventSource.JFR,
                            null, null, START, false, false, null),
                    profile("p-2", "N".repeat(4_000)),
                    profile("p-1", "Checkout run")));

            for (Integer limit : List.of(1, 3)) {
                McpSchemaConformance.assertConforms(page(null, limit, null).data(), SCHEMA);
            }
            when(coreRepositories.findAllProfiles()).thenReturn(List.of());
            McpSchemaConformance.assertConforms(page(null, null, null).data(), SCHEMA);
        }

        /** An empty catalogue, or a search nothing matches, is a status with its reason. */
        @Test
        void saysEmptyWithAReasonWhenNothingIsListed() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of());

            JsonNode empty = page(null, null, null).data();

            McpSchemaConformance.assertConforms(empty, SCHEMA);
            assertEquals("EMPTY", empty.path("status").asString());
            assertTrue(empty.path("reason").asString().contains("No profiles have been analysed yet"), empty.toString());

            when(coreRepositories.findAllProfiles()).thenReturn(List.of(profile("p-1", "Checkout run")));
            JsonNode unmatched = page("nothing like it", null, null).data();

            assertEquals("EMPTY", unmatched.path("status").asString());
            assertEquals("No profile matches the search.", unmatched.path("reason").asString());
        }

        @Test
        void saysOkWithoutAReasonWhenProfilesAreListed() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(profile("p-1", "Checkout run")));

            JsonNode data = page(null, null, null).data();

            assertEquals("OK", data.path("status").asString());
            assertTrue(data.path("reason").isNull());
        }

        /**
         * The text is a Markdown table, so it ends with the footer rendered from the same record: a host
         * that hands the model only the text still gives it the link and the next page's call.
         */
        @Test
        void theTableEndsWithTheFooterOfItsRecord() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-1", "Checkout run"), profile("p-2", "Search run")));

            Page page = page(null, 1, null);

            assertEquals(1, page.data().path("followUp").path("nextTools").size());
            MarkdownFooters.assertRenderedFrom(page.text(), page.data());
        }

        @Test
        void anEmptyCatalogueEndsWithTheFooterToo() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of());

            Page page = page(null, null, null);

            MarkdownFooters.assertRenderedFrom(page.text(), page.data());
        }

        /** The catalogue's page for the user is the recordings list, which shows every profile. */
        @Test
        void linksTheRecordingsPage() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(profile("p-1", "Checkout run")));

            String uiLink = page(null, null, null).data().path("uiLink").asString();

            UiLinkRoutes.assertResolves(uiLink);
            assertTrue(uiLink.endsWith("/recordings"), uiLink);
        }

        /**
         * The next page is a call ready to pass on: the same search and limit, and the cursor this page
         * returned. A profile still being built names the call that follows its build.
         */
        @Test
        void handsBackTheNextPageAndTheBuildToFollowAsCalls() {
            when(coreRepositories.findAllProfiles())
                    .thenReturn(List.of(profile("p-1", true), profile("p-2", false), profile("p-3", true)));

            JsonNode data = page(" Profile ", 2, null).data();

            JsonNode next = data.path("followUp").path("nextTools");
            assertEquals("profiles_list", next.get(0).path("tool").asString());
            assertEquals(" Profile ", next.get(0).path("arguments").path("search").asString());
            assertEquals(2, next.get(0).path("arguments").path("limit").asInt());
            assertEquals(data.path("nextCursor").asString(), next.get(0).path("arguments").path("cursor").asString());
            assertEquals("recordings_status", next.get(1).path("tool").asString());
            assertEquals("rec-p-2", next.get(1).path("arguments").path("recordingId").asString());
            assertEquals(2, McpNextToolConformance.assertFollowable(data, CatalogueSpecs.of(
                    CatalogueSpecs.served(tools, "profiles"),
                    CatalogueSpecs.served(RecordingsMcpToolsFixture.of(null, null).build(),
                            "recordings"))));
        }

        @Test
        void theLastPageHandsBackNoNextPage() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(profile("p-1", true)));

            JsonNode data = page(null, null, null).data();

            assertEquals(0, data.path("followUp").path("nextTools").size());
        }

        /** Where the recordings family is withheld, the build is not followed with a call it cannot make. */
        @Test
        void dropsTheBuildCallWhenTheRecordingsFamilyIsWithheld() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(profile("p-2", false)));
            ProfilesMcpTools trimmed = new ProfilesMcpTools(coreRepositories, AdvertisedFamilies.of(
                    McpTestProperties.of(true, true, true, Set.of("profiles", "flamegraph"))));

            JsonNode data = trimmed.list(null, null, null).structuredContent();

            assertEquals(0, data.path("followUp").path("nextTools").size());
        }

        /** A locally opened recording's profile is listed like any other; nothing scopes the list. */
        @Test
        void listsALocallyOpenedProfile() {
            when(coreRepositories.findAllProfiles())
                    .thenReturn(List.of(profile("p-2", "Local dump")));

            String text = tools.list(null, null, null).text();

            assertTrue(text.contains("p-2"), text);
            assertTrue(text.contains("Local dump"), text);
        }

        @Test
        void filtersByNameCaseInsensitively() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-1", "Checkout run"),
                    profile("p-2", "Search run")));

            String result = tools.list("CHECKOUT", null, null).text();

            assertTrue(result.contains("p-1"));
            assertFalse(result.contains("p-2"));
        }

        @Test
        void reportsWhenTheLimitOmitsMatchingProfiles() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-1", "One"),
                    profile("p-2", "Two")));

            String result = tools.list(null, 1, null).text();

            // Newest first, so the single row is p-2.
            assertTrue(result.contains("p-2"));
            assertFalse(result.contains("p-1"));
            assertTrue(result.contains("Returned 1 of 2 matching profiles."), result);
            // The next page is named once, by the footer's Next: line, never again in prose.
            assertTrue(result.contains("- profiles_list {"), result);
            assertFalse(result.contains("Call profiles_list with the same search"), result);
            assertFalse(result.contains("Continuation resource"), result);
        }

        @Test
        void reportsWhenTheLimitReturnsEveryMatchingProfile() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-1", "One"),
                    profile("p-2", "Two")));

            String result = tools.list(null, 2, null).text();

            assertTrue(result.contains("Returned 2 of 2 matching profiles."), result);
            assertFalse(result.contains("narrow `search`"), result);
        }

        @Test
        void countsMatchesBeforeApplyingTheLimit() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-1", "Checkout before"),
                    profile("p-2", "Unrelated"),
                    profile("p-3", "Checkout after")));

            String result = tools.list("checkout", 1, null).text();

            assertTrue(result.contains("Returned 1 of 2 matching profiles."), result);
        }

        @Test
        void reportsWhenTheDefaultLimitOmitsMatchingProfiles() {
            List<ProfileInfo> profiles = IntStream.rangeClosed(1, 101)
                    .mapToObj(index -> profile("p-" + index, "Profile " + index))
                    .toList();
            when(coreRepositories.findAllProfiles()).thenReturn(profiles);

            String result = tools.list(null, null, null).text();

            assertTrue(result.contains("Returned 100 of 101 matching profiles."), result);
            assertTrue(result.contains("More profiles are available (hasMore=true)."), result);
            assertTrue(result.contains("- profiles_list {"), result);
        }

        /**
         * "Nothing here" is a normal answer with a next step, not an error — a fresh installation has
         * no profiles until a recording is analysed.
         */
        @Test
        void explainsAnEmptyInstallation() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of());

            String result = tools.list(null, null, null).text();

            assertTrue(result.contains("No profiles"), result);
            assertTrue(result.contains("Returned 0 of 0 matching profiles."), result);
        }

        @Test
        void saysWhenNothingMatchedTheSearch() {
            when(coreRepositories.findAllProfiles())
                    .thenReturn(List.of(profile("p-1", "Checkout run")));

            assertTrue(tools.list("nothing-like-this", null, null).text().contains("No profile matches"));
        }

        /**
         * A pipe in a profile name would split the Markdown row it sits in, silently shifting every
         * later column.
         */
        @Test
        void keepsAPipeInANameOffTheColumnBoundaries() {
            when(coreRepositories.findAllProfiles())
                    .thenReturn(List.of(profile("p-1", "before|after")));

            String row = tools.list(null, null, null).text().lines()
                    .filter(line -> line.contains("p-1"))
                    .findFirst()
                    .orElseThrow();

            assertFalse(row.contains("before|after"));
            assertTrue(row.contains("before\\|after"));
        }
    }

    @Nested
    class Pagination {

        @Test
        void traversesBeyondMaximumWithoutDuplicates() {
            List<ProfileInfo> profiles = IntStream.range(0, 1103)
                    .mapToObj(index -> profile("p-%04d".formatted(index), "Profile " + index))
                    .toList().reversed();
            when(coreRepositories.findAllProfiles()).thenReturn(profiles);
            List<String> ids = new ArrayList<>();
            String cursor = null;
            do {
                Page page = page(null, 1000, cursor);
                McpSchemaConformance.assertConforms(page.data(), SCHEMA);
                assertEquals(1103, page.data().path("total").asInt());
                assertTrue(page.data().path("complete").asBoolean());
                assertEquals(page.data().path("profiles").size(), page.data().path("returned").asInt());
                assertTrue(page.text().length() <= McpToolOutput.MAX_CHARS);
                assertTrue(page.data().toString().length() <= McpToolOutput.MAX_CHARS);
                for (JsonNode row : page.data().path("profiles")) {
                    ids.add(row.path("profileId").asString());
                    assertEquals("rec-" + row.path("profileId").asString(), row.path("recordingId").asString());
                }
                cursor = nextCursor(page);
                assertEquals(cursor != null, page.data().path("hasMore").asBoolean());
            } while (cursor != null);
            assertEquals(1103, ids.size());
            assertEquals(ids.size(), new HashSet<>(ids).size());
            // The stub already answers newest-first, like the query it stands for, and that is the
            // order the traversal must preserve across every page.
            assertEquals(profiles.stream().map(ProfileInfo::id).toList(), ids);
        }

        @Test
        void resumesAfterEarlierInsertion() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-3", true), profile("p-2", true)));
            String cursor = nextCursor(page(null, 1, null));
            // Newest first, so a profile that lands before the cursor is a newer one: p-4 sorts ahead
            // of the page already returned and is not picked up by resuming, which is why the tool
            // says a live catalogue needs a fresh traversal to see additions.
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-4", true), profile("p-3", true), profile("p-2", true)));
            Page next = page(null, 1, cursor);
            assertEquals("p-2", next.data().path("profiles").get(0).path("profileId").asString());
            assertFalse(next.data().path("hasMore").asBoolean());
        }

        @Test
        void bindsTheCursorToNormalizedSearch() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-1", "Checkout before"), profile("p-2", "Checkout after")));
            String cursor = nextCursor(page(" CHECKOUT ", 1, null));
            assertEquals("p-1", page("checkout", 1, cursor).data()
                    .path("profiles").get(0).path("profileId").asString());
            assertThrows(IllegalArgumentException.class, () -> page("different", 1, cursor));
        }

        @Test
        void rejectsMalformedCursors() {
            for (String cursor : List.of("", "not-a-cursor", "e30", "bnVsbA", "W10")) {
                assertThrows(IllegalArgumentException.class, () -> page(null, 1, cursor), cursor);
            }
        }

        /** Every refusal tells the caller the way out: the same call without a cursor. */
        @Test
        void aRefusedCursorSaysToStartAgainWithoutOne() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-1", "Checkout before"), profile("p-2", "Checkout after")));
            String cursor = nextCursor(page("checkout", 1, null));

            for (String refused : List.of("not-a-cursor", cursor)) {
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                        () -> page("different", 1, refused));
                assertTrue(error.getMessage().contains(START_AGAIN), error.getMessage());
            }
        }

        /** Another paged tool's cursor is bound to that tool, even where its filters look the same. */
        @Test
        void refusesACursorAnotherToolHandedOut() {
            String foreign = JeffreyMcpServer.CURSOR.encode(McpCursor.Filters.of("connector_sessions", ""),
                    new McpCursor.Keyset(List.of("p-1")));

            assertThrows(IllegalArgumentException.class, () -> page(null, 1, foreign));
        }

        /** A position this tool cannot read as a single profile id is refused, not skipped over. */
        @Test
        void refusesAKeysetThatIsNotOneProfileId() {
            McpCursor.Filters filters = McpCursor.Filters.of("profiles_list", "");

            for (McpCursor.Position position : List.<McpCursor.Position>of(new McpCursor.Keyset(List.of("p-1", "p-2")),
                    new McpCursor.Keyset(List.of("")), new McpCursor.Offset(1))) {
                String cursor = JeffreyMcpServer.CURSOR.encode(filters, position);
                assertThrows(IllegalArgumentException.class, () -> page(null, 1, cursor), position.toString());
            }
        }

        @Test
        void boundsLongNamesWithoutLosingIdentifiers() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-2", "\"\n".repeat(120_001)), profile("p-1", true)));
            Page first = page(null, 1, null);
            McpSchemaConformance.assertConforms(first.data(), SCHEMA);
            JsonNode row = first.data().path("profiles").get(0);
            assertEquals("p-2", row.path("profileId").asString());
            assertEquals("rec-p-2", row.path("recordingId").asString());
            assertTrue(row.path("nameTruncated").asBoolean());
            assertTrue(row.path("name").asString().length() <= 256);
            assertTrue(first.text().length() <= McpToolOutput.MAX_CHARS);
            assertTrue(first.data().toString().length() <= McpToolOutput.MAX_CHARS);
            assertEquals("p-1", page(null, 1, nextCursor(first)).data()
                    .path("profiles").get(0).path("profileId").asString());
        }

        @Test
        void boundsEscapedJsonAndReportsOnlyRowsPresentInBothRepresentations() {
            when(coreRepositories.findAllProfiles()).thenReturn(IntStream.range(0, 1000)
                    .mapToObj(index -> profile("p-%04d".formatted(index), "\u0001".repeat(256))).toList());
            Page first = page(null, 1000, null);
            int returned = first.data().path("returned").asInt();
            assertTrue(returned > 0 && returned < 1000);
            assertEquals(returned, first.data().path("profiles").size());
            assertEquals(returned, first.text().lines().filter(line -> line.startsWith("| p-")).count());
            assertTrue(first.text().startsWith("Returned " + returned + " of 1000 matching profiles."));
            assertTrue(first.data().toString().length() <= McpToolOutput.MAX_CHARS);
            // Newest first: the page ran p-0999 down to p-(1000-returned), so the next row below it
            // is p-(999-returned).
            assertEquals("p-%04d".formatted(999 - returned), page(null, 1, nextCursor(first)).data()
                    .path("profiles").get(0).path("profileId").asString());
        }

        @Test
        void emptyCatalogueIsACompleteTerminalPage() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of());
            Page empty = page(null, null, null);
            assertEquals(0, empty.data().path("returned").asInt());
            assertEquals(0, empty.data().path("total").asInt());
            assertTrue(empty.data().path("complete").asBoolean());
            assertFalse(empty.data().path("hasMore").asBoolean());
            assertTrue(empty.data().path("nextCursor").isNull());
        }
    }

    private static String nextCursor(Page page) {
        JsonNode cursor = page.data().path("nextCursor");
        return cursor.isNull() ? null : cursor.asString();
    }

    private Page page(String search, Integer limit, String cursor) {
        McpToolResult result = tools.list(search, limit, cursor);
        return new Page(result.text(), result.structuredContent());
    }

    private record Page(String text, ObjectNode data) {
    }

}
