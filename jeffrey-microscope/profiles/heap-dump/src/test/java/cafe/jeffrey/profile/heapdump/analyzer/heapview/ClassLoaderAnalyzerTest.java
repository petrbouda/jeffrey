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

    /** HprofIndex seeds one synthetic class row per HPROF primitive basic-type
     *  (byte/char/short/int/long/float/double/boolean) so primitive-array
     *  instances can be attributed to a real class name. They all bind to the
     *  bootstrap loader, so per-loader expectations must account for them. */
    private static final int SYNTHETIC_PRIMITIVE_ARRAY_CLASSES = 8;

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
            assertEquals(4 + SYNTHETIC_PRIMITIVE_ARRAY_CLASSES, report.totalClasses(),
                    "4 explicit classes + 8 synthetic primitive-array classes (bootstrap)");
            assertEquals(1, report.duplicateClassCount(), "DupClass loaded by two loaders");
            assertEquals("DupClass", report.duplicateClasses().get(0).className());
            assertEquals(2, report.duplicateClasses().get(0).loaderCount());
            assertTrue(report.leakChains().isEmpty(), "leak chains deferred to PR #10");
            // Bootstrap now leads with 1 explicit + 8 synthetic primitive-array classes = 9.
            assertEquals(1 + SYNTHETIC_PRIMITIVE_ARRAY_CLASSES,
                    report.classLoaders().get(0).classCount(),
                    "bootstrap sorted first (highest classCount)");
            // Without a dominator-tree build, retained_size table is empty → 0 for everyone.
            report.classLoaders().forEach(li -> assertEquals(0L, li.retainedSize()));
        }
    }

    /**
     * The {@code totalClassSize} column sums {@code instance.shallow_size}
     * for every instance of every class the loader owns. The {@code
     * retainedSize} column reports the retained size of the <em>loader
     * instance itself</em> — summing retained sizes across all owned
     * instances would double-count shared dominated subgraphs and exceed
     * the heap.
     */
    @Test
    void sumsInstanceShallowAndPullsRetainedSizeFromLoaderInstance(@TempDir Path tmp) throws IOException, SQLException {
        // One loader instance (CC10) defines one class with three rooted instances.
        // Each instance has a single int field → on-disk fields = 4, shallow = align(16 + 4) = 24.
        // The CC10 loader instance itself is a separate object with one OBJECT field (own shallow 24),
        // GC-rooted so the dominator builder assigns it a retained size.
        long loaderInstClass = 0xC200L;
        long oneClass = 0xC100L;
        long loaderInst = 0xCC10L;
        long inst1 = 0x100L;
        long inst2 = 0x101L;
        long inst3 = 0x102L;

        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "OneClass")
                .string(0xA002L, "n")
                .string(0xA003L, "TestLoader")
                .string(0xA004L, "owned")
                .loadClass(1, loaderInstClass, 0, 0xA003L)
                .loadClass(2, oneClass, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(loaderInstClass, 0xA004L)
                        .simpleClassDump(oneClass, 0L, loaderInst, 4, 0xA002L)
                        // Root the loader and the three instances.
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, loaderInst)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, inst1)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, inst2)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, inst3)
                        .instanceDump(loaderInst, loaderInstClass, idBytes(0L))
                        .instanceDump(inst1, oneClass, new byte[]{0, 0, 0, 0})
                        .instanceDump(inst2, oneClass, new byte[]{0, 0, 0, 0})
                        .instanceDump(inst3, oneClass, new byte[]{0, 0, 0, 0}))
                .heapDumpEnd()
                .writeTo(tmp, "shallow.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        cafe.jeffrey.profile.heapdump.parser.DominatorTreeBuilder.build(indexDb);

        try (HeapView view = HeapView.open(indexDb)) {
            ClassLoaderReport report = ClassLoaderAnalyzer.analyze(view);
            var cc10 = report.classLoaders().stream()
                    .filter(li -> li.objectId() == loaderInst)
                    .findFirst()
                    .orElseThrow();
            // 3 owned instances × 24 bytes = 72. The loader instance itself is loaded by
            // the bootstrap loader (super=0 on its class), so its own 24 bytes don't fall
            // under CC10's totalClassSize.
            assertEquals(72L, cc10.totalClassSize(),
                    "totalClassSize sums per-instance shallow_size, not class.instance_size");
            // The loader instance dominates only itself (the owned instances are GC-rooted
            // independently). Shallow = 24 → retained = 24.
            assertEquals(24L, cc10.retainedSize(),
                    "retainedSize is the retained size of the loader instance itself");
        }
    }

    private static byte[] idBytes(long id) {
        return new byte[]{
                (byte) (id >>> 56), (byte) (id >>> 48), (byte) (id >>> 40), (byte) (id >>> 32),
                (byte) (id >>> 24), (byte) (id >>> 16), (byte) (id >>> 8), (byte) id
        };
    }
}
