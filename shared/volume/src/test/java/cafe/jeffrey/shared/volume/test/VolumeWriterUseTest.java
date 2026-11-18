/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.shared.volume.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link VolumeWriter} lives in {@code shared/volume}'s main code only so that every side's tests share one
 * copy; production never writes the volume this way (the provisioner lays it down). No main source outside
 * this package and Microscope's test-support module may name it.
 */
class VolumeWriterUseTest {

    private static final Path REPOSITORY_ROOT = Path.of("../..").toAbsolutePath().normalize();

    private static final String MAIN_SOURCES = "src/main/java";

    private static final String JAVA_SUFFIX = ".java";

    /** Build output, dependencies and Git's own store: nothing in them is a source. */
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("target", "node_modules", ".git");

    /** The writer's own package, and Microscope's test support, whose RecordedSession writes through it. */
    private static final Set<Path> ALLOWED = Set.of(
            Path.of("shared/volume/src/main/java/cafe/jeffrey/shared/volume/test"),
            Path.of("jeffrey-microscope/microscope-test"));

    private static final Pattern NAMES_THE_WRITER = Pattern.compile("cafe\\.jeffrey\\.shared\\.volume\\.test\\.");

    @Test
    void noProductionSourceNamesTheWriter() throws IOException {
        assertEquals(List.of(), offenders(REPOSITORY_ROOT));
    }

    /** The scan itself: a main source naming the writer is listed; a test source or an allowed one is not. */
    @Test
    void theScanNamesOnlyProductionSources(@TempDir Path root) throws IOException {
        write(root, "volume-scheduler/src/main/java/Job.java",
                "import cafe.jeffrey.shared.volume.test.VolumeWriter;");
        write(root, "core/src/main/java/Inline.java",
                "    var path = cafe.jeffrey.shared.volume.test.VolumeWriter.session(dir, start);");
        write(root, "core/src/test/java/JobTest.java",
                "import cafe.jeffrey.shared.volume.test.VolumeWriter;");
        write(root, "jeffrey-microscope/microscope-test/src/main/java/Recorded.java",
                "import cafe.jeffrey.shared.volume.test.VolumeWriter;");
        write(root, "core/target/src/main/java/Generated.java",
                "import cafe.jeffrey.shared.volume.test.VolumeWriter;");
        write(root, "core/src/main/java/Reader.java",
                "import cafe.jeffrey.shared.volume.layout.ProjectTree;");

        assertEquals(List.of("core/src/main/java/Inline.java", "volume-scheduler/src/main/java/Job.java"),
                offenders(root));
    }

    private static List<String> offenders(Path root) throws IOException {
        List<String> offenders = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                Path relative = root.relativize(dir);
                if (SKIPPED_DIRECTORIES.contains(String.valueOf(dir.getFileName())) || ALLOWED.contains(relative)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                String relative = root.relativize(file).toString();
                if (relative.endsWith(JAVA_SUFFIX) && relative.contains(MAIN_SOURCES) && namesTheWriter(file)) {
                    offenders.add(relative);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return offenders.stream().sorted().toList();
    }

    private static boolean namesTheWriter(Path file) throws IOException {
        return NAMES_THE_WRITER.matcher(Files.readString(file)).find();
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
