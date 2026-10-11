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

import cafe.jeffrey.provisioner.feature.JvmFeature;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JeffreyAgentResolverTest {

    private static final String AGENT_PATH = "/opt/jeffrey/jeffrey-agent.jar";

    private static final JeffreyAgentResolver AGENT_PRESENT = new JeffreyAgentResolver(path -> true);
    private static final JeffreyAgentResolver AGENT_MISSING = new JeffreyAgentResolver(path -> false);

    @Nested
    class Enabled {

        @Test
        void attachesTheJarTheAgentPathNames() {
            JvmFeature.JeffreyAgent agent = AGENT_PRESENT.resolve(true, AGENT_PATH);

            assertEquals(new JvmFeature.JeffreyAgent(Path.of(AGENT_PATH)), agent);
        }

        /** A -javaagent the JVM cannot load would stop the application, so the session runs without it. */
        @Test
        void runsWithoutTheAgentWhenTheJarIsMissing() {
            assertFalse(AGENT_MISSING.resolve(true, AGENT_PATH).enabled());
        }

        @Test
        void runsWithoutTheAgentWhenNoPathIsConfigured() {
            assertFalse(AGENT_PRESENT.resolve(true, null).enabled());
        }

        @Test
        void treatsABlankPathAsUnset() {
            assertFalse(AGENT_PRESENT.resolve(true, "   ").enabled());
        }
    }

    @Nested
    class Disabled {

        /** Switched off, the jar is not even looked for: liveness is left to jeffrey-heartbeat. */
        @Test
        void leavesTheAgentOutEvenWhenTheJarIsThere() {
            assertFalse(AGENT_PRESENT.resolve(false, AGENT_PATH).enabled());
        }
    }

    @Nested
    class AgainstTheFilesystem {

        @TempDir
        Path tempDir;

        @Test
        void attachesARegularFile() throws IOException {
            Path jar = Files.createFile(tempDir.resolve("jeffrey-agent.jar"));

            assertEquals(new JvmFeature.JeffreyAgent(jar), new JeffreyAgentResolver().resolve(true, jar.toString()));
        }

        @Test
        void rejectsADirectory() {
            assertFalse(new JeffreyAgentResolver().resolve(true, tempDir.toString()).enabled());
        }
    }
}
