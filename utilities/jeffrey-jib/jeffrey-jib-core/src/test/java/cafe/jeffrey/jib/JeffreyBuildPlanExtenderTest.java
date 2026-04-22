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

package cafe.jeffrey.jib;

import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtension;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeffreyBuildPlanExtenderTest {

    private JeffreyBuildPlanExtender extender;
    private CapturingLogger logger;

    @BeforeEach
    void setUp() {
        extender = new JeffreyBuildPlanExtender(StubExtension.class);
        logger = new CapturingLogger();
    }

    /** Stub so we can satisfy {@code Class<? extends JibPluginExtension>} in tests without
     *  depending on the gradle/maven sibling modules. */
    private static final class StubExtension implements JibPluginExtension {
    }

    @Nested
    class Happy {

        @Test
        void wrapsJibEntrypointAndMovesOriginalToCmd() throws Exception {
            List<String> jibEntrypoint = List.of(
                    "java", "-cp", "@/app/jib-classpath-file",
                    "secondfoundation.klingon.integration.schuetz.KlingonSchuetzService");

            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setBaseImage("eclipse-temurin:21-jre")
                    .setEntrypoint(jibEntrypoint)
                    .build();

            ContainerBuildPlan result = extender.extend(input, new JeffreyJibConfig(), logger);

            assertEquals(
                    List.of("/usr/local/bin/jeffrey-entrypoint"),
                    result.getEntrypoint(),
                    "ENTRYPOINT should be the Jeffrey wrapper");
            assertEquals(
                    jibEntrypoint,
                    result.getCmd(),
                    "Original JIB entrypoint should be preserved verbatim in CMD");
            assertEquals(
                    "eclipse-temurin:21-jre",
                    result.getBaseImage(),
                    "Base image must not be touched by the extension");
        }

        @Test
        void wrapsJarStyleEntrypoint() throws Exception {
            List<String> jarEntrypoint = List.of("java", "-jar", "/app/my-app.jar");

            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setEntrypoint(jarEntrypoint)
                    .build();

            ContainerBuildPlan result = extender.extend(input, new JeffreyJibConfig(), logger);

            assertEquals(List.of("/usr/local/bin/jeffrey-entrypoint"), result.getEntrypoint());
            assertEquals(jarEntrypoint, result.getCmd(), "java -jar CMD should be preserved");
        }

        @Test
        void addsExactlyOneJeffreyEntrypointLayer() throws Exception {
            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setEntrypoint(List.of("java", "-jar", "/app/app.jar"))
                    .build();

            ContainerBuildPlan result = extender.extend(input, new JeffreyJibConfig(), logger);

            List<? extends LayerObject> addedLayers = result.getLayers().stream()
                    .filter(l -> "jeffrey-entrypoint".equals(l.getName()))
                    .toList();
            assertEquals(1, addedLayers.size(), "Exactly one jeffrey-entrypoint layer expected");

            FileEntriesLayer jeffreyLayer = (FileEntriesLayer) addedLayers.get(0);
            assertEquals(1, jeffreyLayer.getEntries().size(), "Layer should contain just the wrapper");
            assertEquals(
                    "/usr/local/bin/jeffrey-entrypoint",
                    jeffreyLayer.getEntries().get(0).getExtractionPath().toString());
            assertEquals(
                    "755",
                    jeffreyLayer.getEntries().get(0).getPermissions().toOctalString(),
                    "Wrapper must be executable");
        }

        @Test
        void preservesExistingLayersAndEnvironment() throws Exception {
            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setEntrypoint(List.of("java", "-jar", "/app/app.jar"))
                    .addEnvironmentVariable("FOO", "bar")
                    .addLayer(FileEntriesLayer.builder().setName("user-layer").build())
                    .build();

            ContainerBuildPlan result = extender.extend(input, new JeffreyJibConfig(), logger);

            assertEquals("bar", result.getEnvironment().get("FOO"));
            long userLayers = result.getLayers().stream()
                    .filter(l -> "user-layer".equals(l.getName()))
                    .count();
            assertEquals(1, userLayers, "Existing user layer must survive the extension");
        }

        @Test
        void emitsLifecycleLogMessage() throws Exception {
            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setEntrypoint(List.of("java", "-jar", "/app/app.jar"))
                    .build();

            extender.extend(input, new JeffreyJibConfig(), logger);

            assertTrue(
                    logger.messages.stream()
                            .anyMatch(m -> m.level == LogLevel.LIFECYCLE
                                    && m.message.contains("jeffrey-jib")),
                    "Expected a lifecycle log announcing the wrap");
        }
    }

    @Nested
    class Disabled {

        @Test
        void returnsBuildPlanUnchangedWhenEnabledFalse() throws Exception {
            List<String> originalEntrypoint = List.of("java", "-jar", "/app/app.jar");
            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setEntrypoint(originalEntrypoint)
                    .build();

            JeffreyJibConfig config = new JeffreyJibConfig();
            config.setEnabled(false);

            ContainerBuildPlan result = extender.extend(input, config, logger);

            assertSame(input, result, "Disabled extension must return the exact same build plan");
            assertEquals(originalEntrypoint, result.getEntrypoint());
            assertTrue(result.getLayers().isEmpty(), "No jeffrey layer should be added");
        }

        @Test
        void noFailureWhenEnabledFalseEvenWithMissingEntrypoint() throws Exception {
            // Disabling is a valid "I don't want Jeffrey on this image" gate; it must not trip
            // the entrypoint-presence check.
            ContainerBuildPlan input = ContainerBuildPlan.builder().build();
            JeffreyJibConfig config = new JeffreyJibConfig();
            config.setEnabled(false);

            ContainerBuildPlan result = extender.extend(input, config, logger);

            assertSame(input, result);
        }
    }

    @Nested
    class EnvDefaults {

        @Test
        void bakesEachConfiguredStringAsImageEnv() throws Exception {
            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setEntrypoint(List.of("java", "-jar", "/app/app.jar"))
                    .build();

            JeffreyJibConfig config = new JeffreyJibConfig();
            config.setJeffreyHome("/mnt/data/jeffrey");
            config.setBaseConfig("/etc/jeffrey/base.conf");
            config.setOverrideConfig("/etc/jeffrey/override.conf");
            config.setCliPath("/opt/jeffrey/bin/jeffrey-cli");
            config.setArgFile("/var/jeffrey/jvm.args");

            Map<String, String> env = extender.extend(input, config, logger).getEnvironment();

            assertEquals("/mnt/data/jeffrey", env.get("JEFFREY_HOME"));
            assertEquals("/etc/jeffrey/base.conf", env.get("JEFFREY_BASE_CONFIG"));
            assertEquals("/etc/jeffrey/override.conf", env.get("JEFFREY_OVERRIDE_CONFIG"));
            assertEquals("/opt/jeffrey/bin/jeffrey-cli", env.get("JEFFREY_CLI_PATH"));
            assertEquals("/var/jeffrey/jvm.args", env.get("JEFFREY_ARG_FILE"));
        }

        @Test
        void nullConfigStringsAddNoEnv() throws Exception {
            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setEntrypoint(List.of("java", "-jar", "/app/app.jar"))
                    .build();

            Map<String, String> env = extender.extend(input, new JeffreyJibConfig(), logger)
                    .getEnvironment();

            assertFalse(env.containsKey("JEFFREY_HOME"));
            assertFalse(env.containsKey("JEFFREY_BASE_CONFIG"));
            assertFalse(env.containsKey("JEFFREY_OVERRIDE_CONFIG"));
            assertFalse(env.containsKey("JEFFREY_CLI_PATH"));
            assertFalse(env.containsKey("JEFFREY_ARG_FILE"));
        }

        @Test
        void emptyConfigStringsAreTreatedAsAbsent() throws Exception {
            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setEntrypoint(List.of("java", "-jar", "/app/app.jar"))
                    .build();

            JeffreyJibConfig config = new JeffreyJibConfig();
            config.setJeffreyHome("");
            config.setBaseConfig("");

            Map<String, String> env = extender.extend(input, config, logger).getEnvironment();

            assertFalse(env.containsKey("JEFFREY_HOME"),
                    "Empty string is indistinguishable from unset for this purpose");
            assertFalse(env.containsKey("JEFFREY_BASE_CONFIG"));
        }

        @Test
        void userEnvironmentSurvivesAlongsideJeffreyEnv() throws Exception {
            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setEntrypoint(List.of("java", "-jar", "/app/app.jar"))
                    .addEnvironmentVariable("SF_ENV", "uat")
                    .addEnvironmentVariable("JEFFREY_HOME", "/will-be-overridden")
                    .build();

            JeffreyJibConfig config = new JeffreyJibConfig();
            config.setJeffreyHome("/mnt/azure/runtime/shared/jeffrey");

            Map<String, String> env = extender.extend(input, config, logger).getEnvironment();

            assertEquals("uat", env.get("SF_ENV"), "Unrelated user env must survive");
            assertEquals("/mnt/azure/runtime/shared/jeffrey", env.get("JEFFREY_HOME"),
                    "Jeffrey config takes precedence for its own keys when the user set one first");
        }
    }

    @Nested
    class FailFast {

        @Test
        void throwsWhenJibProducedNoEntrypoint() {
            ContainerBuildPlan input = ContainerBuildPlan.builder().build();

            JibPluginExtensionException ex = assertThrows(
                    JibPluginExtensionException.class,
                    () -> extender.extend(input, new JeffreyJibConfig(), logger));

            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("mainClass"),
                    "Error message should guide user to fix the root cause; got: " + ex.getMessage());
        }

        @Test
        void throwsWhenEntrypointIsEmptyList() {
            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setEntrypoint(List.of())
                    .build();

            assertThrows(
                    JibPluginExtensionException.class,
                    () -> extender.extend(input, new JeffreyJibConfig(), logger));
        }
    }

    private static final class CapturingLogger implements ExtensionLogger {
        private final List<LogMessage> messages = new ArrayList<>();

        @Override
        public void log(LogLevel level, String message) {
            messages.add(new LogMessage(level, message));
        }
    }

    private record LogMessage(LogLevel level, String message) {
    }
}
