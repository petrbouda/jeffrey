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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderHierarchyEdge;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Walks the {@code java.lang.ClassLoader.parent} field of every known
 * loader instance and emits one {@link ClassLoaderHierarchyEdge} per loader.
 *
 * <p>HPROF doesn't store the parent edge directly; the value is read from the
 * loader's instance body, which requires a heap view with the {@code .hprof}
 * attached. Loader counts in real heaps are bounded (tens-to-low-hundreds
 * even on container apps) so the per-loader {@link HeapView#readInstanceFields}
 * cost is acceptable.
 *
 * <p>Edges are normalised so that an unresolved parent (null reference, no
 * {@code parent} field on a subclass, or a parent id that wasn't aggregated
 * by {@link ClassLoaderAnalyzer}) becomes {@code parentId = 0} — the
 * synthetic bootstrap loader and the tree's root.
 */
public final class ClassLoaderHierarchyAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(ClassLoaderHierarchyAnalyzer.class);

    private static final String PARENT_FIELD_NAME = "parent";

    private static final long BOOTSTRAP_LOADER_ID = 0L;

    private ClassLoaderHierarchyAnalyzer() {
    }

    /**
     * @param view       open heap view with the .hprof attached
     * @param loaderIds  the ids returned by {@link ClassLoaderAnalyzer} —
     *                   includes 0 for bootstrap, which is skipped
     * @return one edge per non-bootstrap loader; ordering is unspecified
     */
    public static List<ClassLoaderHierarchyEdge> analyze(
            HeapView view, Collection<Long> loaderIds) throws SQLException {

        java.util.Set<Long> knownLoaders = new java.util.HashSet<>(loaderIds);

        List<ClassLoaderHierarchyEdge> edges = new ArrayList<>();
        for (Long loaderId : loaderIds) {
            if (loaderId == null || loaderId == BOOTSTRAP_LOADER_ID) {
                continue;
            }
            long parentId = readParentLoaderId(view, loaderId);
            if (parentId != BOOTSTRAP_LOADER_ID && !knownLoaders.contains(parentId)) {
                // Defensive: parent reference points at an instance we didn't
                // aggregate as a loader (heap corruption or a non-standard
                // ClassLoader subtype). Collapse to bootstrap so the tree
                // stays connected.
                parentId = BOOTSTRAP_LOADER_ID;
            }
            edges.add(new ClassLoaderHierarchyEdge(loaderId, parentId));
        }
        return edges;
    }

    private static long readParentLoaderId(HeapView view, long loaderInstanceId) {
        try {
            List<InstanceFieldValue> fields = view.readInstanceFields(loaderInstanceId);
            for (InstanceFieldValue field : fields) {
                if (PARENT_FIELD_NAME.equals(field.name()) && field.value() instanceof Long ref) {
                    return ref;
                }
            }
        } catch (SQLException e) {
            LOG.debug("Failed to read parent loader: loader_id={} reason={}", loaderInstanceId, e.getMessage());
        } catch (RuntimeException e) {
            LOG.debug("Skipping loader without readable parent field: loader_id={} reason={}",
                    loaderInstanceId, e.getMessage());
        }
        return BOOTSTRAP_LOADER_ID;
    }
}
