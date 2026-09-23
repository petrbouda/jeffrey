/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.jib;

import cafe.jeffrey.jib.payload.PayloadDescriptor;
import cafe.jeffrey.jib.payload.PayloadInstallation;
import cafe.jeffrey.jib.payload.PayloadInstaller;
import cafe.jeffrey.jib.payload.PayloadPlan;
import cafe.jeffrey.jib.payload.PayloadResolutionException;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtension;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core build-plan transformation shared by the Maven and Gradle JIB plugin extensions.
 *
 * <p>The extender installs a shell wrapper at {@code /usr/local/bin/jeffrey-entrypoint} into a new
 * image layer, promotes that wrapper to the image ENTRYPOINT, and moves JIB's auto-derived java
 * command ({@code java -cp @/app/jib-classpath-file <MainClass>}) into CMD. At container start the
 * wrapper runs {@code provisioner init} and then execs the original command with the
 * provisioner-produced argfile prepended.
 *
 * <p>It also bakes the binaries that run needs — the provisioner, and async-profiler unless the
 * image supplies its own — into a second layer under {@code /opt/jeffrey}. They come from the
 * payload jar the build put on the extension's class path by declaring a flavour of the extension
 * ({@code jeffrey-jib-maven-jar}, {@code jeffrey-jib-maven-native}, or the Gradle equivalents) as
 * the jib plugin dependency; nothing is downloaded by the extension itself. An image built this way
 * is self-contained: it needs a shared volume for the recordings it writes, never to find its own
 * tooling.
 *
 * <p>Non-null string fields on the {@link JeffreyJibConfig} are baked as image-level ENV
 * defaults on the build plan (wrapper reads them at runtime; Kubernetes pod env still wins).
 * If {@code config.isEnabled() == false}, the build plan is returned unchanged — image has
 * no wrapper and no Jeffrey layer.
 *
 * <p>Note on JVM flags in CMD: whatever JIB produced in its entrypoint (including any JVM
 * flags upstream plugins added via {@code container.jvmFlags}) is moved verbatim into CMD.
 * At runtime those flags are passed after the Jeffrey argfile ({@code java @/tmp/jvm.args
 * <CMD…>}), so Java evaluates them after the argfile — single-value {@code -XX:} options in
 * CMD will silently override their argfile counterparts. If Jeffrey is meant to own JVM
 * configuration, clear those flags at the call site (e.g. {@code jib.container.jvmFlags =
 * emptyList()}) before invoking JIB; this extender does not second-guess the build script.
 */
public final class JeffreyBuildPlanExtender {

    private static final String ENTRYPOINT_RESOURCE = "/cafe/jeffrey/jib/jeffrey-entrypoint.sh";
    private static final String ENTRYPOINT_FILE_NAME = "jeffrey-entrypoint.sh";
    private static final AbsoluteUnixPath ENTRYPOINT_PATH =
            AbsoluteUnixPath.get("/usr/local/bin/jeffrey-entrypoint");
    private static final FilePermissions EXEC_PERMS = FilePermissions.fromOctalString("755");
    private static final String LAYER_NAME = "jeffrey-entrypoint";

    /** Removed properties, still named so a build that sets them is told rather than quietly ignored. */
    private static final String REMOVED_PROVISIONER_PATH = "provisionerPath";
    private static final String REMOVED_PROVISIONER_SOURCE = "provisionerSource";
    private static final String REMOVED_PAYLOAD_VERSION = "payloadVersion";
    private static final String LINUX_OS = "linux";
    private static final Set<String> SUPPORTED_ARCHITECTURES = Set.of("amd64", "arm64");

    private final Class<? extends JibPluginExtension> extensionClass;
    private final ClassLoader payloads;
    private final Path workDirectory;

    /**
     * @param payloads      the class loader the payload jar is visible through — the extension's
     *                      own, since the payload arrives as a sibling plugin dependency
     * @param workDirectory where the entrypoint script and payloads are unpacked before JIB
     *                      reads them; see {@link WorkDirectories}
     */
    public JeffreyBuildPlanExtender(
            Class<? extends JibPluginExtension> extensionClass,
            ClassLoader payloads,
            Path workDirectory) {

        this.extensionClass = extensionClass;
        this.payloads = payloads;
        this.workDirectory = workDirectory;
    }

