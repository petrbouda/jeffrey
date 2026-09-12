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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.McpToolResult;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfilesMcpToolsTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    MicroscopeCoreRepositories coreRepositories;

    private ProfilesMcpTools tools;

    @BeforeEach
    void setUp() {
        tools = new ProfilesMcpTools(coreRepositories);
    }

    private static ProfileInfo profile(String id, String name, String projectId) {
        return profile(id, name, projectId, true);
    }

    private static ProfileInfo profile(String id, boolean enabled) {
        return profile(id, "Profile " + id, null, enabled);
    }

    /**
     * @param enabled whether the profile has finished being built; false is a row whose recording is
     *                still being parsed
     */
    private static ProfileInfo profile(String id, String name, String projectId, boolean enabled) {
        return new ProfileInfo(
                id, projectId, projectId == null ? null : "ws-1", name, RecordingEventSource.JDK,
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

            String result = tools.list(null, null);

            assertTrue(result.contains("| yes |"), result);
            assertTrue(result.contains("| building |"), result);
            assertTrue(result.contains("still being parsed"));
        }

        @Test
        void listsEveryProfileWithItsId() {
            when(coreRepositories.findAllProfiles())
                    .thenReturn(List.of(profile("p-1", "Checkout run", "proj-1")));

            String result = tools.list(null, null);

            assertTrue(result.contains("p-1"));
            assertTrue(result.contains("Checkout run"));
        }

        /**
         * A Quick Analysis profile belongs to no project. It has to appear anyway — it is how a
         * locally opened recording shows up, and it would otherwise be invisible to a client.
         */
        @Test
        void labelsQuickAnalysisProfiles() {
            when(coreRepositories.findAllProfiles())
                    .thenReturn(List.of(profile("p-2", "Local dump", null)));

            assertTrue(tools.list(null, null).contains("quick analysis"));
        }

        @Test
        void filtersByNameCaseInsensitively() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-1", "Checkout run", "proj-1"),
                    profile("p-2", "Search run", "proj-1")));

            String result = tools.list("CHECKOUT", null);

            assertTrue(result.contains("p-1"));
            assertFalse(result.contains("p-2"));
        }

        @Test
        void reportsWhenTheLimitOmitsMatchingProfiles() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-1", "One", "proj-1"),
                    profile("p-2", "Two", "proj-1")));

            String result = tools.list(null, 1);

            // Newest first, so the single row is p-2.
            assertTrue(result.contains("p-2"));
            assertFalse(result.contains("p-1"));
            assertTrue(result.contains("Returned 1 of 2 matching profiles."), result);
            assertTrue(result.contains("nextCursor"), result);
        }

        @Test
        void reportsWhenTheLimitReturnsEveryMatchingProfile() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-1", "One", "proj-1"),
                    profile("p-2", "Two", "proj-1")));

            String result = tools.list(null, 2);

            assertTrue(result.contains("Returned 2 of 2 matching profiles."), result);
            assertFalse(result.contains("narrow `search`"), result);
        }

        @Test
        void countsMatchesBeforeApplyingTheLimit() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-1", "Checkout before", "proj-1"),
                    profile("p-2", "Unrelated", "proj-1"),
                    profile("p-3", "Checkout after", "proj-1")));

            String result = tools.list("checkout", 1);

            assertTrue(result.contains("Returned 1 of 2 matching profiles."), result);
        }

        @Test
        void reportsWhenTheDefaultLimitOmitsMatchingProfiles() {
            List<ProfileInfo> profiles = IntStream.rangeClosed(1, 101)
                    .mapToObj(index -> profile("p-" + index, "Profile " + index, "proj-1"))
                    .toList();
            when(coreRepositories.findAllProfiles()).thenReturn(profiles);

            String result = tools.list(null, null);

            assertTrue(result.contains("Returned 100 of 101 matching profiles."), result);
            assertTrue(result.contains("nextCursor"), result);
        }

        /**
         * "Nothing here" is a normal answer with a next step, not an error — a fresh installation has
         * no profiles until a recording is analysed.
         */
        @Test
        void explainsAnEmptyInstallation() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of());

            String result = tools.list(null, null);

            assertTrue(result.contains("No profiles"), result);
            assertTrue(result.contains("Returned 0 of 0 matching profiles."), result);
        }

        @Test
        void saysWhenNothingMatchedTheSearch() {
            when(coreRepositories.findAllProfiles())
                    .thenReturn(List.of(profile("p-1", "Checkout run", "proj-1")));

            assertTrue(tools.list("nothing-like-this", null).contains("No profile matches"));
        }

        /**
         * A pipe in a profile name would split the Markdown row it sits in, silently shifting every
         * later column.
         */
        @Test
        void keepsAPipeInANameOffTheColumnBoundaries() {
            when(coreRepositories.findAllProfiles())
                    .thenReturn(List.of(profile("p-1", "before|after", "proj-1")));

            String row = tools.list(null, null).lines()
                    .filter(line -> line.contains("p-1"))
                    .findFirst()
                    .orElseThrow();

            assertFalse(row.contains("before|after"));
            assertTrue(row.contains("before/after"));
        }
    }

    @Nested
    class Pagination {

        @Test
        void traversesBeyondMaximumWithoutDuplicates() {
            List<ProfileInfo> profiles = IntStream.range(0, 1103)
                    .mapToObj(index -> profile("p-%04d".formatted(index), "Profile " + index, null))
                    .toList().reversed();
            when(coreRepositories.findAllProfiles()).thenReturn(profiles);
            List<String> ids = new ArrayList<>();
            String cursor = null;
            do {
                Page page = page(null, 1000, cursor);
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
                    profile("p-1", "Checkout before", null), profile("p-2", "Checkout after", null)));
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

        @Test
        void boundsLongNamesWithoutLosingIdentifiers() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-2", "\"\n".repeat(120_001), null), profile("p-1", true)));
            Page first = page(null, 1, null);
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
                    .mapToObj(index -> profile("p-%04d".formatted(index), "\u0001".repeat(256), null)).toList());
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
