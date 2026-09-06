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

package cafe.jeffrey.ide.plugin.idea.recording;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The view list the panel offers. Small rules, but each one has already been a bug waiting: a link
 * whose path is wrong opens Microscope's not-found page, and a reader has no way to tell that from a
 * view with nothing in it.
 */
public class ProfileViewTest {

    /**
     * {@code AUTO_ANALYSIS} used to be {@code ALL.get(1)}, which meant inserting a view above it
     * silently relabelled the link beside the findings.
     */
    @Test
    public void findsAutoAnalysisByIdentityRatherThanByPosition() {
        assertTrue(ProfileView.ALL.contains(ProfileView.AUTO_ANALYSIS));
        assertEquals("auto-analysis", ProfileView.AUTO_ANALYSIS.path());
    }

    /** Three per row, so the grid must divide evenly or the last row comes out ragged. */
    @Test
    public void fillsEveryRowOfTheTileGrid() {
        assertEquals(0, ProfileView.ALL.size() % ProfileView.COLUMNS);
    }

    /** Every tile references an icon the panel actually registered, or it renders as a broken tag. */
    @Test
    public void referencesOnlyRegisteredIcons() {
        for (ProfileView view : ProfileView.ALL) {
            assertTrue("no icon registered for " + view.iconKey(),
                    PanelIcons.BY_KEY.containsKey(view.iconKey()));
        }
    }

    @Test
    public void gatesOnlyTheViewsMicroscopeCanActuallyDisable() {
        assertTrue(ProfileView.ALL.stream()
                .filter(view -> view.feature() != null)
                .allMatch(view -> Set.of("TRACES", "SUBSECOND").contains(view.feature())));
    }

    @Test
    public void treatsAnUngatedViewAsAlwaysAvailable() {
        ProfileView flame = ProfileView.ALL.getFirst();
        assertTrue(flame.isAvailable(List.of("TRACES", "HEAP_DUMP")));

        ProfileView traces = ProfileView.ALL.getLast();
        assertTrue(traces.isAvailable(List.of("HEAP_DUMP")));
        assertFalse(traces.isAvailable(List.of("TRACES")));
    }

    @Test
    public void offersTraces() {
        assertTrue(ProfileView.ALL.stream().anyMatch(view -> "Traces".equals(view.label())));
        assertTrue(ProfileView.ALL.stream().anyMatch(view -> "traces/operations".equals(view.path())));
    }

    /** Two entries pointing at the same page, or one labelled twice, is a list nobody has read. */
    @Test
    public void listsEveryViewOnce() {
        Set<String> paths = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (ProfileView view : ProfileView.ALL) {
            assertTrue("duplicate path: " + view.path(), paths.add(view.path()));
            assertTrue("duplicate label: " + view.label(), labels.add(view.label()));
        }
    }

    /**
     * Paths are joined onto {@code /profiles/{id}/} directly, so a leading or trailing slash would
     * produce a double slash the router does not match.
     */
    @Test
    public void keepsPathsRelativeAndUnslashed() {
        for (ProfileView view : ProfileView.ALL) {
            assertFalse(view.path().isBlank());
            assertFalse(view.label().isBlank());
            assertFalse("leading slash: " + view.path(), view.path().startsWith("/"));
            assertFalse("trailing slash: " + view.path(), view.path().endsWith("/"));
        }
    }
}
