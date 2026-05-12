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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cafe.jeffrey.profile.heapdump.model.ClassLoaderLeakChain;
import cafe.jeffrey.profile.heapdump.parser.DominatorTreeBuilder;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassLoaderLeakChainAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);

    @Test
    void emptyResultWhenNoSuspiciousLoaders(@TempDir Path tmp) throws IOException, SQLException {
        // No webapp loaders + retained sizes well below the 50MB threshold → empty.
        long classId = 0xC001L;
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "X")
                .string(0xA002L, "f")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .simpleClassDump(classId, 0L, 0xCC00L, 16, 0xA002L)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, classId)
                        .instanceDump(0x100L, classId, new byte[]{0, 0, 0, 0}))
                .heapDumpEnd()
                .writeTo(tmp, "no-suspicious.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);
        try (HeapView view = HeapView.open(indexDb)) {
            List<ClassLoaderLeakChain> chains = ClassLoaderLeakChainAnalyzer.analyze(view);
            assertTrue(chains.isEmpty());
        }
    }

    @Test
    void detectsWebappClassLoaderByName(@TempDir Path tmp) throws IOException, SQLException {
        // Loader instance whose own class name is "WebappClassLoader" → flagged
        // even though its retained size is small.
        long loaderClass = 0xC100L;
        long appClass = 0xC200L;
        long loaderInstance = 0xCC00L;
        long appInstance = 0xAA00L;

        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "org.apache.catalina.loader.WebappClassLoader")
                .string(0xA002L, "com.example.App")
                .string(0xA003L, "f")
                .loadClass(1, loaderClass, 0, 0xA001L)
                .loadClass(2, appClass, 0, 0xA002L)
                .heapDumpSegment(seg -> seg
                        // The loader instance is itself classed as WebappClassLoader.
                        .simpleClassDump(loaderClass, 0L, 0L, 8, 0xA003L)
                        // App class lives "under" the loader (classloader_id = loaderInstance).
                        .simpleClassDump(appClass, 0L, loaderInstance, 16, 0xA003L)
                        .gcRootJavaFrame(loaderInstance, 1, 1)
                        .instanceDump(loaderInstance, loaderClass, idBytes(0L, 8))
                        .instanceDump(appInstance, appClass, new byte[]{0, 0, 0, 1}))
                .heapDumpEnd()
                .writeTo(tmp, "webapp.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);
        try (HeapView view = HeapView.open(indexDb)) {
            List<ClassLoaderLeakChain> chains = ClassLoaderLeakChainAnalyzer.analyze(view);
            assertTrue(chains.size() >= 1, "WebappClassLoader-named loader must be flagged");
            ClassLoaderLeakChain chain = chains.get(0);
            assertTrue(chain.classLoaderClassName().contains("WebappClassLoader"));
            // The path goes from the Java-frame root straight to the loader, no Thread on it.
            // No hints expected for this minimal scenario, but the chain object is well-formed.
            assertTrue(chain.classCount() >= 1);
        }
    }

    private static byte[] idBytes(long id, int idSize) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            if (idSize == 4) {
                d.writeInt((int) id);
            } else {
                d.writeLong(id);
            }
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
