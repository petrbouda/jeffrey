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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import cafe.jeffrey.profile.heapdump.model.BiggestCollectionEntry;
import cafe.jeffrey.profile.heapdump.model.BiggestCollectionsReport;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofTypeSize;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldDescriptor;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

/**
 * Bulk-SQL analyzer that finds the biggest individual collections in the heap,
 * ranked separately by element count and by retained size.
 *
 * <p>For each supported JDK collection shape, runs one JOIN that pairs the
 * collection instance with its backing array's row and the dominator-tree's
 * {@code retained_size} row in a single result set. The collection's {@code size}
 * field (an inline {@code int}) is read directly from the .hprof via
 * {@link HeapView#readInt(long)} at a per-class precomputed byte offset — the
 * same pattern used by {@link CollectionAnalyzer}.
 *
 * <p>Two min-heaps of size {@code topN} keep only the best entries during the
 * scan; class names and owner-class names are resolved once per surviving entry
 * after the scan (≤ 2×topN instances), so the per-instance work is just a
 * primary-key index hit on the {@code class} table.
 *
 * <p>Requires the dominator tree to have been built so {@code retained_size} is
 * populated; callers should invoke
 * {@link cafe.jeffrey.profile.heapdump.parser.HeapDumpSession#buildDominatorTreeIfNeeded()}
 * before calling this analyzer.
 *
 * <p>Supported shapes:
 * <ul>
 *   <li>{@code java.util.ArrayList}: {@code size} / {@code elementData}</li>
 *   <li>{@code java.util.Vector}: {@code elementCount} / {@code elementData}</li>
 *   <li>{@code java.util.HashMap}, {@code java.util.LinkedHashMap}: {@code size} / {@code table}</li>
 *   <li>{@code java.util.Hashtable}: {@code count} / {@code table}</li>
 *   <li>{@code java.util.PriorityQueue}: {@code size} / {@code queue}</li>
 * </ul>
 */
public final class BiggestCollectionsAnalyzer {

    private static final int DEFAULT_TOP_N = 50;

    /** HPROF basic-type byte for OBJECT references. */
    private static final int BASIC_TYPE_OBJECT = 2;

    /** record_kind value for OBJECT_ARRAY_DUMP rows in the {@code instance} table. */
    private static final byte RECORD_KIND_OBJECT_ARRAY = 1;

    private static final List<Shape> SHAPES = List.of(
            new Shape("java.util.ArrayList", "size", "elementData"),
            new Shape("java.util.Vector", "elementCount", "elementData"),
            new Shape("java.util.HashMap", "size", "table"),
            new Shape("java.util.LinkedHashMap", "size", "table"),
            new Shape("java.util.Hashtable", "count", "table"),
            new Shape("java.util.PriorityQueue", "size", "queue")
    );

    /**
     * Bulk per-shape join. Parameters: {@code 1 = field_id} of the
     * backing-array field, {@code 2 = collection class_id}.
     */
    private static final String COLLECTIONS_SQL = """
            SELECT
                s.instance_id    AS coll_id,
                s.file_offset    AS coll_offset,
                s.shallow_size   AS coll_shallow,
                arr.instance_id  AS arr_id,
                arr.array_length AS arr_length,
                arr.record_kind  AS arr_kind,
                rs.bytes         AS retained
            FROM instance s
            LEFT JOIN outbound_ref ref
                ON ref.source_id = s.instance_id AND ref.field_id = ?
            LEFT JOIN instance arr
                ON arr.instance_id = ref.target_id
            LEFT JOIN retained_size rs
                ON rs.instance_id = s.instance_id
            WHERE s.class_id = ?
            """;

    /** Resolve the first inbound owner's class name for a given collection id. */
    private static final String OWNER_CLASS_SQL = """
            SELECT c.name
            FROM outbound_ref ref
            JOIN instance owner ON owner.instance_id = ref.source_id
            JOIN class c ON c.class_id = owner.class_id
            WHERE ref.target_id = ?
            ORDER BY ref.source_id
            LIMIT 1
            """;

    private BiggestCollectionsAnalyzer() {
    }

    public static BiggestCollectionsReport analyze(HeapView view) throws SQLException {
        return analyze(view, DEFAULT_TOP_N);
    }

    public static BiggestCollectionsReport analyze(HeapView view, int topN) throws SQLException {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive: topN=" + topN);
        }

        int idSize = view.metadata().idSize();
        long instanceHeaderBytes = 2L * idSize + 8L;

        // class_id -> className for survivor lookups (per-shape, populated lazily).
        Map<Long, String> classNameByClassId = new HashMap<>();

        PriorityQueue<Candidate> byCount = new PriorityQueue<>(
                topN + 1, Comparator.comparingInt(c -> c.elementCount));
        PriorityQueue<Candidate> byRetained = new PriorityQueue<>(
                topN + 1, Comparator.comparingLong(c -> c.retainedSize));
        int totalAnalyzed = 0;

