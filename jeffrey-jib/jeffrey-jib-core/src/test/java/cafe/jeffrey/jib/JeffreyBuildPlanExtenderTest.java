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
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtension;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeffreyBuildPlanExtenderTest {

    private static final String PAYLOAD_ENTRY_PREFIX = "jeffrey-payload/";
    private static final String PAYLOAD_DESCRIPTOR = PAYLOAD_ENTRY_PREFIX + "payload.properties";
    private static final String PAYLOAD_LAYER = "jeffrey-payload";
    private static final byte[] PAYLOAD_BYTES = {1, 2, 3, 4};

    private static final List<String> NATIVE_FILES = List.of(
            "provisioner-linux-amd64", "provisioner-linux-arm64",
            "libasyncProfiler-linux-amd64.so", "libasyncProfiler-linux-arm64.so");
    private static final List<String> JAR_FILES = List.of(
            "provisioner.jar", "libasyncProfiler-linux-amd64.so", "libasyncProfiler-linux-arm64.so");

    @TempDir
    private Path tempDir;

    private JeffreyBuildPlanExtender extender;
    private CapturingLogger logger;
    private Path workDirectory;

    @BeforeEach
    void setUp() throws IOException {
        logger = new CapturingLogger();
        workDirectory = tempDir.resolve("work");
        extender = extenderOver(nativePayloadJar());
    }

    private static JeffreyJibConfig config() {
        return new JeffreyJibConfig();
    }

    /**
     * The extension finds its payload on its own class path — in production the payload jar is a
     * sibling plugin dependency. Here the class path is a loader over exactly the jars a test
     * wants, so "no payload", "two payloads" and "a broken payload" are all real class paths.
     */
    private JeffreyBuildPlanExtender extenderOver(Path... jars) throws IOException {
        URL[] urls = new URL[jars.length];
        for (int i = 0; i < jars.length; i++) {
            urls[i] = jars[i].toUri().toURL();
        }
        // Parent null: the test class path must not leak a payload in.
        ClassLoader payloads = new URLClassLoader(urls, null);
        return new JeffreyBuildPlanExtender(StubExtension.class, payloads, workDirectory);
    }

    private Path nativePayloadJar() throws IOException {
        return payloadJar(descriptor("native", "jeffrey v0.13.22", "async-profiler 4.1"), NATIVE_FILES);
    }

    private Path jarPayloadJar() throws IOException {
        return payloadJar(descriptor("jar", "jeffrey v0.13.22", "async-profiler 4.1"), JAR_FILES);
    }

    private static String descriptor(String kind, String provisionerProvenance, String profilerProvenance) {
        return "kind=" + kind + "\n"
                + "provisioner.provenance=" + provisionerProvenance + "\n"
                + "profiler.provenance=" + profilerProvenance + "\n";
    }

    /** A real payload jar: the descriptor plus the given binaries under the payload prefix. */
    private Path payloadJar(String descriptor, List<String> fileNames) throws IOException {
        Path jar = Files.createTempFile(tempDir, "payload-", ".jar");
        try (OutputStream out = Files.newOutputStream(jar);
             JarOutputStream jarOut = new JarOutputStream(out)) {
            if (descriptor != null) {
                jarOut.putNextEntry(new JarEntry(PAYLOAD_DESCRIPTOR));
                jarOut.write(descriptor.getBytes());
                jarOut.closeEntry();
            }
            for (String fileName : fileNames) {
                jarOut.putNextEntry(new JarEntry(PAYLOAD_ENTRY_PREFIX + fileName));
                jarOut.write(PAYLOAD_BYTES);
                jarOut.closeEntry();
            }
        }
        return jar;
    }

    private static ContainerBuildPlan.Builder planFor(String... architectures) {
        Set<Platform> platforms = new LinkedHashSet<>();
        for (String architecture : architectures) {
            platforms.add(new Platform(architecture, "linux"));
        }
        return ContainerBuildPlan.builder()
                .setEntrypoint(List.of("java", "-jar", "/app/app.jar"))
                .setPlatforms(platforms);
    }

    private static FileEntriesLayer layerNamed(ContainerBuildPlan plan, String name) {
        return (FileEntriesLayer) plan.getLayers().stream()
                .filter(layer -> name.equals(layer.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No layer named " + name));
    }

    private static Map<String, String> entriesOf(FileEntriesLayer layer) {
        Map<String, String> entries = new java.util.LinkedHashMap<>();
        for (FileEntry entry : layer.getEntries()) {
            entries.put(entry.getExtractionPath().toString(), entry.getPermissions().toOctalString());
        }
        return entries;
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
                    "my-test-class");

            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setBaseImage("eclipse-temurin:21-jre")
                    .setEntrypoint(jibEntrypoint)
                    .build();

            ContainerBuildPlan result = extender.extend(input, config(), logger);

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

            ContainerBuildPlan result = extender.extend(input, config(), logger);

            assertEquals(List.of("/usr/local/bin/jeffrey-entrypoint"), result.getEntrypoint());
            assertEquals(jarEntrypoint, result.getCmd(), "java -jar CMD should be preserved");
        }

        @Test
        void addsExactlyOneJeffreyEntrypointLayer() throws Exception {
            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setEntrypoint(List.of("java", "-jar", "/app/app.jar"))
                    .build();

            ContainerBuildPlan result = extender.extend(input, config(), logger);

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

            ContainerBuildPlan result = extender.extend(input, config(), logger);

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

            extender.extend(input, config(), logger);

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

            JeffreyJibConfig config = config();
            config.setEnabled(false);

            ContainerBuildPlan result = extender.extend(input, config, logger);

            assertSame(input, result, "Disabled extension must return the exact same build plan");
            assertEquals(originalEntrypoint, result.getEntrypoint());
            assertTrue(result.getLayers().isEmpty(), "No jeffrey layer should be added");
            assertFalse(Files.exists(workDirectory), "A disabled extension must unpack nothing");
        }

        @Test
        void noFailureWhenEnabledFalseEvenWithMissingEntrypoint() throws Exception {
            // Disabling is a valid "I don't want Jeffrey on this image" gate; it must not trip
            // the entrypoint-presence check.
            ContainerBuildPlan input = ContainerBuildPlan.builder().build();
            JeffreyJibConfig config = config();
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

            JeffreyJibConfig config = config();
            config.setJeffreyHome("/mnt/data/jeffrey");
            config.setBaseConfig("/etc/jeffrey/base.conf");
            config.setOverrideConfig("/etc/jeffrey/override.conf");
            config.setProfilerPath("/opt/vendor/libasyncProfiler.so");
            config.setArgFile("/var/jeffrey/jvm.args");
            config.setProjectName("my-service");

            Map<String, String> env = extender.extend(input, config, logger).getEnvironment();

            assertEquals("/mnt/data/jeffrey", env.get("JEFFREY_HOME"));
            assertEquals("/etc/jeffrey/base.conf", env.get("JEFFREY_BASE_CONFIG"));
            assertEquals("/etc/jeffrey/override.conf", env.get("JEFFREY_OVERRIDE_CONFIG"));
            assertEquals("/opt/vendor/libasyncProfiler.so", env.get("JEFFREY_PROFILER_PATH"));
            assertEquals("/var/jeffrey/jvm.args", env.get("JEFFREY_ARG_FILE"));
            assertEquals("my-service", env.get("JEFFREY_PROJECT_NAME"));

            // Naming a profiler path says the image already carries async-profiler, so that payload
            // is not baked at all. The provisioner always is: it has no such property.
            assertEquals(
                    Set.of("/opt/jeffrey/provisioner"),
                    entriesOf(layerNamed(extender.extend(input, config, logger), PAYLOAD_LAYER)).keySet(),
                    "An explicit profilerPath must not bake an async-profiler payload");
        }

        @Test
        void nullConfigStringsAddNoEnv() throws Exception {
            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setEntrypoint(List.of("java", "-jar", "/app/app.jar"))
                    .build();

            Map<String, String> env = extender.extend(input, config(), logger)
                    .getEnvironment();

            assertFalse(env.containsKey("JEFFREY_HOME"));
            assertFalse(env.containsKey("JEFFREY_BASE_CONFIG"));
            assertFalse(env.containsKey("JEFFREY_OVERRIDE_CONFIG"));
            assertFalse(env.containsKey("JEFFREY_ARG_FILE"));
            assertFalse(env.containsKey("JEFFREY_PROJECT_NAME"));

            // The payload paths are the exception: they describe what the extension just baked
            // into the image, so they are present whether or not anything was configured.
            assertEquals("/opt/jeffrey/provisioner", env.get("JEFFREY_PROVISIONER_PATH"));
            assertEquals("native", env.get("JEFFREY_PROVISIONER_KIND"));
            assertEquals("/opt/jeffrey/libasyncProfiler.so", env.get("JEFFREY_PROFILER_PATH"));
        }

        @Test
        void emptyConfigStringsAreTreatedAsAbsent() throws Exception {
            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setEntrypoint(List.of("java", "-jar", "/app/app.jar"))
                    .build();

            JeffreyJibConfig config = config();
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

            JeffreyJibConfig config = config();
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
                    () -> extender.extend(input, config(), logger));

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
                    () -> extender.extend(input, config(), logger));
        }
    }

    @Nested
    class PayloadBaking {

        @Test
        void bakesNativeProvisionerAndProfilerForSinglePlatform() throws Exception {
            ContainerBuildPlan result = extender.extend(planFor("amd64").build(), config(), logger);

            Map<String, String> entries = entriesOf(layerNamed(result, PAYLOAD_LAYER));
            assertEquals(
                    Map.of("/opt/jeffrey/provisioner", "755",
                            "/opt/jeffrey/libasyncProfiler.so", "644"),
                    entries,
                    "A single-platform image gets unsuffixed names; only the provisioner is executable");

            Map<String, String> env = result.getEnvironment();
            assertEquals("/opt/jeffrey/provisioner", env.get("JEFFREY_PROVISIONER_PATH"));
            assertEquals("native", env.get("JEFFREY_PROVISIONER_KIND"));
            assertEquals("/opt/jeffrey/libasyncProfiler.so", env.get("JEFFREY_PROFILER_PATH"));
        }

        @Test
        void suffixesNamesAndBakesArchPlaceholderForMultiPlatform() throws Exception {
            // JIB layers are not per-platform, so both architectures' files ship in every manifest
            // of the index and the names must differ. The entrypoint resolves {arch} at start-up.
            ContainerBuildPlan result =
                    extender.extend(planFor("amd64", "arm64").build(), config(), logger);

            assertEquals(
                    Set.of("/opt/jeffrey/provisioner-amd64",
                            "/opt/jeffrey/provisioner-arm64",
                            "/opt/jeffrey/libasyncProfiler-amd64.so",
                            "/opt/jeffrey/libasyncProfiler-arm64.so"),
                    entriesOf(layerNamed(result, PAYLOAD_LAYER)).keySet());

            Map<String, String> env = result.getEnvironment();
            assertEquals("/opt/jeffrey/provisioner-{arch}", env.get("JEFFREY_PROVISIONER_PATH"));
            assertEquals("/opt/jeffrey/libasyncProfiler-{arch}.so", env.get("JEFFREY_PROFILER_PATH"));
        }

        @Test
        void jarFlavourBakesOneArchNeutralProvisioner() throws Exception {
            // Which provisioner build an image gets is decided by the payload jar on the class
            // path — the flavour the build declared — not by configuration.
            extender = extenderOver(jarPayloadJar());

            ContainerBuildPlan result =
                    extender.extend(planFor("amd64", "arm64").build(), config(), logger);

            // The provisioner jar is the same file everywhere; only async-profiler stays per-arch.
            assertEquals(
                    Set.of("/opt/jeffrey/provisioner.jar",
                            "/opt/jeffrey/libasyncProfiler-amd64.so",
                            "/opt/jeffrey/libasyncProfiler-arm64.so"),
                    entriesOf(layerNamed(result, PAYLOAD_LAYER)).keySet());

            Map<String, String> env = result.getEnvironment();
            assertEquals("/opt/jeffrey/provisioner.jar", env.get("JEFFREY_PROVISIONER_PATH"));
            assertEquals("jar", env.get("JEFFREY_PROVISIONER_KIND"));
        }

        @Test
        void jarProvisionerIsNotExecutable() throws Exception {
            extender = extenderOver(jarPayloadJar());

            ContainerBuildPlan result = extender.extend(planFor("amd64").build(), config(), logger);

            assertEquals("644",
                    entriesOf(layerNamed(result, PAYLOAD_LAYER)).get("/opt/jeffrey/provisioner.jar"),
                    "The jar is read by a JVM, never executed");
        }

        @Test
        void unpacksOnlyTheArchitecturesBeingBuilt() throws Exception {
            // The payload jar carries both architectures; the image gets only what it targets.
            ContainerBuildPlan result = extender.extend(planFor("amd64").build(), config(), logger);

            assertEquals(
                    List.of("provisioner-linux-amd64", "libasyncProfiler-linux-amd64.so"),
                    layerNamed(result, PAYLOAD_LAYER).getEntries().stream()
                            .map(entry -> entry.getSourceFile().getFileName().toString())
                            .toList(),
                    "An amd64-only build must not unpack arm64 payloads");
        }

        @Test
        void explicitProfilerPathSkipsOnlyThatPayload() throws Exception {
            JeffreyJibConfig config = config();
            config.setProfilerPath("/usr/lib/libasyncProfiler.so");

            ContainerBuildPlan result = extender.extend(planFor("amd64").build(), config, logger);

            assertEquals(
                    Set.of("/opt/jeffrey/provisioner"),
                    entriesOf(layerNamed(result, PAYLOAD_LAYER)).keySet(),
                    "Bring-your-own async-profiler must not also ship ours");
            assertEquals("/usr/lib/libasyncProfiler.so", result.getEnvironment().get("JEFFREY_PROFILER_PATH"));
        }

        @Test
        void theProvisionerIsBakedEvenWhenTheProfilerIsSupplied() throws Exception {
            // provisionerPath does not exist: the provisioner writes the layout Jeffrey Hub reads,
            // so every image gets the one from the payload, and only its build is a choice.
            extender = extenderOver(jarPayloadJar());
            JeffreyJibConfig config = config();
            config.setProfilerPath("/usr/lib/libasyncProfiler.so");

            ContainerBuildPlan result = extender.extend(planFor("amd64").build(), config, logger);

            assertEquals(
                    Set.of("/opt/jeffrey/provisioner.jar"),
                    entriesOf(layerNamed(result, PAYLOAD_LAYER)).keySet());
            Map<String, String> env = result.getEnvironment();
            assertEquals("/opt/jeffrey/provisioner.jar", env.get("JEFFREY_PROVISIONER_PATH"));
            assertEquals("jar", env.get("JEFFREY_PROVISIONER_KIND"));
            assertEquals("/usr/lib/libasyncProfiler.so", env.get("JEFFREY_PROFILER_PATH"));
        }

        @Test
        void namesTheProfilerTheImageSupplies() throws Exception {
            JeffreyJibConfig config = config();
            config.setProfilerPath("/usr/lib/libasyncProfiler.so");

            extender.extend(planFor("amd64").build(), config, logger);

            assertTrue(logger.messages.stream().anyMatch(m ->
                            m.message.contains("neither baked nor version-checked")
                                    && m.message.contains("/usr/lib/libasyncProfiler.so")),
                    "A supplied library has no provenance to print, so the log must name it: "
                            + logger.messages);
        }

        @Test
        void warnsThatProvisionerPathIsNoLongerConfigurable() {
            JeffreyJibConfig config = config();

            JeffreyBuildPlanExtender.applyProperties(
                    config, Map.of("provisionerPath", "/opt/vendor/provisioner"), logger);

            assertTrue(logger.messages.stream().anyMatch(m -> m.level == LogLevel.WARN
                            && m.message.contains("provisionerPath")
                            && m.message.contains("plugin dependency")),
                    "A build still setting the removed property must be told: " + logger.messages);
        }

        @Test
        void warnsThatProvisionerSourceAndPayloadVersionMovedToTheDependency() {
            // Both were properties of the previous design; a build migrating from it must learn
            // where the choice went rather than have its setting silently ignored.
            JeffreyJibConfig config = config();

            JeffreyBuildPlanExtender.applyProperties(
                    config, Map.of("provisionerSource", "jar", "payloadVersion", "0.14.0"), logger);

            List<String> warnings = logger.messages.stream()
                    .filter(m -> m.level == LogLevel.WARN)
                    .map(m -> m.message)
                    .toList();
            assertEquals(2, warnings.size(), warnings.toString());
            assertTrue(warnings.stream().allMatch(m -> m.contains("jeffrey-jib-maven-jar")), warnings.toString());
        }

        @Test
        void unpacksIntoTheWorkDirectoryAndLeavesAnUnchangedPayloadAlone() throws Exception {
            // JIB keys its layer cache on the source path and modification time, so the second
            // build must hand it the very same file, untouched.
            ContainerBuildPlan first = extender.extend(planFor("amd64").build(), config(), logger);
            Path unpacked = layerNamed(first, PAYLOAD_LAYER).getEntries().get(0).getSourceFile();
            assertTrue(unpacked.startsWith(workDirectory), "Unpacked under the work directory: " + unpacked);
            assertEquals("provisioner-linux-amd64", unpacked.getFileName().toString(),
                    "Named after the file in the payload jar: " + unpacked);

            FileTime sentinel = FileTime.fromMillis(0);
            Files.setLastModifiedTime(unpacked, sentinel);

            ContainerBuildPlan second = extender.extend(planFor("amd64").build(), config(), logger);

            assertEquals(unpacked, layerNamed(second, PAYLOAD_LAYER).getEntries().get(0).getSourceFile());
            assertEquals(sentinel, Files.getLastModifiedTime(unpacked), "Identical content must not be rewritten");
        }

        @Test
        void rewritesAPayloadWhoseContentChanged() throws Exception {
            ContainerBuildPlan first = extender.extend(planFor("amd64").build(), config(), logger);
            Path unpacked = layerNamed(first, PAYLOAD_LAYER).getEntries().get(0).getSourceFile();
            Files.write(unpacked, new byte[] {9, 9, 9, 9});

            extender.extend(planFor("amd64").build(), config(), logger);

            assertArrayEquals(PAYLOAD_BYTES, Files.readAllBytes(unpacked),
                    "A stale file of the same size is detected by content and replaced");
        }

        @Test
        void logsWhereEachPayloadCameFrom() throws Exception {
            // The payload jar's version is a jeffrey-jib release; the Jeffrey release and
            // async-profiler version inside it are only knowable from the payload descriptor, so
            // the build log has to say them.
            extender.extend(planFor("amd64").build(), config(), logger);

            assertTrue(logger.messages.stream().anyMatch(m ->
                            m.message.contains("/opt/jeffrey/provisioner (jeffrey v0.13.22)")
                                    && m.message.contains("/opt/jeffrey/libasyncProfiler.so (async-profiler 4.1)")),
                    "Expected the provenance next to each install path; got: " + logger.messages);
        }
    }

    @Nested
    class PayloadFailures {

        @Test
        void failsWhenNoPayloadIsOnTheClassPath() throws Exception {
            // The bare extension was declared instead of a flavour.
            extender = extenderOver();
            ContainerBuildPlan input = planFor("amd64").build();

            JibPluginExtensionException ex = assertThrows(
                    JibPluginExtensionException.class,
                    () -> extender.extend(input, config(), logger));

            assertTrue(ex.getMessage().contains("jeffrey-jib-maven-jar")
                            && ex.getMessage().contains("jeffrey-jib-maven-native"),
                    "The message must name the flavours to declare; got: " + ex.getMessage());
        }

        @Test
        void failsWhenTwoPayloadsAreOnTheClassPath() throws Exception {
            // Both flavours declared: which provisioner wins would depend on class-path order.
            extender = extenderOver(nativePayloadJar(), jarPayloadJar());
            ContainerBuildPlan input = planFor("amd64").build();

            JibPluginExtensionException ex = assertThrows(
                    JibPluginExtensionException.class,
                    () -> extender.extend(input, config(), logger));

            assertTrue(ex.getMessage().contains("more than one payload jar"), ex.getMessage());
        }

        @Test
        void failsOnAnUnknownPayloadKind() throws Exception {
            extender = extenderOver(payloadJar(descriptor("graalvm", "jeffrey v1", "async-profiler 4.1"), NATIVE_FILES));
            ContainerBuildPlan input = planFor("amd64").build();

            JibPluginExtensionException ex = assertThrows(
                    JibPluginExtensionException.class,
                    () -> extender.extend(input, config(), logger));

            assertTrue(ex.getMessage().contains("native") && ex.getMessage().contains("jar"),
                    "The message must list the valid kinds; got: " + ex.getMessage());
        }

        @Test
        void failsOnUnsupportedArchitecture() {
            ContainerBuildPlan input = planFor("ppc64le").build();

            assertThrows(
                    JibPluginExtensionException.class,
                    () -> extender.extend(input, config(), logger));
        }

        @Test
        void failsWhenNoLinuxPlatformIsRequested() {
            ContainerBuildPlan input = ContainerBuildPlan.builder()
                    .setEntrypoint(List.of("java", "-jar", "/app/app.jar"))
                    .setPlatforms(Set.of(new Platform("amd64", "windows")))
                    .build();

            assertThrows(
                    JibPluginExtensionException.class,
                    () -> extender.extend(input, config(), logger));
        }

        @Test
        void failsWhenThePayloadJarLacksABinary() throws Exception {
            // Catches a release that published a payload whose binary was never staged.
            extender = extenderOver(payloadJar(
                    descriptor("native", "jeffrey v1", "async-profiler 4.1"), List.of("provisioner-linux-amd64")));
            ContainerBuildPlan input = planFor("amd64").build();

            JibPluginExtensionException ex = assertThrows(
                    JibPluginExtensionException.class,
                    () -> extender.extend(input, config(), logger));

            assertTrue(ex.getMessage().contains("jeffrey-payload/libasyncProfiler-linux-amd64.so")
                            && ex.getMessage().contains("carries no such file"), ex.getMessage());
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
