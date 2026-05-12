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
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofTypeSize;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldDescriptor;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

/**
 * Bulk-SQL collection analysis.
 *
 * <p>For each supported JDK collection shape, runs a single JOIN that pairs
 * every instance with its backing-array row in one result set. The {@code size}
 * field — an inline {@code int} that has no representation in the index DB —
 * is read directly from the {@code .hprof} via
 * {@link HeapView#readInt(long)} at a per-class precomputed byte offset. This
 * replaces the previous per-instance pattern (two JDBC round-trips per
 * collection: {@code readInstanceFields} + {@code findInstanceById}), which
 * dominated init cost on heaps with hundreds of thousands of collections.
 *
 * <p>Supported shapes:
 * <ul>
 *   <li>{@code java.util.ArrayList}, {@code java.util.Vector}: backing field
 *       {@code elementData} (Object[])</li>
 *   <li>{@code java.util.HashMap}, {@code java.util.LinkedHashMap}: backing
 *       field {@code table} (Node[])</li>
 *   <li>{@code java.util.HashSet}, {@code java.util.LinkedHashSet}: backing
 *       field {@code map} (a HashMap — capacity unfolds only one level so
 *       these contribute totals but not fill ratio)</li>
 * </ul>
 * Other collection types (LinkedList, TreeMap, ConcurrentHashMap, ArrayDeque,
 * PriorityQueue, CopyOnWriteArrayList) are not decoded here.
 */
public final class CollectionAnalyzer {

    private static final List<Shape> SHAPES = List.of(
            new Shape("java.util.ArrayList", "elementData"),
            new Shape("java.util.Vector", "elementData"),
            new Shape("java.util.HashMap", "table"),
            new Shape("java.util.LinkedHashMap", "table"),
            new Shape("java.util.HashSet", "map"),
            new Shape("java.util.LinkedHashSet", "map"));

    /** HPROF basic-type byte for OBJECT references. */
    private static final int BASIC_TYPE_OBJECT = 2;

    /** record_kind value for OBJECT_ARRAY_DUMP rows in the {@code instance} table. */
    private static final byte RECORD_KIND_OBJECT_ARRAY = 1;

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

    private CollectionAnalyzer() {
    }

    public static CollectionAnalysisReport analyze(HeapView view) throws SQLException {
        int idSize = view.metadata().idSize();
        long instanceHeaderBytes = 2L * idSize + 8L;

        Map<String, Acc> byType = new LinkedHashMap<>();
        Map<String, Long> wastedByClass = new HashMap<>();
        long totalEmpty = 0;
        long totalWasted = 0;
        long totalCollections = 0;
        FillBuckets overallBuckets = new FillBuckets();

        for (Shape shape : SHAPES) {
            for (JavaClassRow cls : view.findClassesByName(shape.className)) {
                ClassLayout layout = computeLayout(view, cls.classId(), shape, idSize);
                if (layout == null) {
                    continue; // Class has no `size` int or no array field — skip this class.
                }
                Acc acc = byType.computeIfAbsent(shape.className, k -> new Acc());

                try (PreparedStatement stmt =
                             view.connection().prepareStatement(COLLECTIONS_WITH_ARRAY_SQL)) {
                    stmt.setInt(1, layout.arrayFieldId);
                    stmt.setLong(2, cls.classId());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            long collFileOffset = rs.getLong(2);
                            long arrId = rs.getLong(3);
                            if (rs.wasNull() || arrId == 0L) {
                                continue;
                            }
                            int arrKind = rs.getInt(5);
                            if (rs.wasNull() || arrKind != RECORD_KIND_OBJECT_ARRAY) {
                                // HashSet's `map` points at a HashMap, not an array — skip.
                                continue;
                            }
                            int arrLength = rs.getInt(4);
                            if (rs.wasNull()) {
                                continue;
                            }

                            // Inline int read: jump to the precomputed offset of `size`
                            // inside this instance's field block and pull 4 big-endian bytes.
                            long sizeFieldOffset = collFileOffset + instanceHeaderBytes
                                    + layout.sizeFieldByteOffset;
                            int size;
                            try {
                                size = view.readInt(sizeFieldOffset);
                            } catch (RuntimeException ignored) {
                                continue;
                            }

                            acc.totalCount++;
                            totalCollections++;
                            if (size == 0) {
                                acc.emptyCount++;
                                totalEmpty++;
                            }
                            long wasted = wastedBytes(size, arrLength);
                            acc.totalWasted += wasted;
                            totalWasted += wasted;
                            wastedByClass.merge(shape.className, wasted, Long::sum);

                            double fillRatio = arrLength == 0 ? 0.0 : (double) size / arrLength;
                            acc.fillRatioSum += fillRatio;
                            acc.buckets.add(fillRatio, size);
                            overallBuckets.add(fillRatio, size);
                        }
                    }
                }
            }
        }

        List<CollectionStats> stats = new ArrayList<>();
        for (Map.Entry<String, Acc> e : byType.entrySet()) {
            Acc a = e.getValue();
            double avg = a.totalCount == 0 ? 0.0 : a.fillRatioSum / a.totalCount;
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

    /**
     * Per-class layout: the {@code field_id} (chain-global index) of the
     * backing-array field, plus the byte offset of the {@code size} int
     * field within an instance's field block. Returns {@code null} if either
     * field is missing.
     */
    private static ClassLayout computeLayout(HeapView view, long classId, Shape shape, int idSize)
            throws SQLException {
        List<InstanceFieldDescriptor> chain = view.instanceFieldsWithChain(classId);
        int arrayFieldId = -1;
        int sizeFieldOffset = -1;
        long cursor = 0;
        for (int i = 0; i < chain.size(); i++) {
            InstanceFieldDescriptor f = chain.get(i);
            int fieldSize = HprofTypeSize.sizeOf(f.basicType(), idSize);
            if ("size".equals(f.name()) && fieldSize == 4) {
                sizeFieldOffset = (int) cursor;
            }
            if (shape.arrayFieldName.equals(f.name()) && f.basicType() == BASIC_TYPE_OBJECT) {
                arrayFieldId = i;
            }
            cursor += fieldSize;
        }
        if (arrayFieldId < 0 || sizeFieldOffset < 0) {
            return null;
        }
        return new ClassLayout(arrayFieldId, sizeFieldOffset);
    }

    private static long wastedBytes(int size, int capacity) {
        if (capacity <= size) {
            return 0L;
        }
        // Cost of unused slots is roughly idSize per slot for object-array-backed
        // collections. Assume idSize=8 (most modern JVMs); minor overcount on idSize=4.
        return (long) (capacity - size) * 8L;
    }

    private record Shape(String className, String arrayFieldName) {
    }

    private record ClassLayout(int arrayFieldId, int sizeFieldByteOffset) {
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
