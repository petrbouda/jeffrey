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

import cafe.jeffrey.provisioner.placeholder.JeffreyPlaceholderSource;
import cafe.jeffrey.provisioner.placeholder.Placeholders;

import static org.junit.jupiter.api.Assertions.*;

class ProfilerSettingsResolverTest {

    private static ResolvedProfilerSettings resolve(
            String profilerPath,
            String profilerCommand,
            Path workspacePath,
            String projectName,
            Path sessionPath,
            String features) {

        // Only the session matters here; the rest of the layout is never read back.
        SessionLayout layout = new SessionLayout(
                null, workspacePath, workspacePath, workspacePath.resolve(projectName), sessionPath);
        Placeholders placeholders = Placeholders.of(
                JeffreyPlaceholderSource.of(layout, profilerPath, EnvFileBuilder.DEFAULT_FILE_TEMPLATE));

        return new ProfilerSettingsResolver().resolve(profilerCommand, placeholders, features);
    }

    @Nested
    class ConfiguredProfilerCommand {

        @TempDir
        Path tempDir;

        @Test
        void usesProvidedProfilerConfigWhenNotBlank() {
            Path workspacePath = tempDir.resolve("workspace");
            Path sessionPath = tempDir.resolve("session");
            String profilerCommand = "-agentpath:/custom/path=start,event=cpu";

            ResolvedProfilerSettings resolved = resolve(
                    "/path/to/profiler", profilerCommand, workspacePath, "my-project", sessionPath, "");

            assertEquals(profilerCommand, resolved.command().trim());
            assertEquals(ProfilerSettingsSource.CLI_CONFIG, resolved.source());
        }

        @Test
        void replacesPlaceholdersInProvidedConfig() {
            Path workspacePath = tempDir.resolve("workspace");
            Path sessionPath = tempDir.resolve("session");
            String profilerCommand = "-agentpath:<<JEFFREY:PROFILER_PATH>>=start,file=<<JEFFREY:CURRENT_SESSION>>/output.jfr";

            String result = resolve(
                    "/custom/profiler.so", profilerCommand, workspacePath, "my-project", sessionPath, "").command();

            assertTrue(result.contains("/custom/profiler.so"));
            assertTrue(result.contains(sessionPath.toString()));
            assertFalse(result.contains("<<JEFFREY:PROFILER_PATH>>"));
            assertFalse(result.contains("<<JEFFREY:CURRENT_SESSION>>"));
        }

        @Test
        void appendsFeaturesAfterConfig() {
            Path workspacePath = tempDir.resolve("workspace");
            Path sessionPath = tempDir.resolve("session");
            String profilerCommand = "-agentpath:/custom/path=start";
            String features = "-XX:+UsePerfData -XX:+HeapDumpOnOutOfMemoryError";

            String result = resolve(
                    "/path/to/profiler", profilerCommand, workspacePath, "my-project", sessionPath, features).command();

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
            assertFalse(result.contains("<<JEFFREY:PROFILER_PATH>>"));
        }

        @Test
        void replacesCurrentSessionPlaceholder() throws IOException {
            Path workspacePath = Files.createDirectories(tempDir.resolve("workspace"));
            Path sessionPath = tempDir.resolve("sessions/session-abc123");

            String result = resolve(
                    "/profiler.so", null, workspacePath, "my-project", sessionPath, "").command();

            assertTrue(result.contains(sessionPath.toString()));
            assertFalse(result.contains("<<JEFFREY:CURRENT_SESSION>>"));
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
