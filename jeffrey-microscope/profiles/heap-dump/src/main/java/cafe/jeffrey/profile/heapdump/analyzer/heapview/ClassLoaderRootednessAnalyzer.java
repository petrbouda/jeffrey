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
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderHierarchyEdge;
import cafe.jeffrey.profile.heapdump.view.HeapView;

/**
 * Computes the set of loaders that are <em>effectively</em> rooted — i.e.
 * either directly listed in the HPROF GC-root table, or reachable from a
 * directly-rooted loader by walking back up the {@code parent} chain.
 *
 * <p>The naive "is this loader instance a GC root?" check misclassifies most
 * JDK parent loaders: HotSpot typically marks only the leaf-most application
 * loader as a root, even though Platform and Bootstrap loaders are obviously
 * alive (they're held via the leaf's {@code parent} field). This analyzer
 * fixes that by promoting every ancestor of a rooted loader to "rooted",
 * leaving the {@link cafe.jeffrey.profile.heapdump.model.UnloadabilityVerdict#PINNED_TRANSITIVE}
 * verdict reserved for genuine redeploy-leak signatures (loader has live
 * instances, is not rooted, and has no rooted descendant either).
 *
 * <p>Bootstrap (id {@code 0}) is always considered rooted by definition — it
 * has no instance object to mark in the HPROF root table, yet it is the JVM's
 * permanent root of every class definition.
 */
public final class ClassLoaderRootednessAnalyzer {

    private static final long BOOTSTRAP_LOADER_ID = 0L;

    private ClassLoaderRootednessAnalyzer() {
    }

    /**
     * @param view       open heap view
     * @param loaderIds  every loader id known to the report (including 0 for bootstrap)
     * @param edges      child → parent edges produced by {@link ClassLoaderHierarchyAnalyzer}
     * @return the set of loader ids that are effectively rooted; safe to query
     *         with {@link Set#contains(Object)} per loader
     */
    public static Set<Long> analyze(
            HeapView view,
            Collection<Long> loaderIds,
            Collection<ClassLoaderHierarchyEdge> edges) throws SQLException {

        Set<Long> rooted = new HashSet<>();
        for (Long id : loaderIds) {
            if (id == null) {
                continue;
            }
            if (id == BOOTSTRAP_LOADER_ID || view.isGcRoot(id)) {
                rooted.add(id);
            }
        }

        Map<Long, Long> parentByChild = new HashMap<>(edges.size());
        for (ClassLoaderHierarchyEdge edge : edges) {
            parentByChild.put(edge.childId(), edge.parentId());
        }

        // For each directly-rooted loader, promote its entire ancestor chain
        // (via child → parent edges) to also rooted. The snapshot avoids
        // mutating the set we iterate over.
        Set<Long> seeds = new HashSet<>(rooted);
        for (Long seed : seeds) {
            Long current = parentByChild.get(seed);
            while (current != null && !rooted.contains(current)) {
                rooted.add(current);
                current = parentByChild.get(current);
            }
        }
        return rooted;
    }
}
