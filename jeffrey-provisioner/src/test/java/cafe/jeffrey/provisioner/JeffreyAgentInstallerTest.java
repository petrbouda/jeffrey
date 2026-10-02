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
package cafe.jeffrey.provisioner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeffreyAgentInstallerTest {

    @TempDir
    Path tempDir;

    private Path session() throws IOException {
        return Files.createDirectories(tempDir.resolve("session"));
    }

    @Nested
    class Bundled {

        @Test
        void writesTheAgentIntoTheSessionAsAHiddenFile() throws IOException {
            Path session = session();

            Optional<Path> agent = BundledAgentFixture.bundledUnder(tempDir.resolve("classes")).install(session);

            assertEquals(Optional.of(session.resolve(".jeffrey-agent.jar")), agent);
            assertArrayEquals(BundledAgentFixture.CONTENT, Files.readAllBytes(agent.orElseThrow()));
        }

        @Test
        void leavesNoScratchFileBehind() throws IOException {
            Path session = session();

            BundledAgentFixture.bundledUnder(tempDir.resolve("classes")).install(session);

            assertFalse(Files.exists(session.resolve(".jeffrey-agent.jar.tmp")));
        }
    }

    @Nested
    class FailsOpen {

        @Test
        void withoutABundledAgent() throws IOException {
            Path session = session();

            Optional<Path> agent = BundledAgentFixture.missingUnder(tempDir.resolve("classes")).install(session);

            assertTrue(agent.isEmpty());
            assertFalse(Files.exists(session.resolve(".jeffrey-agent.jar")));
        }

        @Test
        void whenTheSessionRefusesTheFile() {
            Path missingSession = tempDir.resolve("no-such-session");

            Optional<Path> agent = BundledAgentFixture.bundledUnder(tempDir.resolve("classes")).install(missingSession);

            assertTrue(agent.isEmpty());
        }
    }
}
