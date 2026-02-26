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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProfilerSettingsResolverTest {

    @Nested
    class DirectProfilerConfig {

        @TempDir
        Path tempDir;

        @Test
        void usesProvidedProfilerConfigWhenNotBlank() {
            Path workspacePath = tempDir.resolve("workspace");
            Path sessionPath = tempDir.resolve("session");
            String profilerConfig = "-agentpath:/custom/path=start,event=cpu";

            ProfilerSettingsResolver resolver = new ProfilerSettingsResolver();
            String result = resolver.resolve(
                    "/path/to/profiler",
                    profilerConfig,
                    workspacePath,
                    "my-project",
                    sessionPath,
                    ""
            );

            assertEquals(profilerConfig, result.trim());
        }

        @Test
        void replacesPlaceholdersInProvidedConfig() {
            Path workspacePath = tempDir.resolve("workspace");
            Path sessionPath = tempDir.resolve("session");
            String profilerConfig = "-agentpath:<<JEFFREY_PROFILER_PATH>>=start,file=<<JEFFREY_CURRENT_SESSION>>/output.jfr";

            ProfilerSettingsResolver resolver = new ProfilerSettingsResolver();
            String result = resolver.resolve(
                    "/custom/profiler.so",
                    profilerConfig,
                    workspacePath,
                    "my-project",
                    sessionPath,
                    ""
            );

            assertTrue(result.contains("/custom/profiler.so"));
            assertTrue(result.contains(sessionPath.toString()));
            assertFalse(result.contains("<<JEFFREY_PROFILER_PATH>>"));
            assertFalse(result.contains("<<JEFFREY_CURRENT_SESSION>>"));
        }

        @Test
        void appendsFeaturesAfterConfig() {
            Path workspacePath = tempDir.resolve("workspace");
            Path sessionPath = tempDir.resolve("session");
            String profilerConfig = "-agentpath:/custom/path=start";
            String features = "-XX:+UsePerfData -XX:+HeapDumpOnOutOfMemoryError";

            ProfilerSettingsResolver resolver = new ProfilerSettingsResolver();
            String result = resolver.resolve(
                    "/path/to/profiler",
                    profilerConfig,
                    workspacePath,
                    "my-project",
                    sessionPath,
                    features
            );

            assertTrue(result.startsWith("-agentpath:/custom/path=start"));
            assertTrue(result.endsWith(features));
        }

        @Test
        void ignoresBlankProfilerConfig() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path sessionPath = tempDir.resolve("session");

            ProfilerSettingsResolver resolver = new ProfilerSettingsResolver();
            String result = resolver.resolve(
                    "/path/to/profiler.so",
                    "   ",
                    workspacePath,
                    "my-project",
                    sessionPath,
                    ""
            );

            // Should fall back to default config
            assertTrue(result.contains("-agentpath:"));
            assertTrue(result.contains("/path/to/profiler.so"));
        }
    }

    @Nested
    class DefaultConfiguration {

        @TempDir
        Path tempDir;

        @Test
        void usesBuiltInDefaultWhenNoSettingsExist() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path sessionPath = tempDir.resolve("session");

            ProfilerSettingsResolver resolver = new ProfilerSettingsResolver();
            String result = resolver.resolve(
                    "/path/to/profiler.so",
                    null,
                    workspacePath,
                    "my-project",
                    sessionPath,
                    ""
            );

            // Default config should contain these elements
            assertTrue(result.contains("-agentpath:/path/to/profiler.so"));
            assertTrue(result.contains("start"));
            assertTrue(result.contains("alloc"));
            assertTrue(result.contains("lock"));
            assertTrue(result.contains("event=ctimer"));
            assertTrue(result.contains("jfrsync=default"));
            assertTrue(result.contains(sessionPath.toString()));
        }

        @Test
        void createsSettingsDirectoryIfNotExists() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path sessionPath = tempDir.resolve("session");
            Path settingsDir = workspacePath.resolve(".settings");

            assertFalse(Files.exists(settingsDir));

            ProfilerSettingsResolver resolver = new ProfilerSettingsResolver();
            resolver.resolve(
                    "/path/to/profiler.so",
                    null,
                    workspacePath,
                    "my-project",
                    sessionPath,
                    ""
            );

            assertTrue(Files.exists(settingsDir));
            assertTrue(Files.isDirectory(settingsDir));
        }

        @Test
        void handlesNullProfilerPath() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path sessionPath = tempDir.resolve("session");

            ProfilerSettingsResolver resolver = new ProfilerSettingsResolver();
            String result = resolver.resolve(
                    null,
                    null,
                    workspacePath,
                    "my-project",
                    sessionPath,
                    ""
            );

            // Profiler path placeholder should be replaced with empty string
            assertTrue(result.contains("-agentpath:="));
        }
    }

    @Nested
    class WorkspaceSettings {

        @TempDir
        Path tempDir;

        @Test
        void usesDefaultSettingsFromFile() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path settingsDir = Files.createDirectories(workspacePath.resolve(".settings"));
            Path sessionPath = tempDir.resolve("session");

            String settingsJson = """
                    {
                        "profiler": {
                            "defaultSettings": "-agentpath:<<JEFFREY_PROFILER_PATH>>=start,event=wall,file=<<JEFFREY_CURRENT_SESSION>>/profile.jfr",
                            "projectSettings": {}
                        }
                    }
                    """;
            Files.writeString(settingsDir.resolve("settings-2025-01-15T120000000000.json"), settingsJson);

            ProfilerSettingsResolver resolver = new ProfilerSettingsResolver();
            String result = resolver.resolve(
                    "/my/profiler.so",
                    null,
                    workspacePath,
                    "unknown-project",
                    sessionPath,
                    ""
            );

            assertTrue(result.contains("-agentpath:/my/profiler.so"));
            assertTrue(result.contains("event=wall"));
            assertTrue(result.contains(sessionPath.toString() + "/profile.jfr"));
        }

        @Test
        void usesProjectSpecificSettings() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path settingsDir = Files.createDirectories(workspacePath.resolve(".settings"));
            Path sessionPath = tempDir.resolve("session");

            String settingsJson = """
                    {
                        "profiler": {
                            "defaultSettings": "-agentpath:<<JEFFREY_PROFILER_PATH>>=start,event=wall",
                            "projectSettings": {
                                "my-project": "-agentpath:<<JEFFREY_PROFILER_PATH>>=start,event=cpu,alloc"
                            }
                        }
                    }
                    """;
            Files.writeString(settingsDir.resolve("settings-2025-01-15T120000000000.json"), settingsJson);

            ProfilerSettingsResolver resolver = new ProfilerSettingsResolver();
            String result = resolver.resolve(
                    "/my/profiler.so",
                    null,
                    workspacePath,
                    "my-project",
                    sessionPath,
                    ""
            );

            // Should use project-specific settings, not default
            assertTrue(result.contains("event=cpu"));
            assertTrue(result.contains("alloc"));
            assertFalse(result.contains("event=wall"));
        }

        @Test
        void usesSingleSettingsFile() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path settingsDir = Files.createDirectories(workspacePath.resolve(".settings"));
            Path sessionPath = tempDir.resolve("session");

            String settingsJson = """
                    {
                        "profiler": {
                            "defaultSettings": "-agentpath:<<JEFFREY_PROFILER_PATH>>=custom-config",
                            "projectSettings": {}
                        }
                    }
                    """;
            Files.writeString(settingsDir.resolve("settings-2025-01-15T120000000000.json"), settingsJson);

            ProfilerSettingsResolver resolver = new ProfilerSettingsResolver();
            String result = resolver.resolve(
                    "/profiler.so",
                    null,
                    workspacePath,
                    "my-project",
                    sessionPath,
                    ""
            );

            assertTrue(result.contains("custom-config"));
        }

        @Test
        void ignoresNonSettingsFiles() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path settingsDir = Files.createDirectories(workspacePath.resolve(".settings"));
            Path sessionPath = tempDir.resolve("session");

            // Create non-settings files
            Files.writeString(settingsDir.resolve("other-file.json"), "{}");
            Files.writeString(settingsDir.resolve("settings.txt"), "text file");

            ProfilerSettingsResolver resolver = new ProfilerSettingsResolver();
            String result = resolver.resolve(
                    "/profiler.so",
                    null,
                    workspacePath,
                    "my-project",
                    sessionPath,
                    ""
            );

            // Should use built-in default since no valid settings files
            assertTrue(result.contains("jfrsync=default"));
        }
    }

    @Nested
    class PlaceholderReplacement {

        @TempDir
        Path tempDir;

        @Test
        void replacesProfilerPathPlaceholder() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path sessionPath = tempDir.resolve("session");

            ProfilerSettingsResolver resolver = new ProfilerSettingsResolver();
            String result = resolver.resolve(
                    "/custom/async-profiler/libasyncProfiler.so",
                    null,
                    workspacePath,
                    "my-project",
                    sessionPath,
                    ""
            );

            assertTrue(result.contains("/custom/async-profiler/libasyncProfiler.so"));
            assertFalse(result.contains("<<JEFFREY_PROFILER_PATH>>"));
        }

        @Test
        void replacesCurrentSessionPlaceholder() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path sessionPath = tempDir.resolve("sessions/session-abc123");

            ProfilerSettingsResolver resolver = new ProfilerSettingsResolver();
            String result = resolver.resolve(
                    "/profiler.so",
                    null,
                    workspacePath,
                    "my-project",
                    sessionPath,
                    ""
            );

            assertTrue(result.contains(sessionPath.toString()));
            assertFalse(result.contains("<<JEFFREY_CURRENT_SESSION>>"));
        }
    }

    @Nested
    class FeaturesAppending {

        @TempDir
        Path tempDir;

        @Test
        void appendsEmptyFeatures() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path sessionPath = tempDir.resolve("session");

            ProfilerSettingsResolver resolver = new ProfilerSettingsResolver();
            String result = resolver.resolve(
                    "/profiler.so",
                    "-agentpath:/profiler.so=start",
                    workspacePath,
                    "my-project",
                    sessionPath,
                    ""
            );

            assertEquals("-agentpath:/profiler.so=start", result.trim());
        }

        @Test
        void appendsMultipleFeatures() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path sessionPath = tempDir.resolve("session");
            String features = "-XX:+UsePerfData -XX:+HeapDumpOnOutOfMemoryError -Xlog:jfr*=trace";

            ProfilerSettingsResolver resolver = new ProfilerSettingsResolver();
            String result = resolver.resolve(
                    "/profiler.so",
                    "-agentpath:/profiler.so=start",
                    workspacePath,
                    "my-project",
                    sessionPath,
                    features
            );

            assertTrue(result.contains("-agentpath:/profiler.so=start"));
            assertTrue(result.contains("-XX:+UsePerfData"));
            assertTrue(result.contains("-XX:+HeapDumpOnOutOfMemoryError"));
            assertTrue(result.contains("-Xlog:jfr*=trace"));
        }
    }
}
