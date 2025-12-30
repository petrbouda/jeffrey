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
import pbouda.jeffrey.shared.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.model.RepositoryType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InitConfigTest {

    // Base config template with all required fields for ConfigBeanFactory
    private static final String BASE_CONFIG = """
            silent = false
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
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE);

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
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE);

            assertNotNull(config.getPerfCounters());
            assertTrue(config.getPerfCounters().isEnabled());
            assertTrue(config.isPerfCountersEnabled());
        }

        @Test
        void parsesHeapDumpConfig() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE);

            assertNotNull(config.getHeapDump());
            assertTrue(config.getHeapDump().isEnabled());
            assertEquals("crash", config.getHeapDump().getType());
            assertEquals(HeapDumpType.CRASH, config.resolveHeapDumpType());
        }

        @Test
        void parsesJvmLoggingConfig() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE);

            assertNotNull(config.getJvmLogging());
            assertTrue(config.getJvmLogging().isEnabled());
            assertTrue(config.getJvmLoggingCommand().contains("jfr*=trace"));
        }

        @Test
        void parsesMessagingConfig() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE);

            assertNotNull(config.getMessaging());
            assertTrue(config.getMessaging().isEnabled());
            assertEquals("12h", config.getMessaging().getMaxAge());
            assertTrue(config.isMessagingEnabled());
            assertEquals("12h", config.getMessagingMaxAge());
        }

        @Test
        void parsesJdkJavaOptionsConfig() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE);

            assertNotNull(config.getJdkJavaOptions());
            assertTrue(config.getJdkJavaOptions().isEnabled());
            assertEquals("-Xmx1200m -Xms1200m", config.getJdkJavaOptions().getAdditionalOptions());
            assertTrue(config.isJdkJavaOptionsEnabled());
            assertEquals("-Xmx1200m -Xms1200m", config.getAdditionalJvmOptions());
        }

        @Test
        void parsesAttributes() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE);

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
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE);

            assertEquals("/tmp/jeffrey", config.getJeffreyHome());
            assertEquals("test-workspace", config.getWorkspaceId());
            assertEquals("minimal-project", config.getProjectName());
        }

        @Test
        void optionalFieldsAreNull() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE);

            assertNull(config.getWorkspacesDir());
            assertNull(config.getProjectLabel());
            assertNull(config.getProfilerPath());
            assertNull(config.getProfilerConfig());
            assertNull(config.getRepositoryType());
            assertNull(config.getAttributes());
        }

        @Test
        void nestedConfigsAreDisabled() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE);

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
        }
    }

    @Nested
    class ParseWorkspacesDirConfig {

        private static final Path CONFIG_FILE = FileSystemUtils.classpathPath("valid-workspaces-dir-config.conf");

        @Test
        void parsesWorkspacesDirInsteadOfJeffreyHome() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE);

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
            InitConfig config = InitConfig.fromHoconFile(FULL_CONFIG);
            assertTrue(config.useJeffreyHome());
        }

        @Test
        void useJeffreyHomeReturnsFalseWhenUsingWorkspacesDir() {
            InitConfig config = InitConfig.fromHoconFile(WORKSPACES_CONFIG);
            assertFalse(config.useJeffreyHome());
        }

        @Test
        void isPerfCountersEnabledReturnsFalseWhenDisabled() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG);
            assertFalse(config.isPerfCountersEnabled());
        }

        @Test
        void isMessagingEnabledReturnsFalseWhenDisabled() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG);
            assertFalse(config.isMessagingEnabled());
        }

        @Test
        void getMessagingMaxAgeReturnsDefaultWhenNotSet() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG);
            assertEquals("24h", config.getMessagingMaxAge());
        }

        @Test
        void isJdkJavaOptionsEnabledReturnsFalseWhenDisabled() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG);
            assertFalse(config.isJdkJavaOptionsEnabled());
        }

        @Test
        void getAdditionalJvmOptionsReturnsNullWhenNotSet() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG);
            assertNull(config.getAdditionalJvmOptions());
        }

        @Test
        void getJvmLoggingCommandReturnsNullWhenDisabled() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG);
            assertNull(config.getJvmLoggingCommand());
        }

        @Test
        void resolveHeapDumpTypeReturnsNullWhenDisabled() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG);
            assertNull(config.resolveHeapDumpType());
        }
    }

    @Nested
    class TypeResolution {

        private static final Path FULL_CONFIG = FileSystemUtils.classpathPath("valid-full-config.conf");
        private static final Path MINIMAL_CONFIG = FileSystemUtils.classpathPath("valid-minimal-config.conf");

        @Test
        void resolveRepositoryTypeReturnsDefaultWhenNull() {
            InitConfig config = InitConfig.fromHoconFile(MINIMAL_CONFIG);
            assertEquals(RepositoryType.ASYNC_PROFILER, config.resolveRepositoryType());
        }

        @Test
        void resolveRepositoryTypeReturnsSpecifiedValue() {
            InitConfig config = InitConfig.fromHoconFile(FULL_CONFIG);
            assertEquals(RepositoryType.ASYNC_PROFILER, config.resolveRepositoryType());
        }

        @Test
        void resolveHeapDumpTypeReturnsCrash() {
            InitConfig config = InitConfig.fromHoconFile(FULL_CONFIG);
            assertEquals(HeapDumpType.CRASH, config.resolveHeapDumpType());
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
                    () -> InitConfig.fromHoconFile(nonExistent)
            );
            assertTrue(exception.getMessage().contains("Config file does not exist"));
        }

        @Test
        void throwsExceptionWhenNeitherJeffreyHomeNorWorkspacesDirSpecified() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "workspace-id = \"test\"",
                    "project-name = \"test\""
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile)
            );
            assertEquals("Either 'jeffrey-home' or 'workspaces-dir' must be specified", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenBothJeffreyHomeAndWorkspacesDirSpecified() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "workspaces-dir = \"/tmp/workspaces\"",
                    "workspace-id = \"test\"",
                    "project-name = \"test\""
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile)
            );
            assertEquals("Cannot specify both 'jeffrey-home' and 'workspaces-dir'", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenWorkspaceIdMissing() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project-name = \"test\""
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile)
            );
            assertEquals("'workspace-id' must be specified", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenWorkspaceIdBlank() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "workspace-id = \"   \"",
                    "project-name = \"test\""
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile)
            );
            assertEquals("'workspace-id' must be specified", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenProjectNameMissing() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "workspace-id = \"test\""
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile)
            );
            assertEquals("'project-name' must be specified", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenProjectNameBlank() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "workspace-id = \"test\"",
                    "project-name = \"   \""
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile)
            );
            assertEquals("'project-name' must be specified", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenProjectNameContainsInvalidCharacters() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "workspace-id = \"test\"",
                    "project-name = \"my project\""
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile)
            );
            assertEquals("Project name can only contain alphanumeric characters, underscores, and dashes", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenProjectNameContainsSpecialCharacters() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "workspace-id = \"test\"",
                    "project-name = \"my@project!\""
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile)
            );
            assertEquals("Project name can only contain alphanumeric characters, underscores, and dashes", exception.getMessage());
        }

        @Test
        void validProjectNameWithUnderscoresAndDashes() throws IOException {
            Path configFile = tempDir.resolve("valid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "workspace-id = \"test\"",
                    "project-name = \"my_project-123\""
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile);
            assertEquals("my_project-123", config.getProjectName());
        }

        @Test
        void throwsExceptionWhenMessagingEnabledWithProfilerConfig() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "workspace-id = \"test\"",
                    "project-name = \"test\"",
                    "profiler-config = \"some-config\"",
                    "messaging { enabled = true }"
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile)
            );
            assertEquals("Cannot specify both 'messaging.enabled' and 'profiler-config'", exception.getMessage());
        }

        @Test
        void allowsMessagingDisabledWithProfilerConfig() throws IOException {
            Path configFile = tempDir.resolve("valid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "workspace-id = \"test\"",
                    "project-name = \"test\"",
                    "profiler-config = \"some-config\"",
                    "messaging { enabled = false }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile);
            assertFalse(config.isMessagingEnabled());
            assertEquals("some-config", config.getProfilerConfig());
        }
    }
}
