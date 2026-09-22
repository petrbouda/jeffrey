/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.provisioner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EnvFileBuilderTest {

    private static final Path JEFFREY_HOME = Path.of("/tmp/jeffrey");
    private static final Path WORKSPACES_PATH = Path.of("/tmp/jeffrey/workspaces");
    private static final Path WORKSPACE_PATH = Path.of("/tmp/jeffrey/workspaces/uat");
    private static final Path PROJECT_PATH = Path.of("/tmp/jeffrey/workspaces/uat/my-project");
    private static final Path SESSION_PATH = Path.of("/tmp/jeffrey/workspaces/uat/my-project/session-123");
    private static final String PROFILER_SETTINGS = "-agentpath:/profiler.so=start,event=cpu";

    private final EnvFileBuilder builder = new EnvFileBuilder();

    private static SessionLayout layout(Path jeffreyHome) {
        return new SessionLayout(jeffreyHome, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH);
    }

    @Nested
    class Heartbeat {

        @Test
        void namesTheDirectoryInsideTheSession() {
            // Named outright so the jeffrey-heartbeat library needs no opinion about where inside
            // a session directory the liveness files belong
            String result = builder.build(new EnvFileBuilder.Context(layout(null), null, false, false));

            assertTrue(result.contains("export JEFFREY_HEARTBEAT_DIR=" + SESSION_PATH.resolve(".heartbeat")),
                    result);
        }

        @Test
        void exportsWhatTheSessionDeclared() {
            String result = builder.build(new EnvFileBuilder.Context(layout(null), null, false, true));

            assertTrue(result.contains("export JEFFREY_HEARTBEAT_ENABLED=true"), result);
        }

        @Test
        void exportsFalseForAnApplicationThatWillNotReport() {
            // An application without the jeffrey-heartbeat dependency. The library reads this and
            // stands down; the same value in the session marker stops the hub holding the session
            // to a deadline it could never meet.
            String result = builder.build(new EnvFileBuilder.Context(layout(null), null, false, false));

            assertTrue(result.contains("export JEFFREY_HEARTBEAT_ENABLED=false"), result);
        }
    }

    @Nested
    class RequiredExports {

        @Test
        void includesWorkspacesExport() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(null), null, false, false);

            String result = builder.build(context);

            assertTrue(result.contains("export JEFFREY_WORKSPACES=" + WORKSPACES_PATH));
        }

        @Test
        void includesWorkspaceExport() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(null), null, false, false);

            String result = builder.build(context);

            assertTrue(result.contains("export JEFFREY_CURRENT_WORKSPACE=" + WORKSPACE_PATH));
        }

        @Test
        void includesProjectExport() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(null), null, false, false);

            String result = builder.build(context);

            assertTrue(result.contains("export JEFFREY_CURRENT_PROJECT=" + PROJECT_PATH));
        }

        @Test
        void includesSessionExport() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(null), null, false, false);

            String result = builder.build(context);

            assertTrue(result.contains("export JEFFREY_CURRENT_SESSION=" + SESSION_PATH));
        }

        @Test
        void includesFilePatternExport() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(null), null, false, false);

            String result = builder.build(context);

            assertTrue(result.contains("export JEFFREY_FILE_PATTERN=" + SESSION_PATH + "/profile-%t.jfr"));
        }
    }

    @Nested
    class JeffreyHomeExport {

        @Test
        void includesJeffreyHomeWhenTheRunHasOne() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(JEFFREY_HOME), null, false, false);

            String result = builder.build(context);

            assertTrue(result.contains("export JEFFREY_HOME=" + JEFFREY_HOME));
        }

        /**
         * The export follows the path, and there is no longer a separate flag that could disagree
         * with it: a run configured with an explicit workspaces directory has no Jeffrey home.
         */
        @Test
        void excludesJeffreyHomeWhenTheRunHasNone() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(null), null, false, false);

            String result = builder.build(context);

            assertFalse(result.contains("JEFFREY_HOME"));
        }

        @Test
        void jeffreyHomeIsFirstExportWhenPresent() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(JEFFREY_HOME), null, false, false);

            String result = builder.build(context);

            assertTrue(result.startsWith("export JEFFREY_HOME="));
        }
    }

    @Nested
    class ProfilerConfigExport {

        /**
         * The resolved command reaches the JVM through the argfile, or through
         * {@code JDK_JAVA_OPTIONS} when that export is asked for. The {@code .env} file carries the
         * session layout only, so it never holds a second copy of the command.
         */
        @Test
        void doesNotCarryTheCommandWhenJdkJavaOptionsIsOff() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(null), PROFILER_SETTINGS, false, false);

            String result = builder.build(context);

            assertFalse(result.contains(PROFILER_SETTINGS));
        }

        @Test
        void wrapsProfilerSettingsInSingleQuotes() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(null), PROFILER_SETTINGS, true, false);

            String result = builder.build(context);

            assertTrue(result.contains("='" + PROFILER_SETTINGS + "'"));
        }
    }

    @Nested
    class JdkJavaOptionsExport {

        @Test
        void includesJdkJavaOptionsWhenExportEnabledAndProfilerSettingsPresent() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(null), PROFILER_SETTINGS, true, false);

            String result = builder.build(context);

            assertTrue(result.contains("export JDK_JAVA_OPTIONS="));
            assertTrue(result.contains("'" + PROFILER_SETTINGS + "'"));
        }

        @Test
        void excludesJdkJavaOptionsWhenExportDisabled() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(null), PROFILER_SETTINGS, false, false);

            String result = builder.build(context);

            assertFalse(result.contains("JDK_JAVA_OPTIONS"));
        }

        @Test
        void excludesJdkJavaOptionsWhenProfilerSettingsNull() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(null), null, true, false);

            String result = builder.build(context);

            assertFalse(result.contains("JDK_JAVA_OPTIONS"));
        }

        @Test
        void jdkJavaOptionsIsLastExport() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(null), PROFILER_SETTINGS, true, false);

            String result = builder.build(context);

            assertEquals("export JDK_JAVA_OPTIONS='" + PROFILER_SETTINGS + "'",
                    result.lines().reduce((first, second) -> second).orElseThrow());
            assertTrue(result.endsWith("\n"), "the file is newline-terminated like any other");
        }
    }

    @Nested
    class OutputFormat {

        @Test
        void eachExportOnSeparateLine() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(JEFFREY_HOME), null, false, false);

            String result = builder.build(context);
            String[] lines = result.split("\n");

            // JEFFREY_HOME, WORKSPACES, WORKSPACE, PROJECT, SESSION, FILE_PATTERN,
            // HEARTBEAT_DIR, HEARTBEAT_ENABLED = 8 exports
            assertEquals(8, lines.length);
            for (String line : lines) {
                assertTrue(line.startsWith("export "));
            }
        }

        @Test
        void fullOutputWithAllOptions() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(layout(JEFFREY_HOME), PROFILER_SETTINGS, true, false);

            String result = builder.build(context);

            // Verify order of exports
            int homeIdx = result.indexOf("JEFFREY_HOME");
            int workspacesIdx = result.indexOf("JEFFREY_WORKSPACES");
            int workspaceIdx = result.indexOf("JEFFREY_CURRENT_WORKSPACE");
            int projectIdx = result.indexOf("JEFFREY_CURRENT_PROJECT");
            int sessionIdx = result.indexOf("JEFFREY_CURRENT_SESSION");
            int patternIdx = result.indexOf("JEFFREY_FILE_PATTERN");
            int jdkIdx = result.indexOf("JDK_JAVA_OPTIONS");

            assertTrue(homeIdx < workspacesIdx);
            assertTrue(workspacesIdx < workspaceIdx);
            assertTrue(workspaceIdx < projectIdx);
            assertTrue(projectIdx < sessionIdx);
            assertTrue(sessionIdx < patternIdx);
            assertTrue(patternIdx < jdkIdx);
        }
    }
}
