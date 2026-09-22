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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.provisioner.config.ConfigPaths;
import cafe.jeffrey.provisioner.config.VolumeConfigLayer;
import cafe.jeffrey.provisioner.feature.TracingJfrEvents;
import cafe.jeffrey.provisioner.model.HeapDumpType;
import cafe.jeffrey.shared.common.CliConstants;
import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.config.ConfigSource;
import cafe.jeffrey.shared.common.config.ConfigType;
import cafe.jeffrey.shared.common.config.ScopedConfigLayout;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.RepositoryType;
import cafe.jeffrey.shared.common.model.repository.AppliedConfigLayer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class InitConfigTest {

    // Base config template with all required fields for ConfigBeanFactory
    private static final String BASE_CONFIG = """
            project { workspace-ref-id = "", name = "", label = "", instance-name = "" }
            perf-counters { enabled = false }
            heap-dump { enabled = false }
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
            assertNull(config.getAsprofSettings());
            assertEquals("uat", config.getWorkspaceRefId());
            assertEquals("test-project", config.getProjectName());
            assertEquals("Test Project", config.getProjectLabel());
            assertEquals("ASYNC_PROFILER", config.getRepositoryType());
        }

        @Test
        void parsesPerfCountersConfig() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertTrue(config.isPerfCountersEnabled());
        }

        @Test
        void parsesHeapDumpConfig() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertEquals(HeapDumpType.CRASH, config.resolveHeapDumpType());
        }

        @Test
        void parsesDebugNonSafepointsConfig() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertTrue(config.isDebugNonSafepointsEnabled());
        }

        @Test
        void parsesJdkJavaOptionsConfig() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertTrue(config.isJdkJavaOptionsEnabled());
            assertEquals("-Xmx1200m -Xms1200m", config.getAdditionalJvmOptions());
        }

        @Test
        void parsesAttributes() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertNotNull(config.getAttributes());
            assertEquals("blue", config.getAttributes().get("cluster"));
        }
    }

    @Nested
    class ParseMinimalConfig {

        private static final Path CONFIG_FILE = FileSystemUtils.classpathPath("valid-minimal-config.conf");

        @Test
        void parsesRequiredFieldsOnly() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertEquals("/tmp/jeffrey", config.getJeffreyHome());
            assertEquals("test-workspace", config.getWorkspaceRefId());
            assertEquals("minimal-project", config.getProjectName());
        }

        @Test
        void optionalFieldsAreNull() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertNull(config.getWorkspacesDir());
            assertNull(config.getProjectLabel());
            assertNull(config.getProfilerPath());
            assertNull(config.getAsprofSettings());
            assertNull(config.getRepositoryType());
            // Attributes are empty rather than null, so callers need no null check
            assertEquals(Map.of(), config.getAttributes());
        }

        @Test
        void nestedConfigsAreDisabled() {
            InitConfig config = InitConfig.fromHoconFile(CONFIG_FILE, null);

            assertFalse(config.isPerfCountersEnabled());
            assertNull(config.resolveHeapDumpType());
            assertFalse(config.isJdkJavaOptionsEnabled());

            // Debug Non-Safepoints is enabled by default
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
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"" + tempDir + "\"",
                    "profiler-path = \"/custom/path/libasyncProfiler.so\"",
                    "project { workspace-ref-id = \"test\", name = \"test\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null);
            assertEquals("/custom/path/libasyncProfiler.so", config.getProfilerPath());
        }

        @Test
        void returnsNullWhenNoProfilerPathIsConfigured() throws IOException {
            // The profiler is baked into the image by the jeffrey-jib build extension, which also
            // bakes JEFFREY_PROFILER_PATH. Nothing is discovered on disk any more, so an
            // unconfigured path simply means an application that starts without profiling rather
            // than one that fails to start.
            Path configFile = tempDir.resolve("config.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"" + tempDir + "\"",
                    "project { workspace-ref-id = \"test\", name = \"test\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null);
            assertNull(config.getProfilerPath());
        }
    }

    @Nested
    class HeartbeatDeclaration {

        @TempDir
        Path tempDir;

        @Test
        void defaultsToExpectingNothing() throws IOException {
            // This switch is a claim about the application, not about the JVM: it says the
            // jeffrey-heartbeat library is on its class path, which the provisioner cannot see.
            // Claimed wrongly it is not inert — the hub finishes the session at its own start
            // timestamp seconds after the JVM came up — so the default is the harmless side.
            Path configFile = tempDir.resolve("config.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"" + tempDir + "\"",
                    "project { workspace-ref-id = \"test\", name = \"test\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null);

            assertFalse(config.isHeartbeatEnabled(),
                    "an application carrying the dependency is something only a deployment knows");
        }

        @Test
        void canDeclareThatSomethingWillReport() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"" + tempDir + "\"",
                    "heartbeat { enabled = true }",
                    "project { workspace-ref-id = \"test\", name = \"test\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null);

            assertTrue(config.isHeartbeatEnabled());
        }
    }

    @Nested
    class LocationPrecedence {

        @TempDir
        Path tempDir;

        /** An environment that has JEFFREY_HOME baked in and nothing else. */
        private static Function<String, String> onlyJeffreyHome(String value) {
            return name -> "JEFFREY_HOME".equals(name) ? value : null;
        }

        @Test
        void usesEnvVarWhenConfigOmitsJeffreyHome() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Files.writeString(configFile, configWithOverrides(
                    "project { workspace-ref-id = \"test\", name = \"test\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null, onlyJeffreyHome("/opt/jeffrey"));
            assertEquals("/opt/jeffrey", config.getJeffreyHome());
            assertTrue(config.useJeffreyHome());
        }

        @Test
        void envVarOverridesTheConfiguredJeffreyHome() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/baked-into-the-image\"",
                    "project { workspace-ref-id = \"test\", name = \"test\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null, onlyJeffreyHome("/from-the-pod"));
            assertEquals("/from-the-pod", config.getJeffreyHome());
        }

        /**
         * The two location settings are mutually exclusive, so the winning layer takes the pair as
         * a unit. An environment naming jeffrey-home replaces a file's workspaces-dir outright,
         * rather than adding to it and failing validation with "cannot specify both".
         */
        @Test
        void envJeffreyHomeReplacesAConfiguredWorkspacesDir() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Path workspacesDir = tempDir.resolve("workspaces");
            Files.createDirectories(workspacesDir);

            Files.writeString(configFile, configWithOverrides(
                    "workspaces-dir = \"" + workspacesDir + "\"",
                    "project { workspace-ref-id = \"test\", name = \"test\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null, onlyJeffreyHome("/opt/jeffrey"));
            assertTrue(config.useJeffreyHome());
            assertEquals("/opt/jeffrey", config.getJeffreyHome());
            assertNull(config.getWorkspacesDir());
        }

        @Test
        void configuredLocationStandsWhenTheEnvironmentNamesNone() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Path workspacesDir = tempDir.resolve("workspaces");
            Files.createDirectories(workspacesDir);

            Files.writeString(configFile, configWithOverrides(
                    "workspaces-dir = \"" + workspacesDir + "\"",
                    "project { workspace-ref-id = \"test\", name = \"test\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null, name -> null);
            assertFalse(config.useJeffreyHome());
            assertEquals(workspacesDir.toString(), config.getWorkspacesDir());
        }

        @Test
        void validationFailsWhenNeitherConfigNorEnvVarIsSet() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Files.writeString(configFile, configWithOverrides(
                    "project { workspace-ref-id = \"test\", name = \"test\" }"
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile, null, name -> null)
            );
            assertTrue(exception.getMessage().contains("Either 'jeffrey-home' or 'workspaces-dir'"));
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
                    "project { workspace-ref-id = \"test\", name = \"test\" }"
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
                    "project { workspace-ref-id = \"test\", name = \"test\" }"
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile, null)
            );
            assertEquals("Cannot specify both 'jeffrey-home' and 'workspaces-dir'", exception.getMessage());
        }

        @Test
        void missingWorkspaceRefId_resolvesToDefaultConstant() throws IOException {
            Path configFile = tempDir.resolve("valid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { name = \"test\" }"
            ));

            InitConfig parsed = InitConfig.fromHoconFile(configFile, null);

            assertEquals(CliConstants.DEFAULT_WORKSPACE_REF_ID, parsed.getWorkspaceRefId(),
                    "Missing workspace-ref-id should default to the shared CliConstants.DEFAULT_WORKSPACE_REF_ID "
                            + "so CLI directory layout matches the server's default workspace");
            assertEquals("test", parsed.getProjectName());
        }

        @Test
        void blankWorkspaceRefId_resolvesToDefaultConstant() throws IOException {
            Path configFile = tempDir.resolve("valid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { workspace-ref-id = \"   \", name = \"test\" }"
            ));

            InitConfig parsed = InitConfig.fromHoconFile(configFile, null);

            assertEquals(CliConstants.DEFAULT_WORKSPACE_REF_ID, parsed.getWorkspaceRefId());
        }

        @Test
        void throwsExceptionWhenProjectNameMissing() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { workspace-ref-id = \"test\" }"
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile, null)
            );
            assertEquals("'project.name' must be specified (HOCON 'project.name' or env JEFFREY_PROJECT_NAME)", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenProjectNameBlank() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { workspace-ref-id = \"test\", name = \"   \" }"
            ));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromHoconFile(configFile, null)
            );
            assertEquals("'project.name' must be specified (HOCON 'project.name' or env JEFFREY_PROJECT_NAME)", exception.getMessage());
        }

        @Test
        void throwsExceptionWhenProjectNameContainsInvalidCharacters() throws IOException {
            Path configFile = tempDir.resolve("invalid.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { workspace-ref-id = \"test\", name = \"my project\" }"
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
                    "project { workspace-ref-id = \"test\", name = \"my@project!\" }"
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
                    "project { workspace-ref-id = \"test\", name = \"my_project-123\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null);
            assertEquals("my_project-123", config.getProjectName());
        }

    }

    @Nested
    class EnvironmentOnlyConfig {

        private static Function<String, String> env(Map<String, String> values) {
            return values::get;
        }

        @Test
        void minimalEnv_homeAndProjectName_producesValidConfig() {
            InitConfig config = InitConfig.fromEnvironment(env(Map.of(
                    "JEFFREY_HOME", "/mnt/jeffrey",
                    "JEFFREY_PROJECT_NAME", "my-service")));

            assertEquals("/mnt/jeffrey", config.getJeffreyHome());
            assertEquals("my-service", config.getProjectName());
            assertEquals(CliConstants.DEFAULT_WORKSPACE_REF_ID, config.getWorkspaceRefId());
            assertEquals(Path.of("/tmp/jvm.args"), config.getArgFilePath());
            assertTrue(config.isDebugNonSafepointsEnabled());
            assertFalse(config.isPerfCountersEnabled());
            assertTrue(config.isSpanTracingEnabled());
            assertNull(config.resolveHeapDumpType());
        }

        @Test
        void tracingDisabledFromEnv_noConfigFileNeeded() {
            InitConfig config = InitConfig.fromEnvironment(env(Map.of(
                    "JEFFREY_HOME", "/mnt/jeffrey",
                    "JEFFREY_PROJECT_NAME", "my-service",
                    "JEFFREY_TRACING_ENABLED", "false")));

            assertFalse(config.isSpanTracingEnabled());
        }

        @Test
        void unrecognizedBoolEnv_keepsTheConfiguredValue() {
            InitConfig config = InitConfig.fromEnvironment(env(Map.of(
                    "JEFFREY_HOME", "/mnt/jeffrey",
                    "JEFFREY_PROJECT_NAME", "my-service",
                    "JEFFREY_TRACING_ENABLED", "maybe")));

            assertTrue(config.isSpanTracingEnabled());
        }

        @Test
        void fullEnv_allSettingsApplied() {
            InitConfig config = InitConfig.fromEnvironment(env(Map.of(
                    "JEFFREY_HOME", "/mnt/jeffrey",
                    "JEFFREY_PROJECT_NAME", "my-service",
                    "JEFFREY_PROJECT_LABEL", "My Service",
                    "JEFFREY_WORKSPACE_REF_ID", "production",
                    "JEFFREY_INSTANCE_NAME", "instance-7",
                    "JEFFREY_ATTRIBUTES", "cluster=blue, namespace=test-namespace",
                    "JEFFREY_HEAP_DUMP", "crash",
                    "JEFFREY_PERF_COUNTERS", "true",
                    "JEFFREY_ADDITIONAL_JVM_OPTIONS", "-Xmx2g")));

            assertEquals("My Service", config.getProjectLabel());
            assertEquals("production", config.getWorkspaceRefId());
            assertEquals("instance-7", config.getInstanceName());
            assertEquals(Map.of("cluster", "blue", "namespace", "test-namespace"), config.getAttributes());
            assertEquals(HeapDumpType.CRASH, config.resolveHeapDumpType());
            assertTrue(config.isPerfCountersEnabled());
            assertEquals("-Xmx2g", config.getAdditionalJvmOptions());
        }

        @Test
        void missingProjectName_failsWithEnvHint() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> InitConfig.fromEnvironment(env(Map.of("JEFFREY_HOME", "/mnt/jeffrey"))));

            assertTrue(exception.getMessage().contains("JEFFREY_PROJECT_NAME"));
        }

        @Test
        void malformedAttributePairs_skipped() {
            InitConfig config = InitConfig.fromEnvironment(env(Map.of(
                    "JEFFREY_HOME", "/mnt/jeffrey",
                    "JEFFREY_PROJECT_NAME", "my-service",
                    "JEFFREY_ATTRIBUTES", "cluster=blue,broken,=novalue,region=eu")));

            assertEquals(Map.of("cluster", "blue", "region", "eu"), config.getAttributes());
        }

        /**
         * The reported case: Kubernetes only expands {@code $(VAR)} for variables declared earlier
         * in the same container's {@code env:} list, so anything injected from elsewhere has to be
         * resolved here instead.
         */
        @Test
        void attributesResolveEnvironmentPlaceholders() {
            InitConfig config = InitConfig.fromEnvironment(env(Map.of(
                    "JEFFREY_HOME", "/mnt/jeffrey",
                    "JEFFREY_PROJECT_NAME", "my-service",
                    "SF_CLUSTER", "blue",
                    "SF_ENV", "uat",
                    "JEFFREY_ATTRIBUTES",
                    "cluster=<<ENV:SF_CLUSTER>>,env=<<ENV:SF_ENV>>,namespace=test-namespace")));

            assertEquals(
                    Map.of("cluster", "blue", "env", "uat", "namespace", "test-namespace"),
                    config.getAttributes());
        }

        @Test
        void unsetAttributePlaceholderFallsBackToItsDefault() {
            InitConfig config = InitConfig.fromEnvironment(env(Map.of(
                    "JEFFREY_HOME", "/mnt/jeffrey",
                    "JEFFREY_PROJECT_NAME", "my-service",
                    "JEFFREY_ATTRIBUTES", "cluster=<<ENV:SF_CLUSTER:-unknown>>")));

            assertEquals(Map.of("cluster", "unknown"), config.getAttributes());
        }

        /**
         * Attributes are split before they are resolved. Resolving first would let a substituted
         * value containing a comma silently break apart into extra attribute pairs.
         */
        @Test
        void aResolvedValueContainingACommaStaysOneAttribute() {
            InitConfig config = InitConfig.fromEnvironment(env(Map.of(
                    "JEFFREY_HOME", "/mnt/jeffrey",
                    "JEFFREY_PROJECT_NAME", "my-service",
                    "SF_ZONES", "eu-west-1,eu-west-2",
                    "JEFFREY_ATTRIBUTES", "zones=<<ENV:SF_ZONES>>")));

            assertEquals(Map.of("zones", "eu-west-1,eu-west-2"), config.getAttributes());
        }

        @Test
        void otherSettingsResolveEnvironmentPlaceholdersToo() {
            InitConfig config = InitConfig.fromEnvironment(env(Map.of(
                    "JEFFREY_HOME", "/mnt/jeffrey",
                    "JEFFREY_PROJECT_NAME", "my-service",
                    "SF_ENV", "uat",
                    "JEFFREY_PROJECT_LABEL", "My Service (<<ENV:SF_ENV>>)",
                    "JEFFREY_ADDITIONAL_JVM_OPTIONS", "-Denv=<<ENV:SF_ENV>>")));

            assertEquals("My Service (uat)", config.getProjectLabel());
            assertEquals("-Denv=uat", config.getAdditionalJvmOptions());
        }

        @Test
        void unrecognizedHeapDumpValue_staysDisabled() {
            InitConfig config = InitConfig.fromEnvironment(env(Map.of(
                    "JEFFREY_HOME", "/mnt/jeffrey",
                    "JEFFREY_PROJECT_NAME", "my-service",
                    "JEFFREY_HEAP_DUMP", "sometimes")));

            assertNull(config.resolveHeapDumpType());
        }

        @Test
        void heapDumpOff_staysDisabled() {
            InitConfig config = InitConfig.fromEnvironment(env(Map.of(
                    "JEFFREY_HOME", "/mnt/jeffrey",
                    "JEFFREY_PROJECT_NAME", "my-service",
                    "JEFFREY_HEAP_DUMP", "off")));

            assertNull(config.resolveHeapDumpType());
        }
    }

    @Nested
    class BooleanEnvVocabulary {

        private static Function<String, String> envWith(String name, String value) {
            Map<String, String> values = new HashMap<>(Map.of(
                    "JEFFREY_HOME", "/mnt/jeffrey",
                    "JEFFREY_PROJECT_NAME", "my-service"));
            values.put(name, value);
            return values::get;
        }

        @ParameterizedTest
        @ValueSource(strings = {"true", "TRUE", "True", " true "})
        void acceptsTrue(String value) {
            assertTrue(InitConfig.fromEnvironment(envWith("JEFFREY_PERF_COUNTERS", value))
                    .isPerfCountersEnabled());
        }

        @ParameterizedTest
        @ValueSource(strings = {"false", "FALSE", "False", " false "})
        void acceptsFalse(String value) {
            assertFalse(InitConfig.fromEnvironment(envWith("JEFFREY_TRACING_ENABLED", value))
                    .isSpanTracingEnabled());
        }

        /**
         * Only true and false are a boolean. Everything else — including the yes/no/on/off and
         * 1/0 spellings this used to accept — is rejected rather than guessed at, because reading
         * an unrecognized value as false would let a typo silently disable a feature.
         */
        @ParameterizedTest
        @ValueSource(strings = {"yes", "no", "on", "off", "1", "0", "maybe", "TRUEISH"})
        void rejectsEverythingElseAndKeepsTheConfiguredValue(String value) {
            assertFalse(InitConfig.fromEnvironment(envWith("JEFFREY_PERF_COUNTERS", value))
                    .isPerfCountersEnabled(), "perf-counters defaults to off and must stay off");
            assertTrue(InitConfig.fromEnvironment(envWith("JEFFREY_TRACING_ENABLED", value))
                    .isSpanTracingEnabled(), "tracing defaults to on and must stay on");
        }
    }

    @Nested
    class EnvironmentReachesEverySetting {

        private static Function<String, String> env(Map<String, String> values) {
            return values::get;
        }

        /**
         * The zero-file path used to be unable to reach jdk-java-options, env-file, print-env,
         * debug-non-safepoints, workspaces-dir or repository-type at all. jdk-java-options was the
         * one that stung: it is how a JVM picks the settings up without an argfile.
         */
        @Test
        void settingsThatUsedToHaveNoEnvVar() {
            InitConfig config = InitConfig.fromEnvironment(env(Map.of(
                    "JEFFREY_HOME", "/mnt/jeffrey",
                    "JEFFREY_PROJECT_NAME", "my-service",
                    "JEFFREY_JDK_JAVA_OPTIONS", "true",
                    "JEFFREY_DEBUG_NON_SAFEPOINTS", "false",
                    "JEFFREY_ENV_FILE", "/tmp/jeffrey.env",
                    "JEFFREY_PRINT_ENV", "true",
                    "JEFFREY_REPOSITORY_TYPE", "ASYNC_PROFILER")));

            assertTrue(config.isJdkJavaOptionsEnabled());
            assertFalse(config.isDebugNonSafepointsEnabled());
            assertEquals(Path.of("/tmp/jeffrey.env"), config.getEnvFilePath());
            assertTrue(config.isPrintEnv());
            assertEquals("ASYNC_PROFILER", config.getRepositoryType());
        }

        /**
         * The async-profiler settings were the last setting the environment could not reach: the
         * generated .env exported the resolved command under the name an input would have used, so
         * accepting one would have fed a run's output into the next. The output has its own name
         * now, and this closes the gap that workaround left.
         */
        @Test
        void asprofSettingsFromEnv() {
            InitConfig config = InitConfig.fromEnvironment(env(Map.of(
                    "JEFFREY_HOME", "/mnt/jeffrey",
                    "JEFFREY_PROJECT_NAME", "my-service",
                    "JEFFREY_ASPROF_SETTINGS", "start,cpu")));

            assertEquals("start,cpu", config.getAsprofSettings());
            assertEquals(ConfigSource.CONTAINER, config.getProfilerCommandSource());
        }

        @Test
        void workspacesDirFromEnv() {
            InitConfig config = InitConfig.fromEnvironment(env(Map.of(
                    "JEFFREY_WORKSPACES_DIR", "/mnt/workspaces",
                    "JEFFREY_PROJECT_NAME", "my-service")));

            assertEquals("/mnt/workspaces", config.getWorkspacesDir());
            assertFalse(config.useJeffreyHome());
        }
    }

    @Nested
    class HoconEnvPrecedence {

        @TempDir
        Path tempDir;

        @Test
        void envProjectName_winsOverHocon() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { name = \"from-hocon\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null,
                    name -> name.equals("JEFFREY_PROJECT_NAME") ? "from-env" : null);

            assertEquals("from-env", config.getProjectName());
        }

        @Test
        void hoconTracing_disabledWithoutEnv() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { name = \"my-service\" }",
                    "tracing { enabled = false }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null, name -> null);

            assertFalse(config.isSpanTracingEnabled());
        }

        /**
         * The environment wins over a file, for flags exactly as for strings. A config file is
         * baked into the image; the environment is set on the pod, so it has to be able to
         * override one setting without a rebuild.
         */
        @Test
        void envTracing_winsOverHocon() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { name = \"my-service\" }",
                    "tracing { enabled = true }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null,
                    name -> name.equals("JEFFREY_TRACING_ENABLED") ? "false" : null);

            assertFalse(config.isSpanTracingEnabled());
        }

        /** The same holds when no file declares the flag at all — the environment beats defaults. */
        @Test
        void envTracing_turnsOffAnOnByDefaultFlag() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { name = \"my-service\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null,
                    name -> name.equals("JEFFREY_TRACING_ENABLED") ? "false" : null);

            assertFalse(config.isSpanTracingEnabled());
        }

        @Test
        void tracingJfrEventSettings_defaultsToTheBuiltInList() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { name = \"my-service\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null, name -> null);

            assertEquals(TracingJfrEvents.DEFAULT_SETTINGS, config.getTracingJfrEventSettings());
        }

        @Test
        void hoconTracingJfrEventSettings_winsOverTheBuiltInList() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { name = \"my-service\" }",
                    "tracing { jfr-event-settings = \"jdk.SocketRead#threshold=0ms\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null, name -> null);

            assertEquals("jdk.SocketRead#threshold=0ms", config.getTracingJfrEventSettings());
        }

        @Test
        void envTracingJfrEventSettings_winsOverHocon() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Files.writeString(configFile, configWithOverrides(
                    "jeffrey-home = \"/tmp/jeffrey\"",
                    "project { name = \"my-service\" }",
                    "tracing { jfr-event-settings = \"jdk.SocketRead#threshold=0ms\" }"
            ));

            InitConfig config = InitConfig.fromHoconFile(configFile, null,
                    name -> name.equals("JEFFREY_TRACING_JFR_EVENT_SETTINGS") ? "none" : null);

            assertEquals("none", config.getTracingJfrEventSettings());
        }

        @Test
        void blankHoconProjectName_filledFromEnv() throws IOException {
            Path configFile = tempDir.resolve("config.conf");
            Files.writeString(configFile, configWithOverrides("jeffrey-home = \"/tmp/jeffrey\""));

            InitConfig config = InitConfig.fromHoconFile(configFile, null,
                    name -> name.equals("JEFFREY_PROJECT_NAME") ? "from-env" : null);

            assertEquals("from-env", config.getProjectName());
        }
    }

    @Nested
    class CatalogueMatchesTheProvisionersKeys {

        /**
         * The linchpin of publishing values without a lookup table: a type's key is derived from its
         * name, and the provisioner must read a key spelled exactly that way. Nothing else connects
         * the two vocabularies, so if they ever drift this is where it shows.
         */
        @Test
        void everyPublishablePathIsASettingTheProvisionerReads() {
            Config defaults = ConfigFactory.parseString(InitConfig.DEFAULTS);

            for (String path : ScopedConfigLayout.publishablePaths()) {
                assertTrue(defaults.hasPath(path),
                        "a published type names a key the provisioner does not read: " + path);
            }
        }

        @Test
        void theAsprofSettingsTypeMapsOntoTheProvisionersOwnKey() {
            assertEquals(
                    ConfigPaths.ASPROF_SETTINGS,
                    ScopedConfigLayout.hoconPath(ConfigType.ASPROF_SETTINGS));
        }
    }

    @Nested
    class VolumeLayerPrecedence {

        private static final String ASPROF_SETTINGS = "asprof-settings";

        private InitConfig withLayers(InitConfig config, VolumeConfigLayer... layers) {
            return config.withVolumeLayers(List.of(layers));
        }

        private VolumeConfigLayer layer(ConfigScope scope, String hocon) {
            return new VolumeConfigLayer(
                    scope, Path.of("/volume/.config/jeffrey.conf"), "digest-" + scope,
                    ConfigFactory.parseString(hocon));
        }

        private InitConfig base(Map<String, String> env) {
            return InitConfig.fromEnvironment(env::get);
        }

        private Map<String, String> minimalEnv() {
            return Map.of("JEFFREY_HOME", "/mnt/jeffrey", "JEFFREY_PROJECT_NAME", "my-service");
        }

        @Test
        void aProjectLayerBeatsAWorkspaceLayerWhichBeatsAGlobalOne() {
            InitConfig config = withLayers(base(minimalEnv()),
                    layer(ConfigScope.GLOBAL, ASPROF_SETTINGS + " = \"global\""),
                    layer(ConfigScope.WORKSPACE, ASPROF_SETTINGS + " = \"workspace\""),
                    layer(ConfigScope.PROJECT, ASPROF_SETTINGS + " = \"project\""));

            assertEquals("project", config.getAsprofSettings());
            assertEquals(ConfigSource.HUB_PROJECT, config.getProfilerCommandSource());
        }

        @Test
        void aWorkspaceLayerWinsWhenNoProjectLayerSetsIt() {
            InitConfig config = withLayers(base(minimalEnv()),
                    layer(ConfigScope.GLOBAL, ASPROF_SETTINGS + " = \"global\""),
                    layer(ConfigScope.WORKSPACE, ASPROF_SETTINGS + " = \"workspace\""));

            assertEquals("workspace", config.getAsprofSettings());
            assertEquals(ConfigSource.HUB_WORKSPACE, config.getProfilerCommandSource());
        }

        @Test
        void aGlobalLayerAppliesWhenNothingMoreSpecificDoes() {
            InitConfig config = withLayers(base(minimalEnv()),
                    layer(ConfigScope.GLOBAL, ASPROF_SETTINGS + " = \"global\""));

            assertEquals("global", config.getAsprofSettings());
            assertEquals(ConfigSource.HUB_GLOBAL, config.getProfilerCommandSource());
        }

        /**
         * The property the whole layering exists to preserve: whoever deploys the container keeps
         * the last word, so a published file can never take over a pod that set the value itself.
         */
        @Test
        void theEnvironmentBeatsEveryPublishedLayer() {
            Map<String, String> env = new HashMap<>(minimalEnv());
            env.put("JEFFREY_ASPROF_SETTINGS", "from-the-container");

            InitConfig config = withLayers(base(env),
                    layer(ConfigScope.PROJECT, ASPROF_SETTINGS + " = \"project\""));

            assertEquals("from-the-container", config.getAsprofSettings());
            assertEquals(ConfigSource.CONTAINER, config.getProfilerCommandSource());
        }

        @Test
        void noLayerLeavesTheSettingUnsetAndTheSourceBuiltIn() {
            InitConfig config = base(minimalEnv());

            assertNull(config.getAsprofSettings());
            assertEquals(ConfigSource.BUILT_IN, config.getProfilerCommandSource());
            assertTrue(config.getAppliedConfigLayers().isEmpty());
        }

        @Test
        void everyLayerFoundIsReportedForTheSessionMarker() {
            InitConfig config = withLayers(base(minimalEnv()),
                    layer(ConfigScope.GLOBAL, ASPROF_SETTINGS + " = \"global\""),
                    layer(ConfigScope.PROJECT, ASPROF_SETTINGS + " = \"project\""));

            assertEquals(
                    List.of(ConfigScope.GLOBAL, ConfigScope.PROJECT),
                    config.getAppliedConfigLayers().stream().map(AppliedConfigLayer::scope).toList());
            assertEquals("digest-GLOBAL", config.getAppliedConfigLayers().getFirst().digest());
        }

        /**
         * A layer is merged key by key, so a published file that only sets the profiler command
         * must not blank out everything else the container configured.
         */
        @Test
        void aLayerOverridesOnlyTheKeysItSets() {
            Map<String, String> env = new HashMap<>(minimalEnv());
            env.put("JEFFREY_PROJECT_LABEL", "My Service");

            InitConfig config = withLayers(base(env),
                    layer(ConfigScope.WORKSPACE, ASPROF_SETTINGS + " = \"workspace\""));

            assertEquals("My Service", config.getProjectLabel());
            assertEquals("my-service", config.getProjectName());
        }
    }
}
