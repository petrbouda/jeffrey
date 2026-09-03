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

package cafe.jeffrey.microscope.mcp.tools;

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
        return new ProfileInfo(
                id, projectId, projectId == null ? null : "ws-1", name, RecordingEventSource.JDK,
                START, START.plusSeconds(120), START, true, false, "rec-" + id);
    }

    @Nested
    class ListProfiles {

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
        void appliesTheLimit() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of(
                    profile("p-1", "One", "proj-1"),
                    profile("p-2", "Two", "proj-1")));

            String result = tools.list(null, 1);

            assertTrue(result.contains("p-1"));
            assertFalse(result.contains("p-2"));
        }

        /**
         * "Nothing here" is a normal answer with a next step, not an error — a fresh installation has
         * no profiles until a recording is analysed.
         */
        @Test
        void explainsAnEmptyInstallation() {
            when(coreRepositories.findAllProfiles()).thenReturn(List.of());

            assertTrue(tools.list(null, null).contains("No profiles"));
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
