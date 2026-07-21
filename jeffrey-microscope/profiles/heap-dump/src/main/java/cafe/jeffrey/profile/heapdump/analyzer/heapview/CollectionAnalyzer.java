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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import cafe.jeffrey.profile.heapdump.model.ClassWasteEntry;
import cafe.jeffrey.profile.heapdump.model.CollectionAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.CollectionStats;
import cafe.jeffrey.profile.heapdump.model.FillDistribution;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.InstanceFieldDescriptor;
import cafe.jeffrey.profile.heapdump.view.JavaClassRow;

/**
 * Bulk-SQL collection analysis.
 *
 * <p>For each supported JDK collection shape, runs a single JOIN that pairs
 * every instance with its backing-array row in one result set. Inline count
 * fields — which have no representation in the index DB — are read directly
 * from the {@code .hprof} via {@link HeapView#readInt(long)} /
 * {@link HeapView#readLong(long)} at per-class precomputed byte offsets.
 *
 * <p>Three families of shapes are decoded:
 * <ul>
 *   <li><b>Array-backed</b> (shared catalog in {@link CollectionShapes}):
 *       ArrayList, Vector, HashMap, LinkedHashMap, Hashtable, PriorityQueue,
 *       ArrayDeque (head/tail arithmetic), ConcurrentHashMap
 *       ({@code baseCount}; approximate under concurrent update) and
 *       CopyOnWriteArrayList (always full). These contribute counts, empties,
 *       wasted bytes and fill-ratio distributions.</li>
 *   <li><b>Size-only</b>: TreeMap, LinkedList, ConcurrentLinkedQueue-style
 *       node chains have no backing array, so they contribute counts and
 *       empties but no capacity-derived metrics.</li>
 *   <li><b>Via-map sets</b>: HashSet, LinkedHashSet and TreeSet delegate their
 *       storage to an internal map; the set's size is read from the referenced
 *       map instance (layout resolved from the map's actual class), again
 *       counts/empties only.</li>
 * </ul>
 */
public final class CollectionAnalyzer {

    private static final List<SizeOnlyShape> SIZE_ONLY_SHAPES = List.of(
            new SizeOnlyShape("java.util.TreeMap", "size"),
            new SizeOnlyShape("java.util.LinkedList", "size"));

    private static final List<ViaMapShape> VIA_MAP_SHAPES = List.of(
            new ViaMapShape("java.util.HashSet", "map"),
            new ViaMapShape("java.util.LinkedHashSet", "map"),
            new ViaMapShape("java.util.TreeSet", "m"));

    private static final int INT_FIELD_BYTES = 4;

    /** Marker for samples with no meaningful capacity (size-only / via-map shapes). */
    private static final int CAPACITY_UNKNOWN = -1;

    /**
     * Bulk join: every instance of a collection class paired with its
     * backing array's row in a single result set.
     *
     * <p>Parameters: {@code 1 = field_id} (chain-global index for the
     * backing-array field), {@code 2 = collection class_id}.
     */
    private static final String COLLECTIONS_WITH_ARRAY_SQL = """
            SELECT
                s.instance_id    AS coll_id,
                s.file_offset    AS coll_offset,
                arr.instance_id  AS arr_id,
                arr.array_length AS arr_length,
                arr.record_kind  AS arr_kind
            FROM instance s
            LEFT JOIN outbound_ref ref
                ON ref.source_id = s.instance_id AND ref.field_id = ?
            LEFT JOIN instance arr
                ON arr.instance_id = ref.target_id
            WHERE s.class_id = ?
            """;

    /** All instances of a class with their field-block offsets. Parameter: {@code 1 = class_id}. */
    private static final String INSTANCES_SQL = """
            SELECT s.instance_id, s.file_offset
            FROM instance s
            WHERE s.class_id = ?
            """;

    /**
     * Every instance of a set class paired with its backing map instance.
     * Parameters: {@code 1 = field_id} of the map field, {@code 2 = set class_id}.
     */
    private static final String SETS_WITH_MAP_SQL = """
            SELECT
                s.instance_id AS set_id,
                m.class_id    AS map_class_id,
                m.file_offset AS map_offset
            FROM instance s
            LEFT JOIN outbound_ref ref
                ON ref.source_id = s.instance_id AND ref.field_id = ?
            LEFT JOIN instance m
                ON m.instance_id = ref.target_id
            WHERE s.class_id = ?
            """;

