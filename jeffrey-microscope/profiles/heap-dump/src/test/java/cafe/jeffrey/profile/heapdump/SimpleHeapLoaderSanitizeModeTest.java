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

package cafe.jeffrey.profile.heapdump;

import cafe.jeffrey.profile.heapdump.sanitizer.HprofTestFileBuilder;
import cafe.jeffrey.profile.heapdump.sanitizer.SanitizeMode;
import cafe.jeffrey.profile.heapdump.sanitizer.SanitizeResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the configurable mode dispatch on {@link SimpleHeapLoader}: the
 * default mode comes from the constructor; per-call overrides take precedence.
 */
class SimpleHeapLoaderSanitizeModeTest {

    @TempDir
    Path tempDir;

    @Nested
    class ConstructorDefaults {

        @Test
        void noArgConstructorDefaultsToInPlace() {
            assertEquals(SanitizeMode.IN_PLACE, new SimpleHeapLoader().defaultSanitizeMode());
        }

        @Test
        void explicitDefaultIsHonoured() {
            assertEquals(SanitizeMode.COPY, new SimpleHeapLoader(SanitizeMode.COPY).defaultSanitizeMode());
        }

        @Test
        void rejectsNullDefaultMode() {
            assertThrows(IllegalArgumentException.class, () -> new SimpleHeapLoader(null));
        }
    }

    @Nested
    class DispatchUsesConfiguredDefault {

        @Test
        void inPlaceDefaultMutatesOriginal() throws IOException {
            Path file = brokenZeroLengthSegment("inplace-default.hprof");
            byte[] before = Files.readAllBytes(file);

            SanitizeResult result = new SimpleHeapLoader(SanitizeMode.IN_PLACE).sanitize(file);

            assertTrue(result.wasModified());
            // Original file mutated, no sibling produced.
            assertFalse(Arrays.equals(before, Files.readAllBytes(file)));
            assertFalse(Files.exists(siblingSanitized(file)));
        }

        @Test
        void copyDefaultLeavesOriginalAndProducesSibling() throws IOException {
            Path file = brokenZeroLengthSegment("copy-default.hprof");
            byte[] before = Files.readAllBytes(file);

            SanitizeResult result = new SimpleHeapLoader(SanitizeMode.COPY).sanitize(file);

            assertTrue(result.wasModified());
            assertArrayEquals(before, Files.readAllBytes(file));
            assertTrue(Files.exists(siblingSanitized(file)));
        }
    }

    @Nested
    class PerCallOverrideWins {

        @Test
        void copyOverrideWhenDefaultIsInPlace() throws IOException {
            Path file = brokenZeroLengthSegment("override-to-copy.hprof");
            byte[] before = Files.readAllBytes(file);

            SimpleHeapLoader loader = new SimpleHeapLoader(SanitizeMode.IN_PLACE);
            SanitizeResult result = loader.sanitize(file, SanitizeMode.COPY);

            assertTrue(result.wasModified());
            assertArrayEquals(before, Files.readAllBytes(file));
            assertTrue(Files.exists(siblingSanitized(file)));
        }

        @Test
        void inPlaceOverrideWhenDefaultIsCopy() throws IOException {
            Path file = brokenZeroLengthSegment("override-to-inplace.hprof");
            byte[] before = Files.readAllBytes(file);

            SimpleHeapLoader loader = new SimpleHeapLoader(SanitizeMode.COPY);
            SanitizeResult result = loader.sanitize(file, SanitizeMode.IN_PLACE);

            assertTrue(result.wasModified());
            assertFalse(Arrays.equals(before, Files.readAllBytes(file)));
            assertFalse(Files.exists(siblingSanitized(file)));
        }

        @Test
        void rejectsNullModeOnPerCallOverride() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SimpleHeapLoader().sanitize(tempDir.resolve("any.hprof"), null));
        }
    }

    private Path brokenZeroLengthSegment(String name) throws IOException {
        HprofTestFileBuilder builder = new HprofTestFileBuilder();
        byte[] root = builder.buildRootUnknownSubRecord(1L);
        builder.writeHeader().addZeroLengthHeapDumpSegment(root).addHeapDumpEnd();
        return builder.writeTo(tempDir.resolve(name));
    }

    private static Path siblingSanitized(Path heapDump) {
        return heapDump.resolveSibling(heapDump.getFileName().toString() + ".sanitized");
    }
}
