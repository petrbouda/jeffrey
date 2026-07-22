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
import java.util.List;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.HprofTypeSize;
import cafe.jeffrey.profile.heapdump.view.InstanceFieldDescriptor;

/**
 * Shared catalog of JDK collection shapes whose element storage is a single
 * backing {@code Object[]} reachable through {@code outbound_ref}, used by both
 * {@link CollectionAnalyzer} and {@link BiggestCollectionsAnalyzer}.
 *
 * <p>Each shape names its backing-array field and carries a {@link SizeReaderSpec}
 * describing how the live element count is stored:
 * <ul>
 *   <li>{@link IntFieldSpec} — a plain inline {@code int} ({@code ArrayList.size},
 *       {@code Vector.elementCount}, {@code Hashtable.count}, ...)</li>
 *   <li>{@link HeadTailSpec} — {@code ArrayDeque}'s circular-buffer indices;
 *       size is {@code (tail - head) mod capacity}</li>
 *   <li>{@link LongFieldSpec} — {@code ConcurrentHashMap.baseCount}; an
 *       approximation that ignores {@code counterCells}, so counts taken during
 *       concurrent updates may slightly undercount</li>
 * </ul>
 *
 * <p>Layouts are resolved per concrete class via {@link #computeArrayLayout};
 * the returned {@link SizeReader} reads the count straight from the mapped
 * {@code .hprof} given the start of the instance's field block.
 */
final class CollectionShapes {

    /** HPROF basic-type byte for OBJECT references. */
    static final int BASIC_TYPE_OBJECT = 2;

    /** record_kind value for OBJECT_ARRAY_DUMP rows in the {@code instance} table. */
    static final byte RECORD_KIND_OBJECT_ARRAY = 1;

    private static final int INT_FIELD_BYTES = 4;

    private static final int LONG_FIELD_BYTES = 8;

    private static final List<ArrayShape> ARRAY_BACKED = List.of(
            new ArrayShape("java.util.ArrayList", "elementData", new IntFieldSpec("size")),
            new ArrayShape("java.util.Vector", "elementData", new IntFieldSpec("elementCount")),
            new ArrayShape("java.util.HashMap", "table", new IntFieldSpec("size")),
            new ArrayShape("java.util.LinkedHashMap", "table", new IntFieldSpec("size")),
            new ArrayShape("java.util.Hashtable", "table", new IntFieldSpec("count")),
            new ArrayShape("java.util.PriorityQueue", "queue", new IntFieldSpec("size")),
            new ArrayShape("java.util.ArrayDeque", "elements", new HeadTailSpec("head", "tail")),
            new ArrayShape("java.util.concurrent.ConcurrentHashMap", "table",
                    new LongFieldSpec("baseCount")),
            new ArrayShape("java.util.concurrent.CopyOnWriteArrayList", "array",
                    new ArrayLengthSpec()));

    private CollectionShapes() {
    }

    static List<ArrayShape> arrayBackedShapes() {
        return ARRAY_BACKED;
    }

    /** One array-backed collection shape: class, backing-array field, size storage. */
    record ArrayShape(String className, String arrayFieldName, SizeReaderSpec sizeSpec) {
    }

    /**
     * Per-class resolution of an {@link ArrayShape}: the chain-global field index
     * of the backing array (joins to {@code outbound_ref.field_id}) plus the
     * reader for the element count. {@code null} when the class layout does not
     * match the shape (e.g. an unusual JDK version).
     */
    record ArrayLayout(int arrayFieldId, SizeReader sizeReader) {
    }

    static ArrayLayout computeArrayLayout(HeapView view, long classId, ArrayShape shape, int idSize)
            throws SQLException {
        List<InstanceFieldDescriptor> chain = view.instanceFieldsWithChain(classId);
        int arrayFieldId = -1;
        long cursor = 0;
        for (int i = 0; i < chain.size(); i++) {
            InstanceFieldDescriptor f = chain.get(i);
            if (shape.arrayFieldName().equals(f.name()) && f.basicType() == BASIC_TYPE_OBJECT) {
                arrayFieldId = i;
            }
            cursor += HprofTypeSize.sizeOf(f.basicType(), idSize);
        }
        if (arrayFieldId < 0) {
            return null;
        }
        SizeReader reader = shape.sizeSpec().resolve(chain, idSize);
        if (reader == null) {
            return null;
        }
        return new ArrayLayout(arrayFieldId, reader);
    }

