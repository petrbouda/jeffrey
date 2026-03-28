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

package pbouda.jeffrey.profile.heapdump.analyzer;

import org.netbeans.lib.profiler.heap.Heap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflective accessor for the NetBeans Profiler's internal dominator tree.
 * <p>
 * The NetBeans library computes a true dominator tree during retained size calculation
 * (triggered by {@code Instance.getRetainedSize()}), but the result is stored in
 * package-private classes ({@code HprofHeap.domTree -> DominatorTree.map -> LongHashMap.table}).
 * <p>
 * This class uses reflection to access the internal {@code long[]} table of the
 * {@code LongHashMap} that stores {@code instanceId -> idomId} mappings for
 * multi-parent instances. The table layout is: {@code table[2i]} = key (instanceId),
 * {@code table[2i+1]} = value (idomId), key == 0 means empty slot.
 * <p>
 * If reflection fails (API changes, module restrictions), {@link #isAvailable()} returns
 * {@code false} and callers should fall back to field-reference-based children lookup.
 */
public class DominatorTreeReflection {

    private static final Logger LOG = LoggerFactory.getLogger(DominatorTreeReflection.class);

    private final long[] table;
    private final boolean available;

    public DominatorTreeReflection(Heap heap) {
        long[] extractedTable = null;
        try {
            Field domTreeField = heap.getClass().getDeclaredField("domTree");
            domTreeField.setAccessible(true);
            Object domTree = domTreeField.get(heap);
            if (domTree != null) {
                Field mapField = domTree.getClass().getDeclaredField("map");
                mapField.setAccessible(true);
                Object map = mapField.get(domTree);

                Field tableField = map.getClass().getDeclaredField("table");
                tableField.setAccessible(true);
                extractedTable = (long[]) tableField.get(map);
            } else {
                LOG.warn("DominatorTree is null, retained size may not be computed yet");
            }
        } catch (Exception e) {
            LOG.warn("Failed to access DominatorTree internals via reflection, "
                    + "dominator tree children will fall back to field references only: message={}", e.getMessage());
        }
        this.table = extractedTable;
        this.available = extractedTable != null;
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Finds all multi-parent instance IDs whose immediate dominator is {@code parentId}.
     * Performs a linear scan of the internal hash table.
     *
     * @param parentId the object ID of the parent node
     * @return list of instance IDs dominated by the parent, empty if reflection is unavailable
     */
    public List<Long> findDominatedChildren(long parentId) {
        if (!available) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < table.length; i += 2) {
            if (table[i] != 0 && table[i + 1] == parentId) {
                result.add(table[i]);
            }
        }
        return result;
    }

    /**
     * Gets the immediate dominator ID for a multi-parent instance.
     * Uses the same hash function as the NetBeans {@code LongHashMap}.
     *
     * @param instanceId the object ID to look up
     * @return the immediate dominator ID, or {@code -1} if the instance is not in the map
     *         (meaning it's a single-parent instance whose idom is its sole referrer)
     */
    public long getIdom(long instanceId) {
        if (!available) {
            return -1;
        }
        int len = table.length;
        int i = hash(instanceId, len);
        while (true) {
            long item = table[i];
            if (item == instanceId) {
                return table[i + 1];
            }
            if (item == 0) {
                return -1;
            }
            i = nextKeyIndex(i, len);
        }
    }

    // Hash function copied from org.netbeans.lib.profiler.heap.LongHashMap
    private static int hash(long x, int length) {
        int h = (int) (x ^ (x >>> 32));
        h ^= (h >>> 20) ^ (h >>> 12);
        h ^= (h >>> 7) ^ (h >>> 4);
        return (h) & (length - 2);
    }

    // Index advancement copied from org.netbeans.lib.profiler.heap.LongHashMap
    private static int nextKeyIndex(int i, int len) {
        return (i + 2 < len ? i + 2 : 0);
    }
}