    /**
     * Transforms the incoming build plan by adding the wrapper layer, rewriting the entrypoint,
     * and baking any requested image-level ENV defaults.
     *
     * @param buildPlan JIB-produced build plan (must already have an entrypoint — we move it to cmd)
     * @param config    user configuration (never null; pass {@code new JeffreyJibConfig()} for defaults)
     * @param logger    JIB extension logger for build-time messages
     * @return the transformed build plan (or the input unchanged when {@code config.enabled == false})
     * @throws JibPluginExtensionException if the wrapper script cannot be extracted from the
     *                                     classpath, if JIB did not produce an entrypoint, or if
     *                                     no (or more than one) payload jar is on the class path
     */
    public ContainerBuildPlan extend(
            ContainerBuildPlan buildPlan,
            JeffreyJibConfig config,
            ExtensionLogger logger) throws JibPluginExtensionException {

        if (!config.isEnabled()) {
            logger.log(LogLevel.LIFECYCLE, "jeffrey-jib: disabled (enabled=false); build plan unchanged");
            return buildPlan;
        }

        List<String> originalEntrypoint = buildPlan.getEntrypoint();
        if (originalEntrypoint == null || originalEntrypoint.isEmpty()) {
            throw new JibPluginExtensionException(
                    extensionClass,
                    "JIB did not produce an entrypoint. The Jeffrey JIB extension wraps JIB's "
                            + "auto-derived java command; let JIB set the mainClass and do not "
                            + "override container.entrypoint.");
        }

        Path wrapperScript = extractEntrypointScript();
        FileEntriesLayer wrapperLayer = FileEntriesLayer.builder()
                .setName(LAYER_NAME)
                .addEntry(wrapperScript, ENTRYPOINT_PATH, EXEC_PERMS)
                .build();

        PayloadInstallation payload = installPayload(buildPlan, config, logger);
        Map<String, String> extraEnv = collectEnvDefaults(config);

        logger.log(LogLevel.LIFECYCLE,
                "jeffrey-jib: wrapping entrypoint with " + ENTRYPOINT_PATH
                        + "; original entrypoint moved to CMD: " + originalEntrypoint
                        + (extraEnv.isEmpty() ? "" : "; image ENV defaults: " + extraEnv));

        ContainerBuildPlan.Builder builder = buildPlan.toBuilder()
                .addLayer(wrapperLayer)
                .setEntrypoint(List.of(ENTRYPOINT_PATH.toString()))
                .setCmd(originalEntrypoint);

        // The payload is its own layer so the entrypoint layer stays a single small file and the
        // binaries show up as a distinct, individually cacheable line in `docker history`.
        builder.addLayer(payload.layer());

        // Merge our env on top of whatever the build plan already had (user-set JIB
        // container.environment survives; our keys win only for the specific keys we set). The
        // explicit configuration goes on last: naming a path is how an operator says the image
        // already carries that binary, so it must beat what the payload installed.
        Map<String, String> merged = new LinkedHashMap<>(buildPlan.getEnvironment());
        merged.putAll(payload.environment());
        merged.putAll(extraEnv);
        if (!merged.equals(buildPlan.getEnvironment())) {
            builder.setEnvironment(merged);
        }

        return builder.build();
    }

    /**
     * Installs the binaries the entrypoint needs from the payload jar on the class path. The
     * provisioner is always baked, and which build of it is decided by the flavour the build
     * declared; only async-profiler can be left out, by a {@code profilerPath} saying the image
     * already provides one.
     */
    private PayloadInstallation installPayload(
            ContainerBuildPlan buildPlan,
            JeffreyJibConfig config,
            ExtensionLogger logger) throws JibPluginExtensionException {

        boolean bakeProfiler = isBlank(config.getProfilerPath());
        if (!bakeProfiler) {
            logger.log(LogLevel.LIFECYCLE,
                    "jeffrey-jib: using the async-profiler this image already provides, neither baked "
                            + "nor version-checked: " + config.getProfilerPath());
        }

        try {
            PayloadDescriptor descriptor = PayloadDescriptor.discover(payloads);
            PayloadPlan plan = new PayloadPlan(descriptor, linuxArchitectures(buildPlan), bakeProfiler);
            return new PayloadInstaller(payloads, workDirectory, logger).install(plan);
        } catch (PayloadResolutionException e) {
            throw new JibPluginExtensionException(extensionClass, e.getMessage(), e);
        }
    }

    /**
     * The Linux architectures this build targets, in declaration order. JIB always populates the
     * build plan with at least {@code linux/amd64}, so an empty result means every requested
     * platform was something Jeffrey cannot profile.
     */
    private Set<String> linuxArchitectures(ContainerBuildPlan buildPlan) throws JibPluginExtensionException {
        Set<String> architectures = new LinkedHashSet<>();
        for (Platform platform : buildPlan.getPlatforms()) {
            if (!LINUX_OS.equals(platform.getOs())) {
                continue;
            }
            if (!SUPPORTED_ARCHITECTURES.contains(platform.getArchitecture())) {
                throw new JibPluginExtensionException(
                        extensionClass,
                        "jeffrey-jib: unsupported target architecture '" + platform.getArchitecture()
                                + "'. Jeffrey publishes payloads for " + SUPPORTED_ARCHITECTURES + ".");
            }
            architectures.add(platform.getArchitecture());
        }
        if (architectures.isEmpty()) {
            throw new JibPluginExtensionException(
                    extensionClass,
                    "jeffrey-jib: no linux platform in the build plan (" + buildPlan.getPlatforms()
                            + "). Jeffrey profiles Linux containers only.");
        }
        return architectures;
    }

