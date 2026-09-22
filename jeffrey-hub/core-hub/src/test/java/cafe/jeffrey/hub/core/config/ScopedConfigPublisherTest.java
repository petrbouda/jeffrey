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

import cafe.jeffrey.hub.model.config.ScopedConfigEntry;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.shared.common.config.ConfigType;
import cafe.jeffrey.shared.common.config.ScopedConfigLayout;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopedConfigPublisherTest {

    private static final Instant NOW = Instant.parse("2025-01-01T12:00:00Z");

    private final ScopedConfigPublisher publisher = new ScopedConfigPublisher();

    @TempDir
    Path scopeDir;

    private static ScopedConfigEntry entry(String value) {
        return new ScopedConfigEntry(ScopedConfigKey.global(), ConfigType.ASPROF_SETTINGS, value, NOW);
    }

    private Path configFile() {
        return ScopedConfigLayout.configFile(scopeDir);
    }

    private String published() throws IOException {
        return Files.readString(configFile());
    }

    @Nested
    class Rendering {

        @Test
        void rendersTheValueUnderTheTypesKey() {
            String rendered = ScopedConfigPublisher.render(List.of(entry("-agentpath:/opt/lib.so=start,cpu")));

            assertEquals("-agentpath:/opt/lib.so=start,cpu",
                    ConfigFactory.parseString(rendered).getString("asprof-settings"));
        }

        /**
         * The job republishes every scope on every tick and the publisher skips a file whose
         * content already matches, so the same values must always render to the same bytes or
         * every tick would rewrite every file.
         */
        @Test
        void theSameValuesRenderToTheSameBytes() {
            assertEquals(
                    ScopedConfigPublisher.render(List.of(entry("start,cpu"))),
                    ScopedConfigPublisher.render(List.of(entry("start,cpu"))));
        }

        @Test
        void quotesAValueThatWouldOtherwiseBreakTheSyntax() {
            String awkward = "-agentpath:/opt/lib.so=start,file=\"quoted\",tag=a:b";

            String rendered = ScopedConfigPublisher.render(List.of(entry(awkward)));

            assertEquals(awkward, ConfigFactory.parseString(rendered).getString("asprof-settings"));
        }

        @Test
        void saysWhoWroteTheFile() {
            assertTrue(ScopedConfigPublisher.render(List.of(entry("start"))).startsWith("#"),
                    "a file on a shared volume should say where it came from");
        }
    }

    @Nested
    class Publishing {

        @Test
        void writesTheContentAndCreatesTheDirectory() throws IOException {
            publisher.publish(scopeDir, List.of(entry("start,cpu")));

            assertEquals("start,cpu", ConfigFactory.parseString(published()).getString("asprof-settings"));
        }

        @Test
        void replacesContentThatChanged() throws IOException {
            publisher.publish(scopeDir, List.of(entry("start,cpu")));
            publisher.publish(scopeDir, List.of(entry("start,alloc")));

            assertEquals("start,alloc", ConfigFactory.parseString(published()).getString("asprof-settings"));
        }

        /**
         * The job republishes every scope on every tick, so an unchanged scope must not rewrite its
         * file: a rewrite would churn the volume and make every tick look like a change to anything
         * watching the file's timestamp.
         */
        @Test
        void leavesAnUnchangedFileAlone() throws IOException {
            publisher.publish(scopeDir, List.of(entry("start,cpu")));
            Files.setLastModifiedTime(configFile(), FileTime.fromMillis(0));

            publisher.publish(scopeDir, List.of(entry("start,cpu")));

            assertEquals(0, Files.getLastModifiedTime(configFile()).toMillis(),
                    "the file was rewritten even though nothing changed");
        }

        /**
         * The database is the truth without exception, so a file someone edited on the volume is
         * replaced by what the hub holds rather than believed.
         */
        @Test
        void overwritesAFileThatWasEditedByHand() throws IOException {
            publisher.publish(scopeDir, List.of(entry("start,cpu")));
            Files.writeString(configFile(), "asprof-settings = \"edited-by-hand\"\n");

            publisher.publish(scopeDir, List.of(entry("start,cpu")));

            assertEquals("start,cpu", ConfigFactory.parseString(published()).getString("asprof-settings"));
        }

        @Test
        void leavesNoTemporaryFileBehind() throws IOException {
            publisher.publish(scopeDir, List.of(entry("start,cpu")));

            assertEquals(List.of(ScopedConfigLayout.CONFIG_FILE), namesIn(configFile().getParent()));
        }

        /**
         * An editor's save and the synchronizer's tick can publish one scope at the same moment.
         * With a shared temporary name they would interleave their bytes into a file that is then
         * renamed into place, so each publish must write through a name of its own.
         */
        @Test
        void concurrentPublishesDoNotCorruptTheFile() throws Exception {
            List<ScopedConfigEntry> first = List.of(entry("a".repeat(200_000)));
            List<ScopedConfigEntry> second = List.of(entry("b".repeat(200_000)));

            Thread one = Thread.ofPlatform().start(() -> publisher.publish(scopeDir, first));
            Thread two = Thread.ofPlatform().start(() -> publisher.publish(scopeDir, second));
            one.join();
            two.join();

            String written = published();
            assertTrue(written.equals(ScopedConfigPublisher.render(first))
                            || written.equals(ScopedConfigPublisher.render(second)),
                    "the published file is neither of the two versions, so the writes interleaved");
        }
    }

    @Nested
    class Removing {

        @Test
        void aScopeHoldingNothingHasNoFile() {
            publisher.publish(scopeDir, List.of(entry("start,cpu")));

            publisher.publish(scopeDir, List.of());

            assertFalse(Files.exists(configFile()));
        }

        @Test
        void removingWhatIsNotThereSucceeds() {
            publisher.publish(scopeDir, List.of());

            assertFalse(Files.exists(configFile()));
        }
    }

    private static List<String> namesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }
}
