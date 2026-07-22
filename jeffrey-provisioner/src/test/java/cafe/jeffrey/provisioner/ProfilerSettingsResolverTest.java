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
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.provisioner.ProfilerSettingsResolver.ResolvedProfilerSettings;
import cafe.jeffrey.shared.common.model.repository.ProfilerSettingsSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProfilerSettingsResolverTest {

    private static final String PROJECT_ID = "proj-id-1";

    private static ResolvedProfilerSettings resolve(
            String profilerPath,
            String profilerConfig,
            Path workspacePath,
            String projectName,
            Path sessionPath,
            String features) {

        return new ProfilerSettingsResolver().resolve(
                profilerPath, profilerConfig, workspacePath, PROJECT_ID, projectName, sessionPath, features);
    }

    @Nested
    class DirectProfilerConfig {

        @TempDir
        Path tempDir;

        @Test
        void usesProvidedProfilerConfigWhenNotBlank() {
            Path workspacePath = tempDir.resolve("workspace");
            Path sessionPath = tempDir.resolve("session");
            String profilerConfig = "-agentpath:/custom/path=start,event=cpu";

            ResolvedProfilerSettings resolved = resolve(
                    "/path/to/profiler", profilerConfig, workspacePath, "my-project", sessionPath, "");

            assertEquals(profilerConfig, resolved.command().trim());
            assertEquals(ProfilerSettingsSource.CLI_CONFIG, resolved.source());
            assertNull(resolved.sourceDetail());
        }

        @Test
        void replacesPlaceholdersInProvidedConfig() {
            Path workspacePath = tempDir.resolve("workspace");
            Path sessionPath = tempDir.resolve("session");
            String profilerConfig = "-agentpath:<<JEFFREY_PROFILER_PATH>>=start,file=<<JEFFREY_CURRENT_SESSION>>/output.jfr";

            String result = resolve(
                    "/custom/profiler.so", profilerConfig, workspacePath, "my-project", sessionPath, "").command();

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

            String result = resolve(
                    "/path/to/profiler", profilerConfig, workspacePath, "my-project", sessionPath, features).command();

            assertTrue(result.startsWith("-agentpath:/custom/path=start"));
            assertTrue(result.endsWith(features));
        }

        @Test
        void ignoresBlankProfilerConfig() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path sessionPath = tempDir.resolve("session");

            ResolvedProfilerSettings resolved = resolve(
                    "/path/to/profiler.so", "   ", workspacePath, "my-project", sessionPath, "");

            // Should fall back to default config
            assertTrue(resolved.command().contains("-agentpath:"));
            assertTrue(resolved.command().contains("/path/to/profiler.so"));
            assertEquals(ProfilerSettingsSource.BUILT_IN, resolved.source());
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

            ResolvedProfilerSettings resolved = resolve(
                    "/path/to/profiler.so", null, workspacePath, "my-project", sessionPath, "");
            String result = resolved.command();

            // Default config should contain these elements
            assertTrue(result.contains("-agentpath:/path/to/profiler.so"));
            assertTrue(result.contains("start"));
            assertTrue(result.contains("alloc"));
            assertTrue(result.contains("lock"));
            assertTrue(result.contains("event=ctimer"));
            assertTrue(result.contains("jfrsync=default"));
            assertTrue(result.contains(sessionPath.toString()));
            assertEquals(ProfilerSettingsSource.BUILT_IN, resolved.source());
            assertNull(resolved.sourceDetail());
        }

        @Test
        void createsSettingsDirectoryIfNotExists() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path sessionPath = tempDir.resolve("session");
            Path settingsDir = workspacePath.resolve(".settings");

            assertFalse(Files.exists(settingsDir));

            resolve("/path/to/profiler.so", null, workspacePath, "my-project", sessionPath, "");

            assertTrue(Files.exists(settingsDir));
            assertTrue(Files.isDirectory(settingsDir));
        }

        @Test
        void handlesNullProfilerPath() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path sessionPath = tempDir.resolve("session");

            String result = resolve(null, null, workspacePath, "my-project", sessionPath, "").command();

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
                            "defaultSettingsLevel": "WORKSPACE",
                            "projectSettings": {}
                        }
                    }
                    """;
            Files.writeString(settingsDir.resolve("settings-2025-01-15T120000000000.json"), settingsJson);

            ResolvedProfilerSettings resolved = resolve(
                    "/my/profiler.so", null, workspacePath, "unknown-project", sessionPath, "");
            String result = resolved.command();

            assertTrue(result.contains("-agentpath:/my/profiler.so"));
            assertTrue(result.contains("event=wall"));
            assertTrue(result.contains(sessionPath.toString() + "/profile.jfr"));
            assertEquals(ProfilerSettingsSource.HUB_WORKSPACE, resolved.source());
            assertEquals("settings-2025-01-15T120000000000.json", resolved.sourceDetail());
        }

        @Test
        void globalLevelDefaultSettings_reportedAsHubGlobal() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path settingsDir = Files.createDirectories(workspacePath.resolve(".settings"));
            Path sessionPath = tempDir.resolve("session");

            String settingsJson = """
                    {
                        "profiler": {
                            "defaultSettings": "-agentpath:<<JEFFREY_PROFILER_PATH>>=start,event=wall",
                            "defaultSettingsLevel": "GLOBAL",
                            "projectSettings": {}
                        }
                    }
                    """;
            Files.writeString(settingsDir.resolve("settings-2025-01-15T120000000000.json"), settingsJson);

            ResolvedProfilerSettings resolved = resolve(
                    "/my/profiler.so", null, workspacePath, "unknown-project", sessionPath, "");

            assertEquals(ProfilerSettingsSource.HUB_GLOBAL, resolved.source());
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
                            "defaultSettingsLevel": "GLOBAL",
                            "projectSettings": {
                                "my-project": "-agentpath:<<JEFFREY_PROFILER_PATH>>=start,event=cpu,alloc"
                            }
                        }
                    }
                    """;
            Files.writeString(settingsDir.resolve("settings-2025-01-15T120000000000.json"), settingsJson);

            ResolvedProfilerSettings resolved = resolve(
                    "/my/profiler.so", null, workspacePath, "my-project", sessionPath, "");
            String result = resolved.command();

            // Should use project-specific settings, not default
            assertTrue(result.contains("event=cpu"));
            assertTrue(result.contains("alloc"));
            assertFalse(result.contains("event=wall"));
            assertEquals(ProfilerSettingsSource.HUB_PROJECT, resolved.source());
        }

        @Test
        void prefersIdKeyedSettings_overNameKeyed() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path settingsDir = Files.createDirectories(workspacePath.resolve(".settings"));
            Path sessionPath = tempDir.resolve("session");

            String settingsJson = """
                    {
                        "profiler": {
                            "defaultSettings": "-agentpath:<<JEFFREY_PROFILER_PATH>>=start,event=wall",
                            "defaultSettingsLevel": "GLOBAL",
                            "projectSettings": {
                                "my-project": "-agentpath:<<JEFFREY_PROFILER_PATH>>=start,event=cpu"
                            },
                            "projectSettingsById": {
                                "proj-id-1": "-agentpath:<<JEFFREY_PROFILER_PATH>>=start,event=itimer"
                            }
                        }
                    }
                    """;
            Files.writeString(settingsDir.resolve("settings-2025-01-15T120000000000.json"), settingsJson);

            ResolvedProfilerSettings resolved = resolve(
                    "/my/profiler.so", null, workspacePath, "my-project", sessionPath, "");

            assertTrue(resolved.command().contains("event=itimer"),
                    "The id-keyed entry must win over the name-keyed one");
            assertEquals(ProfilerSettingsSource.HUB_PROJECT, resolved.source());
        }

        @Test
        void fallsBackToNameKeyedSettings_whenIdMapMissing() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path settingsDir = Files.createDirectories(workspacePath.resolve(".settings"));
            Path sessionPath = tempDir.resolve("session");

            // Old-format settings file written by a hub that only publishes names
            String settingsJson = """
                    {
                        "profiler": {
                            "defaultSettings": "-agentpath:<<JEFFREY_PROFILER_PATH>>=start,event=wall",
                            "defaultSettingsLevel": "GLOBAL",
                            "projectSettings": {
                                "my-project": "-agentpath:<<JEFFREY_PROFILER_PATH>>=start,event=cpu"
                            }
                        }
                    }
                    """;
            Files.writeString(settingsDir.resolve("settings-2025-01-15T120000000000.json"), settingsJson);

            ResolvedProfilerSettings resolved = resolve(
                    "/my/profiler.so", null, workspacePath, "my-project", sessionPath, "");

            assertTrue(resolved.command().contains("event=cpu"));
            assertEquals(ProfilerSettingsSource.HUB_PROJECT, resolved.source());
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
                            "defaultSettingsLevel": "WORKSPACE",
                            "projectSettings": {}
                        }
                    }
                    """;
            Files.writeString(settingsDir.resolve("settings-2025-01-15T120000000000.json"), settingsJson);

            String result = resolve(
                    "/profiler.so", null, workspacePath, "my-project", sessionPath, "").command();

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

            ResolvedProfilerSettings resolved = resolve(
                    "/profiler.so", null, workspacePath, "my-project", sessionPath, "");

            // Should use built-in default since no valid settings files
            assertTrue(resolved.command().contains("jfrsync=default"));
            assertEquals(ProfilerSettingsSource.BUILT_IN, resolved.source());
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

            String result = resolve(
                    "/custom/async-profiler/libasyncProfiler.so", null, workspacePath,
                    "my-project", sessionPath, "").command();

            assertTrue(result.contains("/custom/async-profiler/libasyncProfiler.so"));
            assertFalse(result.contains("<<JEFFREY_PROFILER_PATH>>"));
        }

        @Test
        void replacesCurrentSessionPlaceholder() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path sessionPath = tempDir.resolve("sessions/session-abc123");

            String result = resolve(
                    "/profiler.so", null, workspacePath, "my-project", sessionPath, "").command();

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

            String result = resolve(
                    "/profiler.so", "-agentpath:/profiler.so=start", workspacePath,
                    "my-project", sessionPath, "").command();

            assertEquals("-agentpath:/profiler.so=start", result.trim());
        }

        @Test
        void appendsMultipleFeatures() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path sessionPath = tempDir.resolve("session");
            String features = "-XX:+UsePerfData -XX:+HeapDumpOnOutOfMemoryError -Xlog:jfr*=trace";

            String result = resolve(
                    "/profiler.so", "-agentpath:/profiler.so=start", workspacePath,
                    "my-project", sessionPath, features).command();

            assertTrue(result.contains("-agentpath:/profiler.so=start"));
            assertTrue(result.contains("-XX:+UsePerfData"));
            assertTrue(result.contains("-XX:+HeapDumpOnOutOfMemoryError"));
            assertTrue(result.contains("-Xlog:jfr*=trace"));
        }
    }
}
