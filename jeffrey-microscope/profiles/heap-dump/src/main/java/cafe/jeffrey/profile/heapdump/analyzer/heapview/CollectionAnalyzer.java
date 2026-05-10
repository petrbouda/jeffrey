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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import cafe.jeffrey.profile.heapdump.model.ClassWasteEntry;
import cafe.jeffrey.profile.heapdump.model.CollectionAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.CollectionStats;
import cafe.jeffrey.profile.heapdump.model.FillDistribution;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldValue;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.CollectionAnalyzer}.
 *
 * Decodes a curated set of JDK collection types' internal state to estimate
 * fill ratios and wasted capacity. Supported in this PR:
 * <ul>
 *   <li>{@code java.util.ArrayList}: {@code size} (int) and {@code elementData}
 *       (Object[] — capacity = array.length)</li>
 *   <li>{@code java.util.Vector}: same shape as ArrayList</li>
 *   <li>{@code java.util.HashMap}: {@code size} (int) and {@code table}
 *       (Node[] — capacity = array.length)</li>
 *   <li>{@code java.util.LinkedHashMap}, {@code java.util.HashSet},
 *       {@code java.util.LinkedHashSet}: delegated to HashMap shape</li>
 * </ul>
 *
 * Other collection types (LinkedList, TreeMap, ConcurrentHashMap, ArrayDeque,
 * PriorityQueue, CopyOnWriteArrayList) are <strong>not</strong> decoded yet —
 * they need per-class layout knowledge that is straightforward to add but is
 * out of scope here. Their instance counts surface in
 * {@link ClassHistogramAnalyzer} so users still see them.
 */
public final class CollectionAnalyzer {

    private static final List<Shape> SHAPES = List.of(
            new Shape("java.util.ArrayList", "elementData"),
            new Shape("java.util.Vector", "elementData"),
            new Shape("java.util.HashMap", "table"),
            new Shape("java.util.LinkedHashMap", "table"),
            new Shape("java.util.HashSet", "map"),
            new Shape("java.util.LinkedHashSet", "map"));

    private CollectionAnalyzer() {
    }

    public static CollectionAnalysisReport analyze(HeapView view) throws SQLException {
        Map<String, Acc> byType = new LinkedHashMap<>();
        Map<String, Long> wastedByClass = new HashMap<>();
        long totalEmpty = 0;
        long totalWasted = 0;
        long totalCollections = 0;
        FillBuckets overallBuckets = new FillBuckets();

        for (Shape shape : SHAPES) {
            for (JavaClassRow cls : view.findClassesByName(shape.className)) {
                Acc acc = byType.computeIfAbsent(shape.className, k -> new Acc());
                try (Stream<InstanceRow> stream = view.instances(cls.classId())) {
                    for (InstanceRow inst : (Iterable<InstanceRow>) stream::iterator) {
                        Decoded d = decode(view, inst, shape);
                        if (d == null) {
                            continue;
                        }
                        acc.totalCount++;
                        totalCollections++;
                        if (d.size == 0) {
                            acc.emptyCount++;
                            totalEmpty++;
                        }
                        long wasted = wastedBytes(d.size, d.capacity, shape);
                        acc.totalWasted += wasted;
                        totalWasted += wasted;
                        wastedByClass.merge(shape.className, wasted, Long::sum);

                        double fillRatio = d.capacity == 0 ? 0.0 : (double) d.size / d.capacity;
                        acc.fillRatioSum += fillRatio;
                        acc.buckets.add(fillRatio, d.size);
                        overallBuckets.add(fillRatio, d.size);
                    }
                }
            }
        }

        List<CollectionStats> stats = new ArrayList<>();
        for (Map.Entry<String, Acc> e : byType.entrySet()) {
            Acc a = e.getValue();
            double avg = a.totalCount == 0 ? 0.0 : a.fillRatioSum / a.totalCount;
            stats.add(new CollectionStats(
                    e.getKey(), a.totalCount, a.emptyCount, a.totalWasted, avg, a.buckets.toFillDistribution()));
        }

        List<ClassWasteEntry> wasteByClass = wastedByClass.entrySet().stream()
                .map(e -> new ClassWasteEntry(e.getKey(), 0, 0, e.getValue(), Map.<String, Integer>of()))
                .sorted(Comparator.comparingLong(ClassWasteEntry::wastedBytes).reversed())
                .toList();

        return new CollectionAnalysisReport(
                (int) Math.min(totalCollections, Integer.MAX_VALUE),
                (int) Math.min(totalEmpty, Integer.MAX_VALUE),
                totalWasted,
                overallBuckets.toFillDistribution(),
                List.copyOf(stats),
                wasteByClass);
    }

    /**
     * Decodes (size, capacity) for the given collection instance.
     * Returns null when the instance can't be inspected (no .hprof, missing fields).
     */
    private static Decoded decode(HeapView view, InstanceRow inst, Shape shape) throws SQLException {
        List<InstanceFieldValue> fields;
        try {
            fields = view.readInstanceFields(inst.instanceId());
        } catch (IllegalStateException noHprof) {
            return null;
        }
        Integer size = null;
        Long arrayRef = null;
        for (InstanceFieldValue f : fields) {
            if ("size".equals(f.name()) && f.value() instanceof Integer i) {
                size = i;
            } else if (shape.arrayFieldName.equals(f.name()) && f.value() instanceof Long ref) {
                arrayRef = ref;
            }
        }
        if (size == null || arrayRef == null || arrayRef == 0L) {
            return null;
        }
        InstanceRow array = view.findInstanceById(arrayRef).orElse(null);
        if (array == null || array.kind() != InstanceRow.Kind.OBJECT_ARRAY || array.arrayLength() == null) {
            // HashSet's "map" field is a HashMap, not an array — skip nested decode for now.
            return null;
        }
        return new Decoded(size, array.arrayLength());
    }

    private static long wastedBytes(int size, int capacity, Shape shape) {
        if (capacity <= size) {
            return 0L;
        }
        // Cost of unused slots is roughly idSize per slot for object-array-backed
        // collections. Assume idSize=8 (most modern JVMs); minor overcount on idSize=4.
        return (long) (capacity - size) * 8L;
    }

    private record Shape(String className, String arrayFieldName) {
    }

    private record Decoded(int size, int capacity) {
    }

    private static final class Acc {
        int totalCount;
        int emptyCount;
        long totalWasted;
        double fillRatioSum;
        final FillBuckets buckets = new FillBuckets();
    }

    /** Five-bucket fill-ratio histogram. */
    private static final class FillBuckets {
        int empty, low, medium, high, full;

        void add(double fillRatio, int size) {
            if (size == 0) {
                empty++;
            } else if (fillRatio < 0.25) {
                low++;
            } else if (fillRatio < 0.50) {
                medium++;
            } else if (fillRatio < 0.90) {
                high++;
            } else {
                full++;
            }
        }

        FillDistribution toFillDistribution() {
            return new FillDistribution(empty, low, medium, high, full);
        }
    }
}
