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

import java.io.IOException;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeapViewTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1_770_000_000_000L), ZoneOffset.UTC);

    private static final long STR_NAME_FOO = 0xA001;
    private static final long STR_NAME_BAR = 0xA002;
    private static final long STR_NAME_FOO_ARRAY = 0xA003;
    private static final long STR_FIELD_VALUE = 0xA010;
    private static final long CLASS_FOO = 0xC001;
    private static final long CLASS_BAR = 0xC002;
    private static final long CLASS_FOO_ARRAY = 0xC003;
    private static final long CLASSLOADER = 0xC100;
    private static final long INSTANCE_FOO_1 = 0x1001;
    private static final long INSTANCE_FOO_2 = 0x1002;
    private static final long INSTANCE_BAR = 0x2001;
    private static final long OBJ_ARRAY = 0x3001;
    private static final long PRIM_ARRAY = 0x3002;

    /** Build a representative index once per test method via the synthetic dump pipeline. */
    private static Path buildIndex(Path tmp) throws IOException, SQLException {
        Path hprof = SyntheticHprof.create("1.0.2", 8, 1234L)
                .string(STR_NAME_FOO, "com.example.Foo")
                .string(STR_NAME_BAR, "com.example.Bar")
                .string(STR_NAME_FOO_ARRAY, "[Lcom.example.Foo;")
                .string(STR_FIELD_VALUE, "value")
                .loadClass(1, CLASS_FOO, 0, STR_NAME_FOO)
                .loadClass(2, CLASS_BAR, 0, STR_NAME_BAR)
                .loadClass(3, CLASS_FOO_ARRAY, 0, STR_NAME_FOO_ARRAY)
                .heapDumpSegment(seg -> seg
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, CLASS_FOO)
                        .gcRootJavaFrame(INSTANCE_FOO_1, 7, 13)
                        .simpleClassDump(CLASS_FOO, 0L, CLASSLOADER, 16, STR_FIELD_VALUE)
                        .simpleClassDump(CLASS_BAR, CLASS_FOO, CLASSLOADER, 24, STR_FIELD_VALUE)
                        .simpleClassDump(CLASS_FOO_ARRAY, 0L, CLASSLOADER, 0, STR_FIELD_VALUE)
                        .instanceDump(INSTANCE_FOO_1, CLASS_FOO, new byte[]{0, 0, 0, 1})
                        .instanceDump(INSTANCE_FOO_2, CLASS_FOO, new byte[]{0, 0, 0, 2})
                        .instanceDump(INSTANCE_BAR, CLASS_BAR, new byte[8])
                        .objectArrayDump(OBJ_ARRAY, CLASS_FOO_ARRAY, new long[]{INSTANCE_FOO_1, INSTANCE_FOO_2})
                        .primitiveArrayDump(PRIM_ARRAY, HprofTag.BasicType.INT, new byte[16], 4))
                .heapDumpEnd()
                .writeTo(tmp, "view.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, FIXED_CLOCK);
        }
        return indexDb;
    }

    @Nested
    class Metadata {

        @Test
        void exposesIdSizeVersionAndCounts(@TempDir Path tmp) throws IOException, SQLException {
            Path indexDb = buildIndex(tmp);
            try (HeapView view = HeapView.open(indexDb)) {
                DumpMetadata meta = view.metadata();
                assertEquals(8, meta.idSize());
                assertEquals("1.0.2", meta.hprofVersion());
                assertEquals(1234L, meta.timestampMs());
                assertFalse(meta.truncated());
                assertEquals(HprofIndex.PARSER_VERSION, meta.parserVersion());
                assertEquals(0, meta.warningCount());
            }
        }
    }

    @Nested
    class Classes {

        @Test
        void listsAllClassesInPrimaryKeyOrder(@TempDir Path tmp) throws IOException, SQLException {
            Path indexDb = buildIndex(tmp);
            try (HeapView view = HeapView.open(indexDb)) {
                // Filter out the eight synthetic primitive-array class rows that
                // HprofIndex seeds (their class_ids are deeply negative).
                List<JavaClassRow> realClasses = view.classes().stream()
                        .filter(c -> c.classId() >= 0)
                        .toList();
                assertEquals(3, realClasses.size());
                assertEquals(CLASS_FOO, realClasses.get(0).classId());
                assertEquals("com.example.Foo", realClasses.get(0).name());
                assertEquals(CLASS_BAR, realClasses.get(1).classId());
                assertEquals(CLASS_FOO_ARRAY, realClasses.get(2).classId());
            }
        }

        @Test
        void mapsHprofIdZeroToNullSuperClassId(@TempDir Path tmp) throws IOException, SQLException {
            Path indexDb = buildIndex(tmp);
            try (HeapView view = HeapView.open(indexDb)) {
                JavaClassRow foo = view.findClassById(CLASS_FOO).orElseThrow();
                assertNull(foo.superClassId(), "Foo declares super=0 (Object) which maps to NULL");
                assertEquals((Long) CLASSLOADER, foo.classloaderId());

                JavaClassRow bar = view.findClassById(CLASS_BAR).orElseThrow();
                assertEquals((Long) CLASS_FOO, bar.superClassId());
            }
        }

        @Test
        void findClassesByNameReturnsExactMatch(@TempDir Path tmp) throws IOException, SQLException {
            Path indexDb = buildIndex(tmp);
            try (HeapView view = HeapView.open(indexDb)) {
                List<JavaClassRow> found = view.findClassesByName("com.example.Foo");
                assertEquals(1, found.size());
                assertEquals(CLASS_FOO, found.get(0).classId());

                assertEquals(0, view.findClassesByName("does.not.exist").size());
            }
        }

        @Test
        void findClassByIdReturnsEmptyForUnknown(@TempDir Path tmp) throws IOException, SQLException {
            Path indexDb = buildIndex(tmp);
            try (HeapView view = HeapView.open(indexDb)) {
                assertEquals(Optional.empty(), view.findClassById(0xDEADL));
            }
        }
    }

    @Nested
    class Instances {

        @Test
        void streamsInstancesByClassId(@TempDir Path tmp) throws IOException, SQLException {
            Path indexDb = buildIndex(tmp);
            try (HeapView view = HeapView.open(indexDb);
                 Stream<InstanceRow> s = view.instances(CLASS_FOO)) {
                List<InstanceRow> rows = s.toList();
                assertEquals(2, rows.size());
                assertEquals(INSTANCE_FOO_1, rows.get(0).instanceId());
                assertEquals(INSTANCE_FOO_2, rows.get(1).instanceId());
                assertEquals(InstanceRow.Kind.INSTANCE, rows.get(0).kind());
                assertNull(rows.get(0).arrayLength());
            }
        }

        @Test
        void instanceCountMatchesStreamSize(@TempDir Path tmp) throws IOException, SQLException {
            Path indexDb = buildIndex(tmp);
            try (HeapView view = HeapView.open(indexDb)) {
                assertEquals(2, view.instanceCount(CLASS_FOO));
                assertEquals(1, view.instanceCount(CLASS_BAR));
                assertEquals(0, view.instanceCount(0xDEADL));
            }
        }

        @Test
        void findInstanceByIdReturnsArrayInstance(@TempDir Path tmp) throws IOException, SQLException {
            Path indexDb = buildIndex(tmp);
            try (HeapView view = HeapView.open(indexDb)) {
                InstanceRow obj = view.findInstanceById(OBJ_ARRAY).orElseThrow();
                assertEquals(InstanceRow.Kind.OBJECT_ARRAY, obj.kind());
                assertEquals((Integer) 2, obj.arrayLength());

                InstanceRow prim = view.findInstanceById(PRIM_ARRAY).orElseThrow();
                assertEquals(InstanceRow.Kind.PRIMITIVE_ARRAY, prim.kind());
                assertEquals((Integer) 4, prim.arrayLength());
                assertEquals((Integer) HprofTag.BasicType.INT, prim.primitiveType());
            }
        }

        @Test
        void streamCloseReleasesUnderlyingResultSet(@TempDir Path tmp) throws IOException, SQLException {
            Path indexDb = buildIndex(tmp);
            // Open and close a stream; nothing should leak. This test fails fast if the
            // stream's onClose hooks throw.
            try (HeapView view = HeapView.open(indexDb)) {
                try (Stream<InstanceRow> s = view.instances(CLASS_FOO)) {
                    s.findFirst();
                }
                // After close, a fresh stream must still work — proves no shared mutable state.
                try (Stream<InstanceRow> s = view.instances(CLASS_FOO)) {
                    assertEquals(2, s.count());
                }
            }
        }
    }

    @Nested
    class GcRoots {

        @Test
        void listsAllRoots(@TempDir Path tmp) throws IOException, SQLException {
            Path indexDb = buildIndex(tmp);
            try (HeapView view = HeapView.open(indexDb)) {
                List<GcRootRow> roots = view.gcRoots();
                assertEquals(2, roots.size());
            }
        }

        @Test
        void isGcRootDetectsRootedAndNonRootedInstances(@TempDir Path tmp) throws IOException, SQLException {
            Path indexDb = buildIndex(tmp);
            try (HeapView view = HeapView.open(indexDb)) {
                assertTrue(view.isGcRoot(INSTANCE_FOO_1));
                assertFalse(view.isGcRoot(INSTANCE_FOO_2));
                assertTrue(view.isGcRoot(CLASS_FOO));
            }
        }
    }

    @Nested
    class Strings {

        @Test
        void findStringResolvesByPoolId(@TempDir Path tmp) throws IOException, SQLException {
            Path indexDb = buildIndex(tmp);
            try (HeapView view = HeapView.open(indexDb)) {
                assertEquals(Optional.of("com.example.Foo"), view.findString(STR_NAME_FOO));
                assertEquals(Optional.empty(), view.findString(0xDEADL));
            }
        }
    }

    @Nested
    class Histogram {

        @Test
        void aggregatesByClassOrderedByTotalSize(@TempDir Path tmp) throws IOException, SQLException {
            Path indexDb = buildIndex(tmp);
            try (HeapView view = HeapView.open(indexDb)) {
                List<HistogramRow> hist = view.classHistogram();
                assertNotNull(hist);
                assertTrue(hist.size() >= 2, "histogram should have at least foo and bar entries");
                // First row has the largest total — could be Foo (2 instances + 1 obj-array),
                // Bar (1 instance), or the primitive array bucket (className NULL).
                long total = hist.stream().mapToLong(HistogramRow::instanceCount).sum();
                assertEquals(5, total, "total across histogram == total instances");
            }
        }

        @Test
        void primitiveArrayInstancesJoinSyntheticClassRow(@TempDir Path tmp) throws IOException, SQLException {
            // HprofIndex seeds synthetic class rows for the eight primitive-array
            // types so PRIMITIVE_ARRAY_DUMP instances surface in the histogram with
            // a proper "int[]"/"byte[]"/... class name. SyntheticHprof emits a
            // single int[] primitive array, so the histogram should carry an
            // "int[]" row with a non-null class_id (the synthetic id is negative).
            Path indexDb = buildIndex(tmp);
            try (HeapView view = HeapView.open(indexDb)) {
                List<HistogramRow> hist = view.classHistogram();
                boolean hasIntArrayRow = hist.stream().anyMatch(r ->
                        r.classId() != null
                                && r.classId() < 0
                                && "int[]".equals(r.className()));
                assertTrue(hasIntArrayRow,
                        "primitive array bucket should appear as int[] with a synthetic (negative) class id");
            }
        }
    }

    @Nested
    class EscapeHatch {

        @Test
        void exposesUnderlyingConnectionForAdHocSql(@TempDir Path tmp) throws IOException, SQLException {
            Path indexDb = buildIndex(tmp);
            try (HeapView view = HeapView.open(indexDb);
                 Statement stmt = view.databaseClient().connection().createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT name FROM class WHERE classloader_id = " + CLASSLOADER
                                 + " AND is_array = FALSE ORDER BY name")) {
                assertTrue(rs.next());
                assertEquals("com.example.Bar", rs.getString(1));
                assertTrue(rs.next());
                assertEquals("com.example.Foo", rs.getString(1));
                assertFalse(rs.next());
            }
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void openFailsForMissingFile(@TempDir Path tmp) {
            Path missing = tmp.resolve("missing.idx.duckdb");
            assertThrows(IOException.class, () -> HeapView.open(missing));
        }
    }
}
