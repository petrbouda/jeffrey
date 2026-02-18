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
import pbouda.jeffrey.init.model.HeapDumpType;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.model.RepositoryType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InitConfigTest {

    // Base config template with all required fields for ConfigBeanFactory
    private static final String BASE_CONFIG = """
            silent = false
            project { workspace-id = "", name = "", label = "", instance-id = "" }
            perf-counters { enabled = false }
            heap-dump { enabled = false }
            jvm-logging { enabled = false }
            messaging { enabled = false }
            jdk-java-options { enabled = false }
            """;

    private static String configWithOverrides(String... overrides) {
        StringBuilder config = new StringBuilder(BASE_CONFIG);
        for (String override : overrides) {
            config.append(override).append("\n");
        }
        return config.toString();
    }

    @Nested
    class ParseFullConfig {

        private static final Path CONFIG_FILE = FileSystemUtils.classpathPath("valid-full-config.conf");

        @Test
        void parsesAllFieldsCorrectly() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertEquals("/tmp/jeffrey", config.getJeffreyHome());
            assertEquals("/tmp/asprof/libasyncProfiler.so", config.getProfilerPath());
            assertNull(config.getProfilerConfig());
            assertEquals("uat", config.getWorkspaceId());
            assertEquals("test-project", config.getProjectName());
            assertEquals("Test Project", config.getProjectLabel());
            assertEquals("ASYNC_PROFILER", config.getRepositoryType());
            assertFalse(config.isSilent());
        }

        @Test
        void parsesPerfCountersConfig() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertNotNull(config.getPerfCounters());
            assertTrue(config.getPerfCounters().isEnabled());
            assertTrue(config.isPerfCountersEnabled());
        }

        @Test
        void parsesHeapDumpConfig() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertNotNull(config.getHeapDump());
            assertTrue(config.getHeapDump().isEnabled());
            assertEquals("crash", config.getHeapDump().getType());
            assertEquals(HeapDumpType.CRASH, config.resolveHeapDumpType());
        }

        @Test
        void parsesJvmLoggingConfig() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertNotNull(config.getJvmLogging());
            assertTrue(config.getJvmLogging().isEnabled());
            assertTrue(config.getJvmLoggingCommand().contains("jfr*=trace"));
        }

        @Test
        void parsesMessagingConfig() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertNotNull(config.getMessaging());
            assertTrue(config.getMessaging().isEnabled());
            assertEquals("12h", config.getMessaging().getMaxAge());
            assertTrue(config.isMessagingEnabled());
            assertEquals("12h", config.getMessagingMaxAge());
        }

        @Test
        void parsesDebugNonSafepointsConfig() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertNotNull(config.getDebugNonSafepoints());
            assertTrue(config.getDebugNonSafepoints().isEnabled());
            assertTrue(config.isDebugNonSafepointsEnabled());
        }

        @Test
        void parsesJdkJavaOptionsConfig() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertNotNull(config.getJdkJavaOptions());
            assertTrue(config.getJdkJavaOptions().isEnabled());
            assertEquals("-Xmx1200m -Xms1200m", config.getJdkJavaOptions().getAdditionalOptions());
            assertTrue(config.isJdkJavaOptionsEnabled());
            assertEquals("-Xmx1200m -Xms1200m", config.getAdditionalJvmOptions());
        }

        @Test
        void parsesAttributes() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertNotNull(config.getAttributes());
            assertEquals("blue", config.getAttributes().get("cluster"));
            assertEquals("klingon", config.getAttributes().get("namespace"));
        }
    }

    @Nested
    class ParseMinimalConfig {

        private static final Path CONFIG_FILE = FileSystemUtils.classpathPath("valid-minimal-config.conf");

        @Test
        void parsesRequiredFieldsOnly() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertEquals("/tmp/jeffrey", config.getJeffreyHome());
            assertEquals("test-workspace", config.getWorkspaceId());
            assertEquals("minimal-project", config.getProjectName());
        }

        @Test
        void optionalFieldsAreNull() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertNull(config.getWorkspacesDir());
            assertNull(config.getProjectLabel());
            assertNull(config.getProfilerPath());
            assertNull(config.getProfilerConfig());
            assertNull(config.getRepositoryType());
            assertNull(config.getAttributes());
        }

        @Test
        void nestedConfigsAreDisabled() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            // Nested configs exist but are disabled
            assertNotNull(config.getPerfCounters());
            assertFalse(config.isPerfCountersEnabled());

            assertNotNull(config.getHeapDump());
            assertNull(config.resolveHeapDumpType());

            assertNotNull(config.getJvmLogging());
            assertNull(config.getJvmLoggingCommand());

            assertNotNull(config.getMessaging());
            assertFalse(config.isMessagingEnabled());

            assertNotNull(config.getJdkJavaOptions());
            assertFalse(config.isJdkJavaOptionsEnabled());

            // Debug Non-Safepoints is enabled by default
            assertNotNull(config.getDebugNonSafepoints());
            assertTrue(config.isDebugNonSafepointsEnabled());
        }
    }

    @Nested
    class ParseWorkspacesDirConfig {

        private static final Path CONFIG_FILE = FileSystemUtils.classpathPath("valid-workspaces-dir-config.conf");

        @Test
        void parsesWorkspacesDirInsteadOfJeffreyHome() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertNull(config.getJeffreyHome());
            assertEquals("/tmp/workspaces", config.getWorkspacesDir());
            assertFalse(config.useJeffreyHome());
        }
    }

    @Nested
    class HelperMethods {

        private static final Path FULL_CONFIG = FileSystemUtils.classpathPath("valid-full-config.conf");
        private static final Path MINIMAL_CONFIG = FileSystemUtils.classpathPath("valid-minimal-config.conf");
        private static final Path WORKSPACES_CONFIG = FileSystemUtils.classpathPath("valid-workspaces-dir-config.conf");

        @Test
        void useJeffreyHomeReturnsTrueWhenSet() {
            InitConfig config = InitConfig.fromHoconFile(FULL_CONFIG, null);
            assertTrue(config.useJeffreyHome());
        }

        @Test
        void useJeffreyHomeReturnsFalseWhenUsingWorkspacesDir() {
            InitConfig config = InitConfig.fromHoconFile(WORKSPACES_CONFIG, null);
            assertFalse(config.useJeffreyHome());
        }

        @Test
        void isPerfCountersEnabledReturnsFalseWhenDisabled() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG, null);
            assertFalse(config.isPerfCountersEnabled());
        }

        @Test
        void isMessagingEnabledReturnsFalseWhenDisabled() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG, null);
            assertFalse(config.isMessagingEnabled());
        }

        @Test
        void getMessagingMaxAgeReturnsDefaultWhenNotSet() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG, null);
            assertEquals("24h", config.getMessagingMaxAge());
        }

        @Test
        void isDebugNonSafepointsEnabledReturnsTrueByDefault() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG, null);
            assertTrue(config.isDebugNonSafepointsEnabled());
        }

        @Test
        void isJdkJavaOptionsEnabledReturnsFalseWhenDisabled() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG, null);
            assertFalse(config.isJdkJavaOptionsEnabled());
        }

        @Test
        void getAdditionalJvmOptionsReturnsNullWhenNotSet() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG, null);
            assertNull(config.getAdditionalJvmOptions());
        }

        @Test
        void getJvmLoggingCommandReturnsNullWhenDisabled() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG, null);
            assertNull(config.getJvmLoggingCommand());
        }

        @Test
        void resolveHeapDumpTypeReturnsNullWhenDisabled() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG, null);
            assertNull(config.resolveHeapDumpType());
        }
    }

    @Nested
    class TypeResolution {

        private static final Path FULL_CONFIG = FileSystemUtils.classpathPath("valid-full-config.conf");
        private static final Path MINIMAL_CONFIG = FileSystemUtils.classpathPath("valid-minimal-config.conf");

        @Test
        void resolveRepositoryTypeReturnsDefaultWhenNull() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG, null);
            assertEquals(RepositoryType.ASYNC_PROFILER, config.resolveRepositoryType());
        }

        @Test
        void resolveRepositoryTypeReturnsSpecifiedValue() {
            InitConfig config = InitConfig.fromHoconFile(FULL_CONFIG, null);
            assertEquals(RepositoryType.ASYNC_PROFILER, config.resolveRepositoryType());
        }

        @Test
        void resolveHeapDumpTypeReturnsCrash() {
            InitConfig config = InitConfig.fromHoconFile(FULL_CONFIG, null);
            assertEquals(HeapDumpType.CRASH, config.resolveHeapDumpType());
        }
    }

    @Nested
    class ProfilerPathResolution {

        @TempDir
        Path tempDir;

        @Test
        void returnsExplicitProfilerPathWhenSet() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Path libsDir = tempDir.resolve("libs/current");
            Files.createDirectories(libsDir);
            Files.createFile(libsDir.resolve("libasyncProfiler.so"));

            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"" + tempDir + "\"",
                    "profiler-path = \"/custom/path/libasyncProfiler.so\"",
                    "project { workspace-id = \"test\", name = \"test\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null);
            assertEquals("/custom/path/libasyncProfiler.so", config.getProfilerPath());
        }

        @Test
        void autoResolvesFromJeffreyHome() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Path libsDir = tempDir.resolve("libs/current");
            Files.createDirectories(libsDir);
            Files.createFile(libsDir.resolve("libasyncProfiler.so"));

            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"" + tempDir + "\"",
                    "project { workspace-id = \"test\", name = \"test\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null);
            assertEquals(libsDir.resolve("libasyncProfiler.so").toString(), config.getProfilerPath());
        }

        @Test
        void returnsNullWhenAutoResolvePathDoesNotExist() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"" + tempDir + "\"",
                    "project { workspace-id = \"test\", name = \"test\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null);
            assertNull(config.getProfilerPath());
        }

        @Test
        void returnsNullWhenUsingWorkspacesDir() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Path libsDir = tempDir.resolve("libs/current");
            Files.createDirectories(libsDir);
            Files.createFile(libsDir.resolve("libasyncProfiler.so"));

            Files.writeString(configFile, configWithOverrides(
                    "workspaces-dir = \"" + tempDir + "\"",
                    "project { workspace-id = \"test\", name = \"test\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null);
            assertNull(config.getProfilerPath());
        }
    }

    @Nested
    class Validation {

        @TempDir
        Path tempDir;

        @Test
        void throwsExceptionWhenConfigFileDoesNotExist() {
            Path nonExistent = Path.of("/non/existent/path.conf");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(nonExistent, null)
            );
            assertTrue(exception.getMessage().contains("Base config file does not exist"));
        }

        @Test
        void throwsExceptionWhenNeitherJeffreyHomeNorWorkspacesDirSpecified() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "project { workspace-id = \"test\", name = \"test\" }"
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile, null)
            );
            assertEquals("Either 'jeffrey-home' or 'workspaces-dir' must be specified", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenBothJeffreyHomeAndWorkspacesDirSpecified() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "workspaces-dir = \"/tmp/workspaces\"",
                    "project { workspace-id = \"test\", name = \"test\" }"
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile, null)
            );
            assertEquals("Cannot specify both 'jeffrey-home' and 'workspaces-dir'", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenWorkspaceIdMissing() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { name = \"test\" }"
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile, null)
            );
            assertEquals("'project.workspace-id' must be specified", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenWorkspaceIdBlank() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { workspace-id = \"   \", name = \"test\" }"
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile, null)
            );
            assertEquals("'project.workspace-id' must be specified", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenProjectNameMissing() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { workspace-id = \"test\" }"
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile, null)
            );
            assertEquals("'project.name' must be specified", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenProjectNameBlank() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { workspace-id = \"test\", name = \"   \" }"
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile, null)
            );
            assertEquals("'project.name' must be specified", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenProjectNameContainsInvalidCharacters() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { workspace-id = \"test\", name = \"my project\" }"
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile, null)
            );
            assertEquals("Project name can only contain alphanumeric characters, underscores, and dashes", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenProjectNameContainsSpecialCharacters() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { workspace-id = \"test\", name = \"my@project!\" }"
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile, null)
            );
            assertEquals("Project name can only contain alphanumeric characters, underscores, and dashes", exception.getMessage());
        }

        @Test
        void validProjectNameWithUnderscoresAndDashes() throws IOException {
            Path configFile = tempDir.resolve("valid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { workspace-id = \"test\", name = \"my_project-123\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null);
            assertEquals("my_project-123", config.getProjectName());
        }

        @Test
        void throwsExceptionWhenMessagingEnabledWithProfilerConfig() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { workspace-id = \"test\", name = \"test\" }",
                    "profiler-config = \"some-config\"",
                    "messaging { enabled = true }"
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile, null)
            );
            assertEquals("Cannot specify both 'messaging.enabled' and 'profiler-config'", exception.getMessage());
        }

        @Test
        void allowsMessagingDisabledWithProfilerConfig() throws IOException {
            Path configFile = tempDir.resolve("valid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { workspace-id = \"test\", name = \"test\" }",
                    "profiler-config = \"some-config\"",
                    "messaging { enabled = false }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null);
            assertFalse(config.isMessagingEnabled());
            assertEquals("some-config", config.getProfilerConfig());
        }
    }
}
