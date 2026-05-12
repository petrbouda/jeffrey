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
package cafe.jeffrey.profile.heapdump.parser;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the shallow-size accounting in {@link HprofIndex}:
 *
 * <ul>
 *   <li>Compressed-oops correction — OOP fields are counted as 4 bytes on a
 *       64-bit dump under the JVM's 32 GiB compressed-pointer limit, not the
 *       on-disk 8 bytes the HPROF spec records.</li>
 *   <li>{@code MinObjAlignment} (8 bytes) rounding for both instances and
 *       arrays.</li>
 *   <li>Super-class chain field counting — corrections apply to every OOP
 *       slot the JVM allocated, including those declared on an ancestor.</li>
 * </ul>
 */
class HprofIndexShallowSizeTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);
    private static final int ID_SIZE = 8;

    @Nested
    class CompressedOops {

        /**
         * Three OBJECT fields:
         * <ul>
         *   <li>On-disk field block: 3 × 8 = 24 bytes</li>
         *   <li>Object header (compressed-oops 64-bit): 16</li>
         *   <li>Raw shallow: 16 + 24 = 40</li>
         *   <li>OOP correction: 3 × (8 − 4) = 12 → 40 − 12 = 28</li>
         *   <li>Aligned to 8: 32</li>
         * </ul>
         */
        @Test
        void instanceShallowDeductsOopOvercountAndAligns(@TempDir Path tmp) throws IOException, SQLException {
            long holderClass = 0xC001L;
            long instId = 0x100L;
            long ref = 0x200L; // ids point at a non-existent object — that's fine for shallow accounting

            Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                    .string(0xA001L, "Holder")
                    .string(0xA002L, "a")
                    .string(0xA003L, "b")
                    .string(0xA004L, "c")
                    .loadClass(1, holderClass, 0, 0xA001L)
                    .heapDumpSegment(seg -> seg
                            .classDumpWithFields(holderClass, 0L, 0L, 3 * ID_SIZE,
                                    new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.OBJECT),
                                    new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.OBJECT),
                                    new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.OBJECT))
                            .instanceDump(instId, holderClass, threeOopFields(ref, ref, ref)))
                    .heapDumpEnd()
                    .writeTo(tmp, "three-oop.hprof");

            assertShallowSize(hprof, instId, 32);
        }

        /**
         * Field spread across a super-class. Superclass has 2 OBJECT fields,
         * subclass adds 1. The HPROF instance record carries all 3 fields, so
         * raw shallow is identical to a flat-three-field class — the test
         * verifies the chain walk reaches the super-class's field count.
         */
        @Test
        void chainOopCountFollowsSuperClass(@TempDir Path tmp) throws IOException, SQLException {
            long superClass = 0xC100L;
            long subClass = 0xC200L;
            long inst = 0x100L;
            long ref = 0x200L;

            Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                    .string(0xA001L, "Parent")
                    .string(0xA002L, "Child")
                    .string(0xA003L, "p1")
                    .string(0xA004L, "p2")
                    .string(0xA005L, "c1")
                    .loadClass(1, superClass, 0, 0xA001L)
                    .loadClass(2, subClass, 0, 0xA002L)
                    .heapDumpSegment(seg -> seg
                            .classDumpWithFields(superClass, 0L, 0L, 2 * ID_SIZE,
                                    new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.OBJECT),
                                    new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.OBJECT))
                            .classDumpWithFields(subClass, superClass, 0L, ID_SIZE,
                                    new SyntheticHprof.SubBuilder.FieldSpec(0xA005L, HprofTag.BasicType.OBJECT))
                            // Subclass instance lays out: c1 (own) then p1, p2 (super) — order is
                            // most-derived-first, but for shallow accounting we only care about
                            // the total byte count (3 × idSize).
                            .instanceDump(inst, subClass, threeOopFields(ref, ref, ref)))
                    .heapDumpEnd()
                    .writeTo(tmp, "chain-oop.hprof");

            assertShallowSize(hprof, inst, 32);
        }

        /**
         * Object arrays use the on-heap OOP width, not idSize.
         * 10 references × 4 bytes (compressed) + array header 16 = 56,
         * already aligned to 8.
         */
        @Test
        void objectArrayUsesOopSize(@TempDir Path tmp) throws IOException, SQLException {
            long elementClass = 0xC001L;
            long arrId = 0x900L;
            long[] slots = new long[10]; // all-zero is fine

            Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                    .string(0xA001L, "Element")
                    .string(0xA002L, "f")
                    .loadClass(1, elementClass, 0, 0xA001L)
                    .heapDumpSegment(seg -> seg
                            .topLevelObjectClassDump(elementClass, 0xA002L)
                            .objectArrayDump(arrId, elementClass, slots))
                    .heapDumpEnd()
                    .writeTo(tmp, "oop-array.hprof");

            assertShallowSize(hprof, arrId, 56);
        }

        /**
         * Primitive arrays are not affected by the OOP correction, but the
         * payload-length is not a multiple of 8, so alignment must kick in.
         * 3 bytes × 1 + array header 16 = 19, aligned to 24.
         */
        @Test
        void primitiveArrayAlignedToObjectBoundary(@TempDir Path tmp) throws IOException, SQLException {
            long arrId = 0x900L;

            Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                    .string(0xA001L, "Holder")
                    .loadClass(1, 0xC001L, 0, 0xA001L)
                    .heapDumpSegment(seg -> seg
                            .primitiveArrayDump(arrId, HprofTag.BasicType.BYTE, new byte[]{1, 2, 3}, 3))
                    .heapDumpEnd()
                    .writeTo(tmp, "prim-array.hprof");

            assertShallowSize(hprof, arrId, 24);
        }
    }

    /**
     * Drives an end-to-end index build for {@code hprof} and asserts the
     * single instance row matches {@code expectedShallow}.
     */
    private static void assertShallowSize(Path hprof, long instanceId, int expectedShallow)
            throws IOException, SQLException {
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            InstanceRow row = view.findInstanceById(instanceId).orElseThrow(() ->
                    new AssertionError("instance not in index: id=" + Long.toHexString(instanceId)));
            assertEquals(expectedShallow, row.shallowSize(),
                    "shallow_size mismatch for instance 0x" + Long.toHexString(instanceId));
        }
    }

    /** Three 8-byte OOP fields, big-endian. */
    private static byte[] threeOopFields(long a, long b, long c) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(buf);
            d.writeLong(a);
            d.writeLong(b);
            d.writeLong(c);
            return buf.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
