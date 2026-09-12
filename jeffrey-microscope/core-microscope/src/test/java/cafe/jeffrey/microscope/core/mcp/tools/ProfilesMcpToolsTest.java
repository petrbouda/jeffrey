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
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

            assertTrue(result.contains("p-1"));
            assertFalse(result.contains("p-2"));
            assertTrue(result.contains("Returned 1 of 2 matching profiles."), result);
            assertTrue(result.contains("Increase `limit` (maximum 1000) or narrow `search`"), result);
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
            assertTrue(result.contains("Increase `limit` (maximum 1000)"), result);
        }

        @Test
        void reportsOnlyCompleteRowsWhenTheMaximumLimitAlsoHitsTheOutputCap() {
            List<ProfileInfo> profiles = IntStream.rangeClosed(1, 1001)
                    .mapToObj(index -> profile(
                            "p-" + index,
                            index == 1 ? "x".repeat(120_001) : "Profile " + index,
                            "proj-1"))
                    .toList();
            when(coreRepositories.findAllProfiles()).thenReturn(profiles);

            String result = tools.list(null, 1000);

            assertTrue(result.startsWith("Returned 0 of 1001 matching profiles."), result);
            assertFalse(result.contains("p-1"), result);
            assertTrue(result.contains("output size limit omitted 1000 selected profiles"), result);
            assertTrue(result.contains("The maximum `limit` is 1000; narrow `search`"), result);
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

}
