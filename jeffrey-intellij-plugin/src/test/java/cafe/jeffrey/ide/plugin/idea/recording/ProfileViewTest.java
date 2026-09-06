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
        assertTrue(ProfileView.RECORDING.contains(ProfileView.AUTO_ANALYSIS));
        assertEquals("auto-analysis", ProfileView.AUTO_ANALYSIS.path());
    }

    /** Three per row, so each grid must divide evenly or the last row comes out ragged. */
    @Test
    public void fillsEveryRowOfBothTileGrids() {
        assertEquals(0, ProfileView.RECORDING.size() % ProfileView.COLUMNS);
        assertEquals(0, ProfileView.HEAP.size() % ProfileView.COLUMNS);
    }

    /**
     * The two lists share no route. A flame graph means nothing for a heap dump and a dominator tree
     * means nothing for a recording; an overlap would be a tile that opens an empty page.
     */
    @Test
    public void keepsTheRecordingAndHeapListsDisjoint() {
        Set<String> recordingPaths = ProfileView.RECORDING.stream().map(ProfileView::path)
                .collect(java.util.stream.Collectors.toSet());
        for (ProfileView view : ProfileView.HEAP) {
            assertFalse("shared route: " + view.path(), recordingPaths.contains(view.path()));
        }
    }

    /** Every heap route lives under the heap-dump section of Microscope. */
    @Test
    public void pointsEveryHeapViewAtAHeapDumpRoute() {
        for (ProfileView view : ProfileView.HEAP) {
            assertTrue(view.path(), view.path().startsWith("heap-dump/"));
        }
    }

    /** Every tile references an icon the panel actually registered, or it renders as a broken tag. */
    @Test
    public void referencesOnlyRegisteredIcons() {
        for (ProfileView view : ProfileView.HEAP) {
            assertTrue("no icon registered for " + view.iconKey(),
                    PanelIcons.BY_KEY.containsKey(view.iconKey()));
        }
        for (ProfileView view : ProfileView.RECORDING) {
            assertTrue("no icon registered for " + view.iconKey(),
                    PanelIcons.BY_KEY.containsKey(view.iconKey()));
        }
    }

    @Test
    public void gatesOnlyTheViewsMicroscopeCanActuallyDisable() {
        assertTrue(ProfileView.RECORDING.stream()
                .filter(view -> view.feature() != null)
                .allMatch(view -> Set.of("TRACES", "SUBSECOND").contains(view.feature())));
    }

    @Test
    public void treatsAnUngatedViewAsAlwaysAvailable() {
        ProfileView flame = ProfileView.RECORDING.getFirst();
        assertTrue(flame.isAvailable(List.of("TRACES", "HEAP_DUMP")));

        ProfileView traces = ProfileView.RECORDING.getLast();
        assertTrue(traces.isAvailable(List.of("HEAP_DUMP")));
        assertFalse(traces.isAvailable(List.of("TRACES")));
    }

    @Test
    public void offersTraces() {
        assertTrue(ProfileView.RECORDING.stream().anyMatch(view -> "Traces".equals(view.label())));
        assertTrue(ProfileView.RECORDING.stream().anyMatch(view -> "traces/operations".equals(view.path())));
    }

    /** Two entries pointing at the same page, or one labelled twice, is a list nobody has read. */
    @Test
    public void listsEveryViewOnce() {
        Set<String> paths = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (ProfileView view : ProfileView.RECORDING) {
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
        List<ProfileView> all = new java.util.ArrayList<>(ProfileView.RECORDING);
        all.addAll(ProfileView.HEAP);
        for (ProfileView view : all) {
            assertFalse(view.path().isBlank());
            assertFalse(view.label().isBlank());
            assertFalse("leading slash: " + view.path(), view.path().startsWith("/"));
            assertFalse("trailing slash: " + view.path(), view.path().endsWith("/"));
        }
    }
}
