/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.heapdump.analyzer;

import org.netbeans.lib.profiler.heap.GCRoot;
import org.netbeans.lib.profiler.heap.Heap;
import pbouda.jeffrey.profile.heapdump.model.GCRootSummary;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Analyzes GC roots in a heap dump.
 */
public class GCRootAnalyzer {

    /**
     * Analyze GC roots and return summary grouped by type.
     *
     * @param heap the loaded heap dump
     * @return summary of GC roots by type
     */
    @SuppressWarnings("unchecked")
    public GCRootSummary analyze(Heap heap) {
        Map<String, Long> rootsByType = new HashMap<>();

        Collection<GCRoot> gcRoots = (Collection<GCRoot>) heap.getGCRoots();
        for (GCRoot root : gcRoots) {
            String kind = root.getKind();
            rootsByType.merge(kind, 1L, Long::sum);
        }

        long totalRoots = rootsByType.values().stream().mapToLong(Long::longValue).sum();

        return new GCRootSummary(rootsByType, totalRoots);
    }
}
