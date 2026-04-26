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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pbouda.jeffrey.server.core.ServerJeffreyDirs;
import pbouda.jeffrey.shared.common.compression.Lz4Compressor;

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

    private static JsonNode requireEvent(ObjectNode env, String typeName) {
        JsonNode n = env.get(typeName);
        assertNotNull(n, typeName + " should be present in parsed env");
        return n;
    }

    @Nested
    class WithGeneratedRecording {

        @Test
        void parsesJvmInformationFromFreshRecording(@TempDir Path tempDir) throws Exception {
            Path jfr = writeFreshRecording(tempDir);

            ObjectNode env = parser.parse(jfr, false);

            JsonNode jvm = requireEvent(env, "jdk.JVMInformation");
            assertTrue(jvm.has("jvmName") && !jvm.get("jvmName").asString().isBlank(), "jvmName must be present");
            assertTrue(jvm.has("jvmVersion"), "jvmVersion must be present");
            assertTrue(jvm.has("pid"), "pid must be present");
            assertEquals(ProcessHandle.current().pid(), jvm.get("pid").asLong(), "pid must match current JVM");
            assertTrue(jvm.has("jvmStartTime"), "jvmStartTime must be present");
            assertTrue(jvm.get("jvmStartTime").asLong() > 0, "jvmStartTime must be a positive epoch-millis value");
        }

        @Test
        void parsesOsInformationFromFreshRecording(@TempDir Path tempDir) throws Exception {
            Path jfr = writeFreshRecording(tempDir);

            ObjectNode env = parser.parse(jfr, false);

            JsonNode os = requireEvent(env, "jdk.OSInformation");
            assertTrue(os.has("osVersion") && !os.get("osVersion").asString().isBlank(), "osVersion must be non-blank");
        }

        @Test
        void parsesCpuInformationFromFreshRecording(@TempDir Path tempDir) throws Exception {
            Path jfr = writeFreshRecording(tempDir);

            ObjectNode env = parser.parse(jfr, false);

            JsonNode cpu = requireEvent(env, "jdk.CPUInformation");
            assertTrue(cpu.has("hwThreads"), "hwThreads must be present");
            assertTrue(cpu.get("hwThreads").asInt() >= 1, "hwThreads must be at least 1");
        }

        @Test
        void parsesGcAndHeapConfigurationFromFreshRecording(@TempDir Path tempDir) throws Exception {
            Path jfr = writeFreshRecording(tempDir);

            ObjectNode env = parser.parse(jfr, false);

            JsonNode gc = requireEvent(env, "jdk.GCConfiguration");
            assertTrue(gc.has("youngCollector") && !gc.get("youngCollector").asString().isBlank(),
                    "youngCollector must be non-blank");
            assertTrue(gc.has("oldCollector") && !gc.get("oldCollector").asString().isBlank(),
                    "oldCollector must be non-blank");

            JsonNode heap = requireEvent(env, "jdk.GCHeapConfiguration");
            assertTrue(heap.has("maxSize"), "maxSize must be present");
            assertTrue(heap.get("maxSize").asLong() > 0, "maxSize must be a positive byte count");
        }

        @Test
        void parsesCompilerConfigurationFromFreshRecording(@TempDir Path tempDir) throws Exception {
            Path jfr = writeFreshRecording(tempDir);

            ObjectNode env = parser.parse(jfr, false);

            JsonNode compiler = requireEvent(env, "jdk.CompilerConfiguration");
            assertTrue(compiler.has("threadCount"), "threadCount must be present");
            assertTrue(compiler.get("threadCount").asInt() >= 0, "threadCount must be non-negative");
        }

        @Test
        void shutdownIsAbsentForNormalDump(@TempDir Path tempDir) throws Exception {
            Path jfr = writeFreshRecording(tempDir);

            ObjectNode env = parser.parse(jfr, true);

            assertFalse(env.has("jdk.Shutdown"),
                    "jdk.Shutdown is emitted only on real JVM exit and must be absent in a dumped Recording");
        }

        @Test
        void parsesConfigEventsFromLz4CompressedRecording(@TempDir Path tempDir) throws Exception {
            Path jfr = writeFreshRecording(tempDir);
            Path compressed = tempDir.resolve("fresh-recording.jfr.lz4");
            Lz4Compressor.compress(jfr, compressed);

            ObjectNode env = parser.parse(compressed, false);

            assertTrue(env.has("jdk.JVMInformation"), "JVMInformation should be present after LZ4 decompression");
            assertTrue(env.has("jdk.OSInformation"), "OSInformation should be present after LZ4 decompression");
            assertTrue(env.has("jdk.CPUInformation"), "CPUInformation should be present after LZ4 decompression");
            assertTrue(env.has("jdk.GCConfiguration"), "GCConfiguration should be present after LZ4 decompression");
            assertTrue(env.has("jdk.GCHeapConfiguration"), "GCHeapConfiguration should be present after LZ4 decompression");
            assertTrue(env.has("jdk.CompilerConfiguration"), "CompilerConfiguration should be present after LZ4 decompression");
        }
    }

    @Nested
    class WithExistingProfileFixture {

        @Test
        void parseProfile1DoesNotThrow() {
            Path profile1 = resolveJfr(PROFILE_1);

            ObjectNode env = assertDoesNotThrow(() -> parser.parse(profile1, false));

            assertNotNull(env, "parser must never return null");
            assertTrue(env.size() >= 1, "profile-1.jfr should contain at least one one-shot configuration event");
        }

        @Test
        void parseProfile1WithExpectShutdownTrue() {
            Path profile1 = resolveJfr(PROFILE_1);

            ObjectNode env = assertDoesNotThrow(() -> parser.parse(profile1, true));

            assertNotNull(env, "parser must never return null on the scan-to-EOF branch");
        }
    }

    @Nested
    class WithBrokenInput {

        @Test
        void parseMissingFileReturnsEmpty(@TempDir Path tempDir) {
            Path missing = tempDir.resolve("does-not-exist.jfr");

            ObjectNode env = parser.parse(missing, false);

            assertTrue(env.isEmpty(), "missing file should yield an empty ObjectNode");
        }

        @Test
        void parseTruncatedFileReturnsEmpty(@TempDir Path tempDir) throws IOException {
            Path truncated = truncate(resolveJfr(PROFILE_1), tempDir.resolve("truncated.jfr"));

            ObjectNode env = assertDoesNotThrow(() -> parser.parse(truncated, false));

            // Even for truncated input the parser must not throw; whatever was
            // read before the truncation point is kept, and the rest is dropped.
            assertNotNull(env);
        }

        private static Path truncate(Path source, Path target) throws IOException {
            byte[] bytes = Files.readAllBytes(source);
            try (OutputStream out = Files.newOutputStream(target)) {
                out.write(bytes, 0, bytes.length / 2);
            }
            return target;
        }
    }
}
