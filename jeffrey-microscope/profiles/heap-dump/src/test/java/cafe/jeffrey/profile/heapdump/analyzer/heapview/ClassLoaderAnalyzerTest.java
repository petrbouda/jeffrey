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

import cafe.jeffrey.profile.heapdump.model.ClassLoaderReport;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassLoaderAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);

    @Test
    void aggregatesByLoaderAndDetectsDuplicateClasses(@TempDir Path tmp) throws IOException, SQLException {
        // Two loaders (0xCC10, 0xCC20) plus bootstrap; class "DupClass" loaded by both.
        long bootClass = 0xC001L;
        long dup1 = 0xC002L;
        long dup2 = 0xC003L;
        long uniq = 0xC004L;

        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "java.lang.String")
                .string(0xA002L, "DupClass")
                .string(0xA003L, "Unique")
                .string(0xA004L, "f")
                .loadClass(1, bootClass, 0, 0xA001L)
                .loadClass(2, dup1, 0, 0xA002L)
                .loadClass(3, dup2, 0, 0xA002L)
                .loadClass(4, uniq, 0, 0xA003L)
                .heapDumpSegment(seg -> seg
                        // boot loader (id 0)
                        .simpleClassDump(bootClass, 0L, 0L, 16, 0xA004L)
                        // loader instance 0xCC10
                        .simpleClassDump(dup1, 0L, 0xCC10L, 16, 0xA004L)
                        // loader instance 0xCC20
                        .simpleClassDump(dup2, 0L, 0xCC20L, 16, 0xA004L)
                        .simpleClassDump(uniq, 0L, 0xCC10L, 16, 0xA004L)
                        // one instance each so totalClassSize is non-zero
                        .instanceDump(0x100L, bootClass, new byte[]{0, 0, 0, 0})
                        .instanceDump(0x101L, dup1, new byte[]{0, 0, 0, 0})
                        .instanceDump(0x102L, dup2, new byte[]{0, 0, 0, 0})
                        .instanceDump(0x103L, uniq, new byte[]{0, 0, 0, 0})
                        // gc roots
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, bootClass))
                .heapDumpEnd()
                .writeTo(tmp, "loaders.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HeapView view = HeapView.open(indexDb)) {
            ClassLoaderReport report = ClassLoaderAnalyzer.analyze(view);
            assertEquals(3, report.totalClassLoaders(), "bootstrap + CC10 + CC20");
            assertEquals(4, report.totalClasses());
            assertEquals(1, report.duplicateClassCount(), "DupClass loaded by two loaders");
            assertEquals("DupClass", report.duplicateClasses().get(0).className());
            assertEquals(2, report.duplicateClasses().get(0).loaderCount());
            assertTrue(report.leakChains().isEmpty(), "leak chains deferred to PR #10");
            // The CC10 loader holds two classes (dup1 + uniq); CC20 holds one; bootstrap holds one.
            assertEquals(2, report.classLoaders().get(0).classCount(),
                    "loader sorted by classCount descending");
            // retainedSize is 0 until PR #10
            report.classLoaders().forEach(li -> assertEquals(0L, li.retainedSize()));
        }
    }
}