        for (Shape shape : SHAPES) {
            for (JavaClassRow cls : view.findClassesByName(shape.className)) {
                ClassLayout layout = computeLayout(view, cls.classId(), shape, idSize);
                if (layout == null) {
                    continue;
                }
                classNameByClassId.put(cls.classId(), shape.className);

                try (PreparedStatement stmt =
                             view.connection().prepareStatement(COLLECTIONS_SQL)) {
                    stmt.setInt(1, layout.arrayFieldId);
                    stmt.setLong(2, cls.classId());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            long collId = rs.getLong(1);
                            long collOffset = rs.getLong(2);
                            int collShallow = rs.getInt(3);
                            long arrId = rs.getLong(4);
                            if (rs.wasNull() || arrId == 0L) {
                                continue;
                            }
                            int arrLength = rs.getInt(5);
                            if (rs.wasNull()) {
                                continue;
                            }
                            int arrKind = rs.getInt(6);
                            if (rs.wasNull() || arrKind != RECORD_KIND_OBJECT_ARRAY) {
                                continue;
                            }
                            long retained = rs.getLong(7);
                            if (rs.wasNull()) {
                                retained = 0L;
                            }

                            int size;
                            try {
                                size = view.readInt(
                                        collOffset + instanceHeaderBytes + layout.sizeFieldByteOffset);
                            } catch (RuntimeException ignored) {
                                continue;
                            }
                            totalAnalyzed++;

                            Candidate c = new Candidate(
                                    collId, cls.classId(), size, arrLength,
                                    collShallow, retained);
                            offer(byCount, c, topN, cmpByCount);
                            offer(byRetained, c, topN, cmpByRetained);
                        }
                    }
                }
            }
        }

        // Dedup survivors so we don't resolve owner-class twice for a collection
        // that lands in both heaps.
        Set<Long> survivors = new HashSet<>(byCount.size() + byRetained.size());
        for (Candidate c : byCount) {
            survivors.add(c.instanceId);
        }
        for (Candidate c : byRetained) {
            survivors.add(c.instanceId);
        }

        Map<Long, String> ownerClassByCollectionId = new HashMap<>(survivors.size() * 2);
        try (PreparedStatement ownerStmt = view.connection().prepareStatement(OWNER_CLASS_SQL)) {
            for (long collId : survivors) {
                ownerStmt.setLong(1, collId);
                try (ResultSet rs = ownerStmt.executeQuery()) {
                    if (rs.next()) {
                        ownerClassByCollectionId.put(collId, rs.getString(1));
                    }
                }
            }
        }

        List<BiggestCollectionEntry> sortedByCount = drain(byCount, classNameByClassId,
                ownerClassByCollectionId, cmpByCountDesc);
        List<BiggestCollectionEntry> sortedByRetained = drain(byRetained, classNameByClassId,
                ownerClassByCollectionId, cmpByRetainedDesc);

        return new BiggestCollectionsReport(totalAnalyzed, sortedByCount, sortedByRetained);
    }

    private static final Comparator<Candidate> cmpByCount =
            Comparator.comparingInt(c -> c.elementCount);
    private static final Comparator<Candidate> cmpByRetained =
            Comparator.comparingLong(c -> c.retainedSize);
    private static final Comparator<Candidate> cmpByCountDesc = cmpByCount.reversed();
    private static final Comparator<Candidate> cmpByRetainedDesc = cmpByRetained.reversed();

    private static void offer(
            PriorityQueue<Candidate> heap, Candidate c, int topN, Comparator<Candidate> cmp) {
        if (heap.size() < topN) {
            heap.offer(c);
            return;
        }
        Candidate worst = heap.peek();
        if (worst != null && cmp.compare(c, worst) > 0) {
            heap.poll();
            heap.offer(c);
        }
    }

    private static List<BiggestCollectionEntry> drain(
            PriorityQueue<Candidate> heap,
            Map<Long, String> classNameByClassId,
            Map<Long, String> ownerClassByCollectionId,
            Comparator<Candidate> sortDesc) {
        List<Candidate> all = new ArrayList<>(heap);
        all.sort(sortDesc);
        List<BiggestCollectionEntry> out = new ArrayList<>(all.size());
        for (Candidate c : all) {
            double fillRatio = c.capacity == 0 ? 0.0 : (double) c.elementCount / c.capacity;
            String className = classNameByClassId.getOrDefault(c.classId, "<unknown>");
            String ownerClassName = ownerClassByCollectionId.get(c.instanceId);
            out.add(new BiggestCollectionEntry(
                    c.instanceId,
                    className,
                    c.elementCount,
                    c.capacity,
                    fillRatio,
                    c.shallowSize,
                    c.retainedSize,
                    ownerClassName));
        }
        return out;
    }

    private static ClassLayout computeLayout(
            HeapView view, long classId, Shape shape, int idSize) throws SQLException {
        List<InstanceFieldDescriptor> chain = view.instanceFieldsWithChain(classId);
        int arrayFieldId = -1;
        int sizeFieldOffset = -1;
        long cursor = 0;
        for (int i = 0; i < chain.size(); i++) {
            InstanceFieldDescriptor f = chain.get(i);
            int fieldSize = HprofTypeSize.sizeOf(f.basicType(), idSize);
            if (shape.sizeFieldName.equals(f.name()) && fieldSize == 4) {
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

    private record Shape(String className, String sizeFieldName, String arrayFieldName) {
    }

    private record ClassLayout(int arrayFieldId, int sizeFieldByteOffset) {
    }

    private static final class Candidate {
        final long instanceId;
        final long classId;
        final int elementCount;
        final int capacity;
        final long shallowSize;
        final long retainedSize;

        Candidate(long instanceId, long classId, int elementCount, int capacity,
                  long shallowSize, long retainedSize) {
            this.instanceId = instanceId;
            this.classId = classId;
            this.elementCount = elementCount;
            this.capacity = capacity;
            this.shallowSize = shallowSize;
            this.retainedSize = retainedSize;
        }
    }
}
