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

import cafe.jeffrey.jib.payload.ArtifactCoordinates;
import cafe.jeffrey.jib.payload.PayloadResolutionException;
import cafe.jeffrey.jib.payload.PayloadResolver;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeffreyBuildPlanExtenderTest {

    private static final String PAYLOAD_VERSION = "0.13.22";
    private static final String PAYLOAD_ENTRY_PREFIX = "jeffrey-payload/";
    private static final String PAYLOAD_LAYER = "jeffrey-payload";

    @TempDir
    private Path tempDir;

    private JeffreyBuildPlanExtender extender;
    private CapturingLogger logger;
    private StubPayloadResolver resolver;
    private Path workDirectory;

    @BeforeEach
    void setUp() throws IOException {
        logger = new CapturingLogger();
        resolver = new StubPayloadResolver(payloadJar("payload.bin"));
        workDirectory = tempDir.resolve("work");
        extender = new JeffreyBuildPlanExtender(StubExtension.class, resolver, workDirectory);
    }

    /**
     * Payloads are mandatory-versioned, so every test that expects a successful extend has to say
     * which version it wants. A helper keeps that out of each test's body.
     */
    private static JeffreyJibConfig config() {
        JeffreyJibConfig config = new JeffreyJibConfig();
        config.setPayloadVersion(PAYLOAD_VERSION);
        return config;
    }

    /** A real jar carrying one file under the prefix the extension unpacks from. */
    private Path payloadJar(String... entryNames) throws IOException {
        return payloadJar(new Manifest(), entryNames);
    }

    /** As above, with a manifest — how a published payload states where its binary came from. */
    private Path payloadJar(Manifest manifest, String... entryNames) throws IOException {
        Path jar = Files.createTempFile(tempDir, "payload-", ".jar");
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (OutputStream out = Files.newOutputStream(jar);
             JarOutputStream jarOut = new JarOutputStream(out, manifest)) {
            for (String entryName : entryNames) {
                jarOut.putNextEntry(new JarEntry(PAYLOAD_ENTRY_PREFIX + entryName));
                jarOut.write(new byte[] {1, 2, 3, 4});
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
            assertTrue(resolver.requested.isEmpty(), "A disabled extension must resolve nothing");
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
            config.setProvisionerPath("/opt/jeffrey/bin/provisioner");
            config.setArgFile("/var/jeffrey/jvm.args");
            config.setProjectName("my-service");

            Map<String, String> env = extender.extend(input, config, logger).getEnvironment();

            assertEquals("/mnt/data/jeffrey", env.get("JEFFREY_HOME"));
            assertEquals("/etc/jeffrey/base.conf", env.get("JEFFREY_BASE_CONFIG"));
            assertEquals("/etc/jeffrey/override.conf", env.get("JEFFREY_OVERRIDE_CONFIG"));
            assertEquals("/opt/jeffrey/bin/provisioner", env.get("JEFFREY_PROVISIONER_PATH"));
            assertEquals("/var/jeffrey/jvm.args", env.get("JEFFREY_ARG_FILE"));
            assertEquals("my-service", env.get("JEFFREY_PROJECT_NAME"));

            // Naming a provisioner path says the image already carries one, so that payload is
            // not fetched at all; async-profiler was not named, so it still is.
            assertTrue(resolver.requested.stream()
                            .noneMatch(c -> c.artifactId().contains("native")),
                    "An explicit provisionerPath must not resolve a provisioner payload");
            assertTrue(resolver.requested.stream()
                            .anyMatch(c -> c.artifactId().contains("profiler")),
                    "profilerPath was not set, so async-profiler must still be baked");
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
        void jarSourceBakesOneArchNeutralProvisioner() throws Exception {
            JeffreyJibConfig config = config();
            config.setProvisionerSource("jar");

            ContainerBuildPlan result =
                    extender.extend(planFor("amd64", "arm64").build(), config, logger);

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
            JeffreyJibConfig config = config();
            config.setProvisionerSource("jar");

            ContainerBuildPlan result = extender.extend(planFor("amd64").build(), config, logger);

            assertEquals("644",
                    entriesOf(layerNamed(result, PAYLOAD_LAYER)).get("/opt/jeffrey/provisioner.jar"),
                    "The jar is read by a JVM, never executed");
        }

        @Test
        void resolvesOnlyTheArchitecturesBeingBuilt() throws Exception {
            extender.extend(planFor("amd64").build(), config(), logger);

            assertEquals(
                    List.of("linux-amd64", "linux-amd64"),
                    resolver.requested.stream().map(ArtifactCoordinates::classifier).toList(),
                    "An amd64-only build must not download arm64 payloads");
        }

        @Test
        void resolvesEveryPayloadAtTheConfiguredVersion() throws Exception {
            JeffreyJibConfig config = config();
            config.setPayloadVersion("9.9.9");

            extender.extend(planFor("amd64").build(), config, logger);

            assertTrue(resolver.requested.stream().allMatch(c -> "9.9.9".equals(c.version())),
                    "payloadVersion must reach every payload: " + resolver.requested);
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
        void noPayloadLayerWhenBothPathsAreSupplied() throws Exception {
            JeffreyJibConfig config = config();
            config.setProvisionerPath("/usr/bin/provisioner");
            config.setProfilerPath("/usr/lib/libasyncProfiler.so");

            ContainerBuildPlan result = extender.extend(planFor("amd64").build(), config, logger);

            assertTrue(result.getLayers().stream().noneMatch(l -> PAYLOAD_LAYER.equals(l.getName())),
                    "An image that supplies both binaries pays for no payload layer");
            assertTrue(resolver.requested.isEmpty());
        }

        @Test
        void payloadVersionIsNotRequiredWhenNothingIsBaked() throws Exception {
            JeffreyJibConfig config = new JeffreyJibConfig();
            config.setProvisionerPath("/usr/bin/provisioner");
            config.setProfilerPath("/usr/lib/libasyncProfiler.so");

            ContainerBuildPlan result = extender.extend(planFor("amd64").build(), config, logger);

            assertEquals(List.of("/usr/local/bin/jeffrey-entrypoint"), result.getEntrypoint());
        }

        @Test
        void namesTheBinariesTheImageSupplies() throws Exception {
            JeffreyJibConfig config = config();
            config.setProvisionerPath("/opt/vendor/provisioner");
            config.setProfilerPath("/usr/lib/libasyncProfiler.so");

            extender.extend(planFor("amd64").build(), config, logger);

            assertTrue(logger.messages.stream().anyMatch(m ->
                            m.message.contains("neither fetched nor version-checked")
                                    && m.message.contains("provisioner=/opt/vendor/provisioner (kind=native)")
                                    && m.message.contains("profiler=/usr/lib/libasyncProfiler.so")),
                    "A supplied binary has no provenance to print, so the log must name it: " + logger.messages);
        }

        @Test
        void warnsWhenASuppliedProvisionerDoesNotMatchItsKind() throws Exception {
            JeffreyJibConfig config = config();
            config.setProvisionerPath("/opt/vendor/provisioner.jar");

            extender.extend(planFor("amd64").build(), config, logger);

            assertTrue(logger.messages.stream().anyMatch(m -> m.level == LogLevel.WARN
                            && m.message.contains("Set provisionerSource=jar")),
                    "A jar left on the native default fails open at container start: " + logger.messages);
        }

        @Test
        void explicitJarProvisionerPathStillBakesTheJarKind() throws Exception {
            // The entrypoint can only tell a jar from a native binary by JEFFREY_PROVISIONER_KIND.
            // Bringing your own jar must declare it the same way baking ours does, or the wrapper
            // would run the executable-bit check against a jar and fail open.
            JeffreyJibConfig config = config();
            config.setProvisionerSource("jar");
            config.setProvisionerPath("/opt/vendor/provisioner.jar");

            ContainerBuildPlan result = extender.extend(planFor("amd64").build(), config, logger);

            Map<String, String> env = result.getEnvironment();
            assertEquals("/opt/vendor/provisioner.jar", env.get("JEFFREY_PROVISIONER_PATH"));
            assertEquals("jar", env.get("JEFFREY_PROVISIONER_KIND"));
            assertEquals(
                    Set.of("/opt/jeffrey/libasyncProfiler.so"),
                    entriesOf(layerNamed(result, PAYLOAD_LAYER)).keySet(),
                    "Only the profiler is still baked");
        }

        @Test
        void platformsAreNotInspectedWhenNothingIsBaked() throws Exception {
            // An image that brings both binaries may target whatever Jeffrey has no payload for;
            // the platform check exists to refuse baking a payload that does not exist.
            JeffreyJibConfig config = new JeffreyJibConfig();
            config.setProvisionerPath("/usr/bin/provisioner");
            config.setProfilerPath("/usr/lib/libasyncProfiler.so");

            ContainerBuildPlan result = extender.extend(planFor("s390x").build(), config, logger);

            assertEquals(List.of("/usr/local/bin/jeffrey-entrypoint"), result.getEntrypoint());
            assertTrue(resolver.requested.isEmpty());
        }

        @Test
        void unpacksIntoTheWorkDirectoryAndLeavesAnUnchangedPayloadAlone() throws Exception {
            // JIB keys its layer cache on the source path and modification time, so the second
            // build must hand it the very same file, untouched.
            ContainerBuildPlan first = extender.extend(planFor("amd64").build(), config(), logger);
            Path unpacked = layerNamed(first, PAYLOAD_LAYER).getEntries().get(0).getSourceFile();
            assertTrue(unpacked.startsWith(workDirectory), "Unpacked under the work directory: " + unpacked);
            assertTrue(unpacked.toString().contains("jeffrey-jib-payload-native-" + PAYLOAD_VERSION + "-linux-amd64"),
                    "Path keyed by coordinates so versions never collide: " + unpacked);

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

            assertArrayEquals(new byte[] {1, 2, 3, 4}, Files.readAllBytes(unpacked),
                    "A stale file of the same size is detected by checksum and replaced");
        }

        @Test
        void logsWhereEachPayloadCameFrom() throws Exception {
            // payloadVersion names a jeffrey-jib release; the Jeffrey release and async-profiler
            // version inside it are only knowable from the payload manifests, so the build log
            // has to say them.
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().putValue("Jeffrey-Payload-Provenance", "jeffrey v0.13.22");
            resolver.returnJar(payloadJar(manifest, "provisioner"));

            extender.extend(planFor("amd64").build(), config(), logger);

            assertTrue(logger.messages.stream().anyMatch(m ->
                            m.message.contains("/opt/jeffrey/provisioner (jeffrey v0.13.22)")),
                    "Expected the provenance next to the install path; got: " + logger.messages);
        }
    }

    @Nested
    class PayloadFailures {

        @Test
        void failsWhenPayloadVersionIsMissing() {
            ContainerBuildPlan input = planFor("amd64").build();

            JibPluginExtensionException ex = assertThrows(
                    JibPluginExtensionException.class,
                    () -> extender.extend(input, new JeffreyJibConfig(), logger));

            assertTrue(ex.getMessage().contains("payloadVersion"),
                    "The message must name the property to set; got: " + ex.getMessage());
        }

        @Test
        void failsWhenAPayloadCannotBeResolved() {
            resolver.failWith("offline");
            ContainerBuildPlan input = planFor("amd64").build();

            JibPluginExtensionException ex = assertThrows(
                    JibPluginExtensionException.class,
                    () -> extender.extend(input, config(), logger));

            assertTrue(ex.getMessage().contains("jeffrey-jib-payload-native"),
                    "The message must name the artifact; got: " + ex.getMessage());
        }

        @Test
        void failsOnUnknownProvisionerSource() {
            JeffreyJibConfig config = config();
            config.setProvisionerSource("graalvm");
            ContainerBuildPlan input = planFor("amd64").build();

            JibPluginExtensionException ex = assertThrows(
                    JibPluginExtensionException.class,
                    () -> extender.extend(input, config, logger));

            assertTrue(ex.getMessage().contains("native") && ex.getMessage().contains("jar"),
                    "The message must list the valid values; got: " + ex.getMessage());
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
        void failsWhenThePayloadJarIsEmpty() throws Exception {
            // Catches a release that published a payload whose binary was never staged.
            resolver.returnJar(payloadJar());
            ContainerBuildPlan input = planFor("amd64").build();

            JibPluginExtensionException ex = assertThrows(
                    JibPluginExtensionException.class,
                    () -> extender.extend(input, config(), logger));

            assertTrue(ex.getMessage().contains("carries no file"), ex.getMessage());
        }

        @Test
        void failsWhenThePayloadJarCarriesMoreThanOneFile() throws Exception {
            resolver.returnJar(payloadJar("provisioner", "stray.txt"));
            ContainerBuildPlan input = planFor("amd64").build();

            JibPluginExtensionException ex = assertThrows(
                    JibPluginExtensionException.class,
                    () -> extender.extend(input, config(), logger));

            assertTrue(ex.getMessage().contains("expected exactly one"), ex.getMessage());
        }
    }

    /** Records what the extension asked for and hands back a real, inspectable payload jar. */
    private static final class StubPayloadResolver implements PayloadResolver {

        private final List<ArtifactCoordinates> requested = new ArrayList<>();
        private Path payloadJar;
        private String failure;

        private StubPayloadResolver(Path payloadJar) {
            this.payloadJar = payloadJar;
        }

        private void returnJar(Path jar) {
            this.payloadJar = jar;
        }

        private void failWith(String reason) {
            this.failure = reason;
        }

        @Override
        public Path resolve(ArtifactCoordinates coordinates) throws PayloadResolutionException {
            requested.add(coordinates);
            if (failure != null) {
                throw new PayloadResolutionException(coordinates, failure);
            }
            return payloadJar;
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
