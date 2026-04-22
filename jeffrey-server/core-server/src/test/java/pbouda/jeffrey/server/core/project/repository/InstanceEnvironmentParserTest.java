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

package pbouda.jeffrey.server.core.project.repository;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pbouda.jeffrey.server.core.ServerJeffreyDirs;
import pbouda.jeffrey.shared.common.compression.Lz4Compressor;
import pbouda.jeffrey.shared.common.model.repository.InstanceEnvironment;
import pbouda.jeffrey.shared.common.model.repository.InstanceEnvironment.CompilerConfiguration;
import pbouda.jeffrey.shared.common.model.repository.InstanceEnvironment.CpuInformation;
import pbouda.jeffrey.shared.common.model.repository.InstanceEnvironment.GcConfiguration;
import pbouda.jeffrey.shared.common.model.repository.InstanceEnvironment.GcHeapConfiguration;
import pbouda.jeffrey.shared.common.model.repository.InstanceEnvironment.JvmInformation;
import pbouda.jeffrey.shared.common.model.repository.InstanceEnvironment.OsInformation;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceEnvironmentParserTest {

    private static final String PROFILE_1 = "profile-1.jfr";

    @TempDir
    Path jeffreyHome;

    private InstanceEnvironmentParser parser;

    @BeforeEach
    void setUp() {
        ServerJeffreyDirs dirs = new ServerJeffreyDirs(jeffreyHome);
        dirs.initialize();
        parser = new InstanceEnvironmentParser(dirs);
    }

    private static Path resolveJfr(String name) {
        try {
            return Path.of(InstanceEnvironmentParserTest.class.getClassLoader()
                    .getResource("jfrs/" + name).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to resolve JFR test file: " + name, e);
        }
    }

    /**
     * Dumps a short Recording with the "default" profile so the resulting chunk carries the
     * full set of one-shot configuration events (JVMInformation, OSInformation, CPUInformation,
     * GCConfiguration, GCHeapConfiguration, CompilerConfiguration). A brief start/stop window
     * is required — dumping an immediately-stopped Recording produces a malformed chunk.
     */
    private static Path writeFreshRecording(Path dir) throws Exception {
        Path dumpFile = dir.resolve("fresh-recording.jfr");
        Configuration defaultConfig = Configuration.getConfiguration("default");
        try (Recording recording = new Recording(defaultConfig)) {
            recording.start();
            Thread.sleep(200);
            recording.stop();
            recording.dump(dumpFile);
        }
        return dumpFile;
    }

    @Nested
    class WithGeneratedRecording {

        @Test
        void parsesJvmInformationFromFreshRecording(@TempDir Path tempDir) throws Exception {
            Path jfr = writeFreshRecording(tempDir);

            InstanceEnvironment env = parser.parse(jfr, false);

            assertTrue(env.jvm().isPresent(), "JvmInformation should be present in a fresh Recording dump");
            JvmInformation jvm = env.jvm().get();
            assertTrue(jvm.jvmName().isPresent() && !jvm.jvmName().get().isBlank(), "jvmName must be present");
            assertTrue(jvm.jvmVersion().isPresent(), "jvmVersion must be present");
            assertTrue(jvm.pid().isPresent(), "pid must be present");
            assertEquals(ProcessHandle.current().pid(), jvm.pid().get(), "pid must match current JVM");
            assertTrue(jvm.jvmStartTime().isPresent(), "jvmStartTime must be present");
            assertTrue(jvm.jvmStartTime().get() > 0, "jvmStartTime must be a positive epoch-millis value");
        }

        @Test
        void parsesOsInformationFromFreshRecording(@TempDir Path tempDir) throws Exception {
            Path jfr = writeFreshRecording(tempDir);

            InstanceEnvironment env = parser.parse(jfr, false);

            assertTrue(env.os().isPresent(), "OsInformation should be present in a fresh Recording dump");
            OsInformation os = env.os().get();
            assertTrue(os.osVersion().isPresent() && !os.osVersion().get().isBlank(), "osVersion must be non-blank");
        }

        @Test
        void parsesCpuInformationFromFreshRecording(@TempDir Path tempDir) throws Exception {
            Path jfr = writeFreshRecording(tempDir);

            InstanceEnvironment env = parser.parse(jfr, false);

            assertTrue(env.cpu().isPresent(), "CpuInformation should be present in a fresh Recording dump");
            CpuInformation cpu = env.cpu().get();
            assertTrue(cpu.hwThreads().isPresent(), "hwThreads must be present");
            assertTrue(cpu.hwThreads().get() >= 1, "hwThreads must be at least 1");
        }

        @Test
        void parsesGcAndHeapConfigurationFromFreshRecording(@TempDir Path tempDir) throws Exception {
            Path jfr = writeFreshRecording(tempDir);

            InstanceEnvironment env = parser.parse(jfr, false);

            assertTrue(env.gc().isPresent(), "GcConfiguration should be present in a fresh Recording dump");
            GcConfiguration gc = env.gc().get();
            assertTrue(gc.youngCollector().isPresent() && !gc.youngCollector().get().isBlank(),
                    "youngCollector must be non-blank");
            assertTrue(gc.oldCollector().isPresent() && !gc.oldCollector().get().isBlank(),
                    "oldCollector must be non-blank");

            assertTrue(env.gcHeap().isPresent(), "GcHeapConfiguration should be present in a fresh Recording dump");
            GcHeapConfiguration heap = env.gcHeap().get();
            assertTrue(heap.maxSize().isPresent(), "maxSize must be present");
            assertTrue(heap.maxSize().get() > 0, "maxSize must be a positive byte count");
        }

        @Test
        void parsesCompilerConfigurationFromFreshRecording(@TempDir Path tempDir) throws Exception {
            Path jfr = writeFreshRecording(tempDir);

            InstanceEnvironment env = parser.parse(jfr, false);

            assertTrue(env.compiler().isPresent(), "CompilerConfiguration should be present in a fresh Recording dump");
            CompilerConfiguration compiler = env.compiler().get();
            assertTrue(compiler.threadCount().isPresent(), "threadCount must be present");
            assertTrue(compiler.threadCount().get() >= 0, "threadCount must be non-negative");
        }

        @Test
        void shutdownIsAbsentForNormalDump(@TempDir Path tempDir) throws Exception {
            Path jfr = writeFreshRecording(tempDir);

            InstanceEnvironment env = parser.parse(jfr, true);

            assertFalse(env.shutdown().isPresent(),
                    "jdk.Shutdown is emitted only on real JVM exit and must be absent in a dumped Recording");
        }

        @Test
        void parsesConfigEventsFromLz4CompressedRecording(@TempDir Path tempDir) throws Exception {
            Path jfr = writeFreshRecording(tempDir);
            Path compressed = tempDir.resolve("fresh-recording.jfr.lz4");
            Lz4Compressor.compress(jfr, compressed);

            InstanceEnvironment env = parser.parse(compressed, false);

            assertTrue(env.jvm().isPresent(), "JvmInformation should be present after LZ4 decompression");
            assertTrue(env.os().isPresent(), "OsInformation should be present after LZ4 decompression");
            assertTrue(env.cpu().isPresent(), "CpuInformation should be present after LZ4 decompression");
            assertTrue(env.gc().isPresent(), "GcConfiguration should be present after LZ4 decompression");
            assertTrue(env.gcHeap().isPresent(), "GcHeapConfiguration should be present after LZ4 decompression");
            assertTrue(env.compiler().isPresent(), "CompilerConfiguration should be present after LZ4 decompression");
        }
    }

    @Nested
    class WithExistingProfileFixture {

        @Test
        void parseProfile1DoesNotThrow() {
            Path profile1 = resolveJfr(PROFILE_1);

            InstanceEnvironment env = assertDoesNotThrow(() -> parser.parse(profile1, false));

            assertNotNull(env, "parser must never return null");
            boolean anyPresent = env.jvm().isPresent()
                    || env.os().isPresent()
                    || env.cpu().isPresent()
                    || env.gc().isPresent()
                    || env.gcHeap().isPresent()
                    || env.compiler().isPresent()
                    || env.container().isPresent()
                    || env.virtualization().isPresent()
                    || env.shutdown().isPresent();
            assertTrue(anyPresent, "profile-1.jfr should contain at least one one-shot configuration event");
        }

        @Test
        void parseProfile1WithExpectShutdownTrue() {
            Path profile1 = resolveJfr(PROFILE_1);

            InstanceEnvironment env = assertDoesNotThrow(() -> parser.parse(profile1, true));

            assertNotNull(env, "parser must never return null on the scan-to-EOF branch");
        }
    }

    @Nested
    class WithBrokenInput {

        @Test
        void parseMissingFileReturnsEmpty(@TempDir Path tempDir) {
            Path missing = tempDir.resolve("does-not-exist.jfr");

            InstanceEnvironment env = parser.parse(missing, false);

            assertAllEmpty(env);
        }

        @Test
        void parseTruncatedFileReturnsEmpty(@TempDir Path tempDir) throws IOException {
            Path truncated = truncate(resolveJfr(PROFILE_1), tempDir.resolve("truncated.jfr"));

            InstanceEnvironment env = assertDoesNotThrow(() -> parser.parse(truncated, false));

            assertAllEmpty(env);
        }

        private static Path truncate(Path source, Path target) throws IOException {
            byte[] bytes = Files.readAllBytes(source);
            try (OutputStream out = Files.newOutputStream(target)) {
                out.write(bytes, 0, bytes.length / 2);
            }
            return target;
        }

        private static void assertAllEmpty(InstanceEnvironment env) {
            assertFalse(env.jvm().isPresent(), "jvm must be empty");
            assertFalse(env.os().isPresent(), "os must be empty");
            assertFalse(env.cpu().isPresent(), "cpu must be empty");
            assertFalse(env.gc().isPresent(), "gc must be empty");
            assertFalse(env.gcHeap().isPresent(), "gcHeap must be empty");
            assertFalse(env.compiler().isPresent(), "compiler must be empty");
            assertFalse(env.container().isPresent(), "container must be empty");
            assertFalse(env.virtualization().isPresent(), "virtualization must be empty");
            assertFalse(env.shutdown().isPresent(), "shutdown must be empty");
        }
    }
}