    /**
     * The explicit configuration, baked as image ENV. The provisioner's own path and kind are not
     * here: they describe what the extension installed, so they come from the payload installation
     * and nothing in the build configuration can contradict them.
     */
    private static Map<String, String> collectEnvDefaults(JeffreyJibConfig config) {
        Map<String, String> env = new LinkedHashMap<>();
        putIfPresent(env, "JEFFREY_HOME", config.getJeffreyHome());
        putIfPresent(env, "JEFFREY_BASE_CONFIG", config.getBaseConfig());
        putIfPresent(env, "JEFFREY_OVERRIDE_CONFIG", config.getOverrideConfig());
        putIfPresent(env, "JEFFREY_ARG_FILE", config.getArgFile());
        putIfPresent(env, "JEFFREY_PROFILER_PATH", config.getProfilerPath());
        putIfPresent(env, "JEFFREY_PROJECT_NAME", config.getProjectName());
        return env;
    }

    private static void putIfPresent(Map<String, String> env, String key, String value) {
        if (!isBlank(value)) {
            env.put(key, value);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Merges a {@code Map<String, String>} of JIB plugin-extension properties into the given
     * config. Used by both the Gradle and Maven extension entry points so consumers can use
     * the string-based {@code properties} DSL — which works uniformly across build systems
     * without requiring JIB's ObjectFactory-based instantiation of the typed config class.
     *
     * <p>Recognised keys map one-to-one to the {@link JeffreyJibConfig} setters:
     * {@code enabled}, {@code jeffreyHome}, {@code baseConfig}, {@code overrideConfig},
     * {@code argFile}, {@code profilerPath}, {@code projectName}. Null / empty values are ignored;
     * unknown keys, and the removed {@code provisionerPath}, {@code provisionerSource} and
     * {@code payloadVersion}, are logged at WARN and otherwise ignored.
     */
    public static void applyProperties(
            JeffreyJibConfig config,
            Map<String, String> properties,
            ExtensionLogger logger) {

        if (properties == null || properties.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.isEmpty()) {
                continue;
            }
            switch (key) {
                case JeffreyJibConfig.ENABLED -> config.setEnabled(Boolean.parseBoolean(value));
                case JeffreyJibConfig.JEFFREY_HOME -> config.setJeffreyHome(value);
                case JeffreyJibConfig.BASE_CONFIG -> config.setBaseConfig(value);
                case JeffreyJibConfig.OVERRIDE_CONFIG -> config.setOverrideConfig(value);
                case REMOVED_PROVISIONER_PATH -> logger.log(
                        LogLevel.WARN,
                        "jeffrey-jib: 'provisionerPath' is no longer configurable and is ignored. The "
                                + "extension always bakes the provisioner, because the layout it writes is "
                                + "the one Jeffrey Hub reads; the plugin dependency chooses native or jar.");
                case REMOVED_PROVISIONER_SOURCE, REMOVED_PAYLOAD_VERSION -> logger.log(
                        LogLevel.WARN,
                        "jeffrey-jib: '" + key + "' is no longer a property and is ignored. The provisioner "
                                + "build and version now come from the jib plugin dependency: declare "
                                + "jeffrey-jib-maven-jar or jeffrey-jib-maven-native (or the jeffrey-jib-gradle-* "
                                + "equivalents) instead of the bare extension.");
                case JeffreyJibConfig.ARG_FILE -> config.setArgFile(value);
                case JeffreyJibConfig.PROFILER_PATH -> config.setProfilerPath(value);
                case JeffreyJibConfig.PROJECT_NAME -> config.setProjectName(value);
                default -> logger.log(
                        LogLevel.WARN,
                        "jeffrey-jib: unknown plugin-extension property '" + key + "'; ignored");
            }
        }
    }

    /**
     * Writes the wrapper script into the work directory, and only when it differs from what a
     * previous build left there: JIB's layer cache keys on the file's modification time, so
     * rewriting identical bytes would still cost a cache miss.
     */
    private Path extractEntrypointScript() throws JibPluginExtensionException {
        try (InputStream in = JeffreyBuildPlanExtender.class.getResourceAsStream(ENTRYPOINT_RESOURCE)) {
            if (in == null) {
                throw new JibPluginExtensionException(
                        extensionClass,
                        "jeffrey-entrypoint.sh not found on classpath at " + ENTRYPOINT_RESOURCE);
            }
            byte[] script = in.readAllBytes();
            Files.createDirectories(workDirectory);
            Path target = workDirectory.resolve(ENTRYPOINT_FILE_NAME);
            if (!Files.isRegularFile(target) || !Arrays.equals(Files.readAllBytes(target), script)) {
                Files.write(target, script);
            }
            return target;
        } catch (IOException e) {
            throw new JibPluginExtensionException(
                    extensionClass, "Failed to extract jeffrey-entrypoint.sh from classpath", e);
        }
    }
}
