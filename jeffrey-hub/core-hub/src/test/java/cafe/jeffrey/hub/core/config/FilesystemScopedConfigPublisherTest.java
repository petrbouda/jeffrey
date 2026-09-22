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


package cafe.jeffrey.hub.core.config;

import cafe.jeffrey.shared.common.config.ContentDigest;
import cafe.jeffrey.shared.common.config.ScopedConfigLayout;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilesystemScopedConfigPublisherTest {

    private static final String CONTENT = "asprof-settings = \"start,cpu\"\n";

    private final FilesystemScopedConfigPublisher publisher = new FilesystemScopedConfigPublisher();

    @TempDir
    Path scopeDir;

    private Path configFile() {
        return ScopedConfigLayout.configFile(scopeDir);
    }

    @Nested
    class Publishing {

        @Test
        void writesTheContentAndCreatesTheDirectory() throws IOException {
            String digest = publisher.publish(scopeDir, CONTENT);

            assertEquals(CONTENT, Files.readString(configFile()));
            assertEquals(ContentDigest.sha256Hex(CONTENT), digest);
        }

        @Test
        void replacesContentThatChanged() throws IOException {
            publisher.publish(scopeDir, CONTENT);
            publisher.publish(scopeDir, "asprof-settings = \"start,alloc\"\n");

            assertEquals("asprof-settings = \"start,alloc\"\n", Files.readString(configFile()));
        }

        /**
         * The job republishes every scope on every tick, so an unchanged scope must not rewrite its
         * file: a rewrite would churn the volume and, worse, make every tick look like a change to
         * anything watching the file's timestamp.
         */
        @Test
        void leavesAnUnchangedFileAlone() throws IOException {
            publisher.publish(scopeDir, CONTENT);
            Files.setLastModifiedTime(configFile(), FileTime.fromMillis(0));

            publisher.publish(scopeDir, CONTENT);

            assertEquals(0, Files.getLastModifiedTime(configFile()).toMillis(),
                    "the file was rewritten even though nothing changed");
        }

        @Test
        void leavesNoTemporaryFileBehind() throws IOException {
            publisher.publish(scopeDir, CONTENT);

            assertEquals(List.of(ScopedConfigLayout.CONFIG_FILE), namesIn(configFile().getParent()));
        }

        /**
         * An editor's save and the synchronizer's tick can publish one scope at the same moment.
         * With a shared temporary name they would interleave their bytes into a file that is then
         * renamed into place, so each publish must write through a name of its own.
         */
        @Test
        void concurrentPublishesDoNotCorruptTheFile() throws Exception {
            String first = "asprof-settings = \"" + "a".repeat(200_000) + "\"\n";
            String second = "asprof-settings = \"" + "b".repeat(200_000) + "\"\n";

            Thread one = Thread.ofPlatform().start(() -> publisher.publish(scopeDir, first));
            Thread two = Thread.ofPlatform().start(() -> publisher.publish(scopeDir, second));
            one.join();
            two.join();

            String written = Files.readString(configFile());
            assertTrue(written.equals(first) || written.equals(second),
                    "the published file is neither of the two versions, so the writes interleaved");
        }
    }

    @Nested
    class Removing {

        @Test
        void emptyContentRemovesTheFile() {
            publisher.publish(scopeDir, CONTENT);

            assertEquals("", publisher.publish(scopeDir, ""));
            assertFalse(Files.exists(configFile()));
        }

        @Test
        void removingWhatIsNotThereSucceeds() {
            assertEquals("", publisher.publish(scopeDir, ""));
        }
    }

    @Nested
    class Reading {

        @Test
        void readsBackWhatWasPublished() {
            publisher.publish(scopeDir, CONTENT);

            assertEquals(CONTENT, publisher.read(scopeDir).orElseThrow());
        }

        @Test
        void readsNothingWhenThereIsNoFile() {
            assertTrue(publisher.read(scopeDir).isEmpty());
        }
    }

    private static List<String> namesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }
}
