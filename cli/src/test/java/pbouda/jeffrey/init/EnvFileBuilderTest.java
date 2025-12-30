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

package pbouda.jeffrey.init;

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

    @Nested
    class RequiredExports {

        @Test
        void includesWorkspacesExport() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    null, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    null, false, false
            );

            String result = builder.build(context);

            assertTrue(result.contains("export JEFFREY_WORKSPACES=" + WORKSPACES_PATH));
        }

        @Test
        void includesWorkspaceExport() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    null, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    null, false, false
            );

            String result = builder.build(context);

            assertTrue(result.contains("export JEFFREY_CURRENT_WORKSPACE=" + WORKSPACE_PATH));
        }

        @Test
        void includesProjectExport() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    null, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    null, false, false
            );

            String result = builder.build(context);

            assertTrue(result.contains("export JEFFREY_CURRENT_PROJECT=" + PROJECT_PATH));
        }

        @Test
        void includesSessionExport() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    null, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    null, false, false
            );

            String result = builder.build(context);

            assertTrue(result.contains("export JEFFREY_CURRENT_SESSION=" + SESSION_PATH));
        }

        @Test
        void includesFilePatternExport() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    null, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    null, false, false
            );

            String result = builder.build(context);

            assertTrue(result.contains("export JEFFREY_FILE_PATTERN=" + SESSION_PATH + "/profile-%t.jfr"));
        }
    }

    @Nested
    class JeffreyHomeExport {

        @Test
        void includesJeffreyHomeWhenUseJeffreyHomeIsTrue() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    JEFFREY_HOME, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    null, true, false
            );

            String result = builder.build(context);

            assertTrue(result.contains("export JEFFREY_HOME=" + JEFFREY_HOME));
        }

        @Test
        void excludesJeffreyHomeWhenUseJeffreyHomeIsFalse() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    JEFFREY_HOME, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    null, false, false
            );

            String result = builder.build(context);

            assertFalse(result.contains("JEFFREY_HOME"));
        }

        @Test
        void jeffreyHomeIsFirstExportWhenPresent() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    JEFFREY_HOME, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    null, true, false
            );

            String result = builder.build(context);

            assertTrue(result.startsWith("export JEFFREY_HOME="));
        }
    }

    @Nested
    class ProfilerConfigExport {

        @Test
        void includesProfilerConfigWhenSettingsNotNull() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    null, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    PROFILER_SETTINGS, false, false
            );

            String result = builder.build(context);

            assertTrue(result.contains("export JEFFREY_PROFILER_CONFIG="));
            assertTrue(result.contains(PROFILER_SETTINGS));
        }

        @Test
        void excludesProfilerConfigWhenSettingsNull() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    null, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    null, false, false
            );

            String result = builder.build(context);

            assertFalse(result.contains("JEFFREY_PROFILER_CONFIG"));
        }

        @Test
        void excludesProfilerConfigWhenSettingsEmpty() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    null, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    "", false, false
            );

            String result = builder.build(context);

            assertFalse(result.contains("JEFFREY_PROFILER_CONFIG"));
        }

        @Test
        void wrapsProfilerSettingsInSingleQuotes() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    null, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    PROFILER_SETTINGS, false, false
            );

            String result = builder.build(context);

            assertTrue(result.contains("='" + PROFILER_SETTINGS + "'"));
        }
    }

    @Nested
    class JdkJavaOptionsExport {

        @Test
        void includesJdkJavaOptionsWhenExportEnabledAndProfilerSettingsPresent() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    null, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    PROFILER_SETTINGS, false, true
            );

            String result = builder.build(context);

            assertTrue(result.contains("export JDK_JAVA_OPTIONS="));
            assertTrue(result.contains("'" + PROFILER_SETTINGS + "'"));
        }

        @Test
        void excludesJdkJavaOptionsWhenExportDisabled() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    null, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    PROFILER_SETTINGS, false, false
            );

            String result = builder.build(context);

            assertFalse(result.contains("JDK_JAVA_OPTIONS"));
        }

        @Test
        void excludesJdkJavaOptionsWhenProfilerSettingsNull() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    null, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    null, false, true
            );

            String result = builder.build(context);

            assertFalse(result.contains("JDK_JAVA_OPTIONS"));
        }

        @Test
        void jdkJavaOptionsIsLastExport() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    null, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    PROFILER_SETTINGS, false, true
            );

            String result = builder.build(context);

            // JDK_JAVA_OPTIONS should be at the end without newline
            assertTrue(result.endsWith("'" + PROFILER_SETTINGS + "'"));
        }
    }

    @Nested
    class OutputFormat {

        @Test
        void eachExportOnSeparateLine() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    JEFFREY_HOME, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    null, true, false
            );

            String result = builder.build(context);
            String[] lines = result.split("\n");

            // JEFFREY_HOME, WORKSPACES, WORKSPACE, PROJECT, SESSION, FILE_PATTERN = 6 exports
            assertEquals(6, lines.length);
            for (String line : lines) {
                assertTrue(line.startsWith("export "));
            }
        }

        @Test
        void fullOutputWithAllOptions() {
            EnvFileBuilder.Context context = new EnvFileBuilder.Context(
                    JEFFREY_HOME, WORKSPACES_PATH, WORKSPACE_PATH, PROJECT_PATH, SESSION_PATH,
                    PROFILER_SETTINGS, true, true
            );

            String result = builder.build(context);

            // Verify order of exports
            int homeIdx = result.indexOf("JEFFREY_HOME");
            int workspacesIdx = result.indexOf("JEFFREY_WORKSPACES");
            int workspaceIdx = result.indexOf("JEFFREY_CURRENT_WORKSPACE");
            int projectIdx = result.indexOf("JEFFREY_CURRENT_PROJECT");
            int sessionIdx = result.indexOf("JEFFREY_CURRENT_SESSION");
            int patternIdx = result.indexOf("JEFFREY_FILE_PATTERN");
            int profilerIdx = result.indexOf("JEFFREY_PROFILER_CONFIG");
            int jdkIdx = result.indexOf("JDK_JAVA_OPTIONS");

            assertTrue(homeIdx < workspacesIdx);
            assertTrue(workspacesIdx < workspaceIdx);
            assertTrue(workspaceIdx < projectIdx);
            assertTrue(projectIdx < sessionIdx);
            assertTrue(sessionIdx < patternIdx);
            assertTrue(patternIdx < profilerIdx);
            assertTrue(profilerIdx < jdkIdx);
        }
    }
}