    /**
     * Finds the byte offset of the inline field named {@code fieldName} with the
     * given byte size within a class's field block; {@code -1} when absent.
     */
    static int inlineFieldOffset(
            List<InstanceFieldDescriptor> chain, String fieldName, int requiredBytes, int idSize) {
        long cursor = 0;
        for (InstanceFieldDescriptor f : chain) {
            int fieldSize = HprofTypeSize.sizeOf(f.basicType(), idSize);
            if (fieldName.equals(f.name()) && fieldSize == requiredBytes) {
                return (int) cursor;
            }
            cursor += fieldSize;
        }
        return -1;
    }

    /** Byte offset of the first field within an INSTANCE_DUMP record body. */
    static long instanceHeaderBytes(int idSize) {
        return 2L * idSize + 8L;
    }

    // ---- Size storage specs (resolved per class) --------------------------

    sealed interface SizeReaderSpec permits IntFieldSpec, LongFieldSpec, HeadTailSpec, ArrayLengthSpec {

        /** Resolves against a concrete class's field chain; {@code null} if the fields are absent. */
        SizeReader resolve(List<InstanceFieldDescriptor> chain, int idSize);
    }

    record IntFieldSpec(String fieldName) implements SizeReaderSpec {

        @Override
        public SizeReader resolve(List<InstanceFieldDescriptor> chain, int idSize) {
            int offset = inlineFieldOffset(chain, fieldName, INT_FIELD_BYTES, idSize);
            return offset < 0 ? null : new IntFieldReader(offset);
        }
    }

    record LongFieldSpec(String fieldName) implements SizeReaderSpec {

        @Override
        public SizeReader resolve(List<InstanceFieldDescriptor> chain, int idSize) {
            int offset = inlineFieldOffset(chain, fieldName, LONG_FIELD_BYTES, idSize);
            return offset < 0 ? null : new LongFieldReader(offset);
        }
    }

    record HeadTailSpec(String headFieldName, String tailFieldName) implements SizeReaderSpec {

        @Override
        public SizeReader resolve(List<InstanceFieldDescriptor> chain, int idSize) {
            int headOffset = inlineFieldOffset(chain, headFieldName, INT_FIELD_BYTES, idSize);
            int tailOffset = inlineFieldOffset(chain, tailFieldName, INT_FIELD_BYTES, idSize);
            if (headOffset < 0 || tailOffset < 0) {
                return null;
            }
            return new HeadTailReader(headOffset, tailOffset);
        }
    }

    /** For collections that are always full (CopyOnWriteArrayList): size == capacity. */
    record ArrayLengthSpec() implements SizeReaderSpec {

        @Override
        public SizeReader resolve(List<InstanceFieldDescriptor> chain, int idSize) {
            return new ArrayLengthReader();
        }
    }

    // ---- Size readers (per row) -------------------------------------------

    sealed interface SizeReader permits IntFieldReader, LongFieldReader, HeadTailReader, ArrayLengthReader {

        /**
         * Reads the element count of one collection instance.
         *
         * @param view            the heap view (mmap-attached)
         * @param fieldBlockStart absolute file offset of the instance's field block
         * @param capacity        backing-array length of this instance
         */
        int read(HeapView view, long fieldBlockStart, int capacity) throws SQLException;
    }

    record IntFieldReader(int byteOffset) implements SizeReader {

        @Override
        public int read(HeapView view, long fieldBlockStart, int capacity) throws SQLException {
            return view.readInt(fieldBlockStart + byteOffset);
        }
    }

    record LongFieldReader(int byteOffset) implements SizeReader {

        @Override
        public int read(HeapView view, long fieldBlockStart, int capacity) throws SQLException {
            long value = view.readLong(fieldBlockStart + byteOffset);
            if (value < 0L) {
                return 0;
            }
            return (int) Math.min(value, Integer.MAX_VALUE);
        }
    }

    record HeadTailReader(int headByteOffset, int tailByteOffset) implements SizeReader {

        @Override
        public int read(HeapView view, long fieldBlockStart, int capacity) throws SQLException {
            if (capacity == 0) {
                return 0;
            }
            int head = view.readInt(fieldBlockStart + headByteOffset);
            int tail = view.readInt(fieldBlockStart + tailByteOffset);
            int size = tail - head;
            if (size < 0) {
                size += capacity;
            }
            if (size < 0 || size > capacity) {
                return 0;
            }
            return size;
        }
    }

    record ArrayLengthReader() implements SizeReader {

        @Override
        public int read(HeapView view, long fieldBlockStart, int capacity) {
            return capacity;
        }
    }
}
