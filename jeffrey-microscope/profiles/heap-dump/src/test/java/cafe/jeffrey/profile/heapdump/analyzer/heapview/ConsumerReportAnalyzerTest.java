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

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cafe.jeffrey.profile.heapdump.model.ConsumerEntry;
import cafe.jeffrey.profile.heapdump.model.ConsumerReport;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerReportAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);

    @Test
    void groupsByPackageAndReturnsTopByShallowSize(@TempDir Path tmp) throws IOException, SQLException {
        long fooClass = 0xC001L;        // com.example.Foo
        long barClass = 0xC002L;        // com.example.Bar
        long mapClass = 0xC003L;        // java.util.HashMap

        // 5 instances of Foo (3 instance fields × 4 bytes = ~12 bytes shallow), 2 of Bar, 1 of Map.
        // Both Foo + Bar are in com.example so they aggregate.
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "com.example.Foo")
                .string(0xA002L, "com.example.Bar")
                .string(0xA003L, "java.util.HashMap")
                .string(0xA004L, "f")
                .loadClass(1, fooClass, 0, 0xA001L)
                .loadClass(2, barClass, 0, 0xA002L)
                .loadClass(3, mapClass, 0, 0xA003L)
                .heapDumpSegment(seg -> {
                    seg.simpleClassDump(fooClass, 0L, 0L, 4, 0xA004L);
                    seg.simpleClassDump(barClass, 0L, 0L, 4, 0xA004L);
                    seg.simpleClassDump(mapClass, 0L, 0L, 4, 0xA004L);
                    for (int i = 0; i < 5; i++) {
                        seg.instanceDump(0x1000L + i, fooClass, new byte[]{0, 0, 0, 0});
                    }
                    for (int i = 0; i < 2; i++) {
                        seg.instanceDump(0x2000L + i, barClass, new byte[]{0, 0, 0, 0});
                    }
                    seg.instanceDump(0x3000L, mapClass, new byte[]{0, 0, 0, 0});
                })
                .heapDumpEnd()
                .writeTo(tmp, "consumer.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HeapView view = HeapView.open(indexDb)) {
            ConsumerReport report = ConsumerReportAnalyzer.analyze(view, 10);
            assertTrue(report.totalHeapSize() > 0);

            // com.example wins with 7 instances (5 Foo + 2 Bar) over java.util's 1.
            ConsumerEntry top = report.topConsumers().get(0);
            assertEquals("com.example", top.packageName());
            assertEquals(7, top.instanceCount());
            assertEquals(2, top.classCount());

            // Second entry should be java.util.
            ConsumerEntry second = report.topConsumers().get(1);
            assertEquals("java.util", second.packageName());
            assertEquals(1, second.instanceCount());
            assertEquals(1, second.classCount());

            // retainedSize is 0 until PR #12.
            report.topConsumers().forEach(e -> assertEquals(0L, e.retainedSize()));

            // componentReport empty for now.
            assertTrue(report.componentReport().isEmpty());
        }
    }

    @Test
    void packageDerivesFromArrayClassDescriptors(@TempDir Path tmp) throws IOException, SQLException {
        // [Lcom.example.Foo; should be aggregated into "com.example".
        long arrayClass = 0xC010L;
        long fooArray = 0x4000L;

        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "[Lcom.example.Foo;")
                .string(0xA002L, "f")
                .loadClass(1, arrayClass, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .simpleClassDump(arrayClass, 0L, 0L, 0, 0xA002L)
                        .objectArrayDump(fooArray, arrayClass, new long[]{}))
                .heapDumpEnd()
                .writeTo(tmp, "array-pkg.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            ConsumerReport report = ConsumerReportAnalyzer.analyze(view, 5);
            assertEquals("com.example", report.topConsumers().get(0).packageName());
        }
    }
}
