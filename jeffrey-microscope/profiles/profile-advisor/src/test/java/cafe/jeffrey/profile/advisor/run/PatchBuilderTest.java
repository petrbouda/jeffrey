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

package cafe.jeffrey.profile.advisor.run;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchBuilderTest {

    @TempDir
    Path repository;

    private static final String PATCH = """
            --- a/src/Order.java
            +++ b/src/Order.java
            @@ -1,99 +1,99 @@
             class Order {
            -    int slow;
            +    int fast;
            """;

    private Path writeSource() throws IOException {
        Path file = repository.resolve("src/Order.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class Order {\n    int slow;\n}\n");
        return file;
    }

    @Nested
    class NothingToBuild {

        @Test
        void returnsNullWhenTheModelProposedNoEdit() {
            assertNull(new PatchBuilder(repository).build(null));
        }

        @Test
        void returnsNullForABlankSection() {
            assertNull(new PatchBuilder(repository).build("   \n  "));
        }

        @Test
        void discardsProseWrittenUnderThePatchMarker() {
            String prose = "I would refactor the lookup into a map, but I could not read the file.";

            assertNull(new PatchBuilder(repository).build(prose),
                    "a payload with no hunk cannot be applied and must not be offered as a patch");
        }
    }

    @Nested
    class Building {

        @Test
        void recountsTheHunkHeaderTheModelMiscounted() throws IOException {
            writeSource();

            String built = new PatchBuilder(repository).build(PATCH);

            assertNotNull(built);
            assertTrue(built.contains("@@ -1,2 +1,2 @@"), built);
        }

        @Test
        void endsWithExactlyOneNewlineSoGitApplyAcceptsIt() throws IOException {
            writeSource();

            String built = new PatchBuilder(repository).build(PATCH.stripTrailing());

            assertTrue(built.endsWith("\n"), "git apply rejects a patch with no trailing newline");
            assertTrue(built.endsWith("+    int fast;\n"), built);
        }

        /**
         * A patch is reported, not dropped, when its files do not resolve. The model may be right about
         * a file Jeffrey cannot see, and a diff the reader can read is worth more than silence.
         */
        @Test
        void keepsAPatchWhoseFilesAreNotInTheSourceFolder() {
            String built = new PatchBuilder(repository).build(PATCH);

            assertNotNull(built);
            assertTrue(built.contains("+    int fast;"), built);
        }

        @Test
        void buildsNothingWhenNoSourceFolderIsConfigured() {
            assertNotNull(new PatchBuilder(null).build(PATCH),
                    "an unconfigured root cannot verify the files, but the diff is still readable");
        }
    }

    @Nested
    class PathContainment {

        @Test
        void treatsAPathEscapingTheRootAsUnresolved() throws IOException {
            Path outside = repository.getParent().resolve("Outside.java");
            Files.writeString(outside, "class Outside {}\n");
            writeSource();
            SourcePaths paths = new SourcePaths(repository);

            assertTrue(paths.containsFile("src/Order.java"));
            assertFalse(paths.containsFile("../Outside.java"), "a path leaving the root is not source");
            assertFalse(paths.containsFile("src/Missing.java"));
        }

        @Test
        void treatsADirectoryAsNotAFile() throws IOException {
            writeSource();

            assertFalse(new SourcePaths(repository).containsFile("src"));
        }
    }
}