    private CollectionAnalyzer() {
    }

    public static CollectionAnalysisReport analyze(HeapView view) throws SQLException {
        int idSize = view.metadata().idSize();

        Totals totals = new Totals(idSize);
        analyzeArrayBackedShapes(view, idSize, totals);
        analyzeSizeOnlyShapes(view, idSize, totals);
        analyzeViaMapShapes(view, idSize, totals);
        return totals.toReport();
    }

    private static void analyzeArrayBackedShapes(HeapView view, int idSize, Totals totals)
            throws SQLException {
        long headerBytes = CollectionShapes.instanceHeaderBytes(idSize);
        for (CollectionShapes.ArrayShape shape : CollectionShapes.arrayBackedShapes()) {
            for (JavaClassRow cls : view.findClassesByName(shape.className())) {
                CollectionShapes.ArrayLayout layout =
                        CollectionShapes.computeArrayLayout(view, cls.classId(), shape, idSize);
                if (layout == null) {
                    continue;
                }
                try (PreparedStatement stmt = view.databaseClient().connection()
                        .prepareStatement(COLLECTIONS_WITH_ARRAY_SQL)) {
                    stmt.setInt(1, layout.arrayFieldId());
                    stmt.setLong(2, cls.classId());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            long collFileOffset = rs.getLong(2);
                            long arrId = rs.getLong(3);
                            if (rs.wasNull() || arrId == 0L) {
                                continue;
                            }
                            int arrKind = rs.getInt(5);
                            if (rs.wasNull() || arrKind != CollectionShapes.RECORD_KIND_OBJECT_ARRAY) {
                                continue;
                            }
                            int arrLength = rs.getInt(4);
                            if (rs.wasNull()) {
                                continue;
                            }
                            int size;
                            try {
                                size = layout.sizeReader()
                                        .read(view, collFileOffset + headerBytes, arrLength);
                            } catch (RuntimeException ignored) {
                                continue;
                            }
                            totals.addSample(shape.className(), size, arrLength);
                        }
                    }
                }
            }
        }
    }

    private static void analyzeSizeOnlyShapes(HeapView view, int idSize, Totals totals)
            throws SQLException {
        long headerBytes = CollectionShapes.instanceHeaderBytes(idSize);
        for (SizeOnlyShape shape : SIZE_ONLY_SHAPES) {
            for (JavaClassRow cls : view.findClassesByName(shape.className())) {
                List<InstanceFieldDescriptor> chain = view.instanceFieldsWithChain(cls.classId());
                int sizeOffset = CollectionShapes.inlineFieldOffset(
                        chain, shape.sizeFieldName(), INT_FIELD_BYTES, idSize);
                if (sizeOffset < 0) {
                    continue;
                }
                try (PreparedStatement stmt =
                             view.databaseClient().connection().prepareStatement(INSTANCES_SQL)) {
                    stmt.setLong(1, cls.classId());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            long fileOffset = rs.getLong(2);
                            int size;
                            try {
                                size = view.readInt(fileOffset + headerBytes + sizeOffset);
                            } catch (RuntimeException ignored) {
                                continue;
                            }
                            totals.addSample(shape.className(), size, CAPACITY_UNKNOWN);
                        }
                    }
                }
            }
        }
    }

    private static void analyzeViaMapShapes(HeapView view, int idSize, Totals totals)
            throws SQLException {
        long headerBytes = CollectionShapes.instanceHeaderBytes(idSize);
        // The referenced map's `size` offset depends on the map's concrete class;
        // resolved lazily and cached across all set shapes (-1 = size field absent).
        Map<Long, Integer> sizeOffsetByMapClassId = new HashMap<>();

        for (ViaMapShape shape : VIA_MAP_SHAPES) {
            for (JavaClassRow cls : view.findClassesByName(shape.className())) {
                List<InstanceFieldDescriptor> chain = view.instanceFieldsWithChain(cls.classId());
                int mapFieldId = objectFieldIndex(chain, shape.mapFieldName());
                if (mapFieldId < 0) {
                    continue;
                }
                try (PreparedStatement stmt =
                             view.databaseClient().connection().prepareStatement(SETS_WITH_MAP_SQL)) {
                    stmt.setInt(1, mapFieldId);
                    stmt.setLong(2, cls.classId());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            long mapClassId = rs.getLong(2);
                            if (rs.wasNull() || mapClassId == 0L) {
                                continue;
                            }
                            long mapOffset = rs.getLong(3);
                            int sizeOffset = mapSizeOffset(
                                    view, sizeOffsetByMapClassId, mapClassId, idSize);
                            if (sizeOffset < 0) {
                                continue;
                            }
                            int size;
                            try {
                                size = view.readInt(mapOffset + headerBytes + sizeOffset);
                            } catch (RuntimeException ignored) {
                                continue;
                            }
                            totals.addSample(shape.className(), size, CAPACITY_UNKNOWN);
                        }
                    }
                }
            }
        }
    }

    private static int mapSizeOffset(
            HeapView view, Map<Long, Integer> cache, long mapClassId, int idSize)
            throws SQLException {
        Integer cached = cache.get(mapClassId);
        if (cached != null) {
            return cached;
        }
        List<InstanceFieldDescriptor> mapChain = view.instanceFieldsWithChain(mapClassId);
        int offset = CollectionShapes.inlineFieldOffset(mapChain, "size", INT_FIELD_BYTES, idSize);
        cache.put(mapClassId, offset);
        return offset;
    }

    private static int objectFieldIndex(List<InstanceFieldDescriptor> chain, String fieldName) {
        for (int i = 0; i < chain.size(); i++) {
            InstanceFieldDescriptor f = chain.get(i);
            if (fieldName.equals(f.name()) && f.basicType() == CollectionShapes.BASIC_TYPE_OBJECT) {
                return i;
            }
        }
        return -1;
    }

    private record SizeOnlyShape(String className, String sizeFieldName) {
    }

    private record ViaMapShape(String className, String mapFieldName) {
    }

    /** Report accumulator shared by all shape families. */
    private static final class Totals {

        private final int idSize;
        private final Map<String, Acc> byType = new LinkedHashMap<>();
        private final Map<String, Long> wastedByClass = new HashMap<>();
        private final FillBuckets overallBuckets = new FillBuckets();
        private long totalCollections;
        private long totalEmpty;
        private long totalWasted;

        private Totals(int idSize) {
            this.idSize = idSize;
        }

        void addSample(String typeName, int size, int capacity) {
            Acc acc = byType.computeIfAbsent(typeName, k -> new Acc());
            acc.totalCount++;
            totalCollections++;
            if (size == 0) {
                acc.emptyCount++;
                totalEmpty++;
            }
            if (capacity == CAPACITY_UNKNOWN) {
                return;
            }
            long wasted = wastedBytes(size, capacity);
            acc.totalWasted += wasted;
            totalWasted += wasted;
            wastedByClass.merge(typeName, wasted, Long::sum);

            double fillRatio = capacity == 0 ? 0.0 : (double) size / capacity;
            acc.fillSamples++;
            acc.fillRatioSum += fillRatio;
            acc.buckets.add(fillRatio, size);
            overallBuckets.add(fillRatio, size);
        }

        private long wastedBytes(int size, int capacity) {
            if (capacity <= size) {
                return 0L;
            }
            // Cost of unused slots is one reference per slot.
            return (long) (capacity - size) * idSize;
        }

        CollectionAnalysisReport toReport() {
            List<CollectionStats> stats = new ArrayList<>();
            for (Map.Entry<String, Acc> e : byType.entrySet()) {
                Acc a = e.getValue();
                double avg = a.fillSamples == 0 ? 0.0 : a.fillRatioSum / a.fillSamples;
                stats.add(new CollectionStats(
                        e.getKey(), a.totalCount, a.emptyCount, a.totalWasted, avg,
                        a.buckets.toFillDistribution()));
            }

            List<ClassWasteEntry> wasteByClass = wastedByClass.entrySet().stream()
                    .map(e -> new ClassWasteEntry(e.getKey(), 0, 0, e.getValue(),
                            Map.<String, Integer>of()))
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
    }

    private static final class Acc {
        int totalCount;
        int emptyCount;
        long totalWasted;
        int fillSamples;
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
