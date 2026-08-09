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

package cafe.jeffrey.provisioner.placeholder;

import cafe.jeffrey.provisioner.EnvFileBuilder;
import cafe.jeffrey.shared.common.CliConstants;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceholdersTest {

    private static Placeholders env(Map<String, String> variables) {
        return Placeholders.of(new EnvPlaceholderSource(variables::get));
    }

    @Nested
    class EnvResolution {

        @Test
        void substitutesEveryVariableInOneValue() {
            Placeholders placeholders = env(Map.of("SF_CLUSTER", "blue", "SF_ENV", "uat"));

            assertEquals(
                    "cluster=blue,env=uat,namespace=test-namespace",
                    placeholders.resolve("cluster=<<ENV:SF_CLUSTER>>,env=<<ENV:SF_ENV>>,namespace=test-namespace"));
        }

        @Test
        void missingVariableBecomesEmptyRatherThanFailing() {
            assertEquals("cluster=", env(Map.of()).resolve("cluster=<<ENV:SF_CLUSTER>>"));
        }

        @Test
        void fallsBackToTheDeclaredDefault() {
            assertEquals("cluster=unknown", env(Map.of()).resolve("cluster=<<ENV:SF_CLUSTER:-unknown>>"));
        }

        @Test
        void prefersTheVariableOverTheDefault() {
            assertEquals("cluster=blue",
                    env(Map.of("SF_CLUSTER", "blue")).resolve("cluster=<<ENV:SF_CLUSTER:-unknown>>"));
        }

        @Test
        void treatsAnEmptyVariableAsUnset() {
            assertEquals("cluster=unknown",
                    env(Map.of("SF_CLUSTER", "  ")).resolve("cluster=<<ENV:SF_CLUSTER:-unknown>>"));
        }

        @Test
        void leavesAValueWithoutPlaceholdersAlone() {
            assertEquals("cluster=blue", env(Map.of()).resolve("cluster=blue"));
        }
    }

    @Nested
    class PhaseIsolation {

        @Test
        void leavesAnUnknownTypeCompletelyUntouched() {
            // The whole point: the config phase runs before the session directory exists, so
            // <<JEFFREY:...>> has to survive it byte-for-byte.
            Placeholders placeholders = env(Map.of("SF_ENV", "uat"));

            assertEquals(
                    "-Dlog=<<JEFFREY:CURRENT_SESSION>>/app-uat.log",
                    placeholders.resolve("-Dlog=<<JEFFREY:CURRENT_SESSION>>/app-<<ENV:SF_ENV>>.log"));
        }


        @Test
        void doesNotRescanASubstitutedValue() {
            // Content arriving from the environment must never be able to inject a placeholder.
            Placeholders placeholders = env(Map.of("INJECTED", "<<ENV:SECRET>>", "SECRET", "leaked"));

            assertEquals("<<ENV:SECRET>>", placeholders.resolve("<<ENV:INJECTED>>"));
        }
    }

    @Nested
    class JeffreyResolution {

        private static final Path SESSION = Path.of("/workspaces/ws/proj/inst/session-1");

        private static Placeholders full() {
            return Placeholders.of(JeffreyPlaceholderSource.of(
                    new JeffreyPlaceholderSource.Layout(
                            Path.of("/mnt/jeffrey"),
                            Path.of("/workspaces"),
                            Path.of("/workspaces/ws"),
                            Path.of("/workspaces/ws/proj"),
                            SESSION,
                            "/libs/libasyncProfiler-amd64.so"),
                    EnvFileBuilder.DEFAULT_FILE_TEMPLATE));
        }

        @Test
        void resolvesEveryExportedName() {
            Placeholders placeholders = full();

            assertEquals("/mnt/jeffrey", placeholders.resolve("<<JEFFREY:HOME>>"));
            assertEquals("/workspaces", placeholders.resolve("<<JEFFREY:WORKSPACES>>"));
            assertEquals("/workspaces/ws", placeholders.resolve("<<JEFFREY:CURRENT_WORKSPACE>>"));
            assertEquals("/workspaces/ws/proj", placeholders.resolve("<<JEFFREY:CURRENT_PROJECT>>"));
            assertEquals(SESSION.toString(), placeholders.resolve("<<JEFFREY:CURRENT_SESSION>>"));
            assertEquals(SESSION.resolve("profile-%t.jfr").toString(),
                    placeholders.resolve("<<JEFFREY:FILE_PATTERN>>"));
            assertEquals("/libs/libasyncProfiler-amd64.so", placeholders.resolve("<<JEFFREY:PROFILER_PATH>>"));
        }

        /** The tokens the rest of the codebase builds its commands from must resolve. */
        @Test
        void resolvesTheSharedConstants() {
            assertEquals(
                    SESSION + "/profile-%t.jfr",
                    full().resolve(CliConstants.CURRENT_SESSION + "/profile-%t.jfr"));
            assertEquals("/libs/libasyncProfiler-amd64.so", full().resolve(CliConstants.PROFILER_PATH));
        }

        @Test
        void leavesAnUnboundNameEmpty() {
            // jeffrey-home is null when the run was configured with an explicit workspaces dir.
            Placeholders placeholders = Placeholders.of(JeffreyPlaceholderSource.ofSession(SESSION));

            assertEquals("", placeholders.resolve("<<JEFFREY:HOME>>"));
        }
    }

}
