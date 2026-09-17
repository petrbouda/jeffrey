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

import cafe.jeffrey.jib.payload.PayloadInstallation;
import cafe.jeffrey.jib.payload.PayloadInstaller;
import cafe.jeffrey.jib.payload.PayloadPlan;
import cafe.jeffrey.jib.payload.PayloadResolutionException;
import cafe.jeffrey.jib.payload.PayloadResolver;
import cafe.jeffrey.jib.payload.ProvisionerSource;
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
 * image supplies its own — into a second layer under {@code /opt/jeffrey}, fetched through the
 * running build's own dependency resolution.
 * An image built this way is self-contained: it needs a shared volume for the recordings it writes,
 * never to find its own tooling.
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

    /** Removed property, still named so a build that sets it is told rather than quietly ignored. */
    private static final String REMOVED_PROVISIONER_PATH = "provisionerPath";
    private static final String LINUX_OS = "linux";
    private static final Set<String> SUPPORTED_ARCHITECTURES = Set.of("amd64", "arm64");

    private final Class<? extends JibPluginExtension> extensionClass;
    private final PayloadResolver payloadResolver;
    private final Path workDirectory;

    /**
     * @param payloadResolver fetches payload artifacts through the build system; only called when
     *                        the configuration actually bakes something, so a build that supplies
     *                        its own binaries never touches the build system's resolver
     * @param workDirectory   where the entrypoint script and payloads are unpacked before JIB
     *                        reads them; see {@link WorkDirectories}
     */
    public JeffreyBuildPlanExtender(
            Class<? extends JibPluginExtension> extensionClass,
            PayloadResolver payloadResolver,
            Path workDirectory) {

        this.extensionClass = extensionClass;
        this.payloadResolver = payloadResolver;
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
     *                                     classpath, if JIB did not produce an entrypoint, or if a
     *                                     payload artifact cannot be resolved
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

        ProvisionerSource source = provisionerSource(config);
        Path wrapperScript = extractEntrypointScript();
        FileEntriesLayer wrapperLayer = FileEntriesLayer.builder()
                .setName(LAYER_NAME)
                .addEntry(wrapperScript, ENTRYPOINT_PATH, EXEC_PERMS)
                .build();

        PayloadInstallation payload = installPayload(buildPlan, config, source, logger);
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
     * Fetches and installs the binaries the entrypoint needs. The provisioner is always baked;
     * only async-profiler can be left out, by a {@code profilerPath} saying the image already
     * provides one, in which case that payload is not resolved at all and costs nothing.
     */
    private PayloadInstallation installPayload(
            ContainerBuildPlan buildPlan,
            JeffreyJibConfig config,
            ProvisionerSource source,
            ExtensionLogger logger) throws JibPluginExtensionException {

        boolean bakeProfiler = isBlank(config.getProfilerPath());
        if (!bakeProfiler) {
            logger.log(LogLevel.LIFECYCLE,
                    "jeffrey-jib: using the async-profiler this image already provides, neither fetched "
                            + "nor version-checked: " + config.getProfilerPath());
        }

        PayloadPlan plan = new PayloadPlan(source, linuxArchitectures(buildPlan), bakeProfiler);
        try {
            return new PayloadInstaller(payloadResolver, payloadVersion(config), workDirectory, logger)
                    .install(plan);
        } catch (PayloadResolutionException e) {
            throw new JibPluginExtensionException(extensionClass, e.getMessage(), e);
        }
    }

    private ProvisionerSource provisionerSource(JeffreyJibConfig config) throws JibPluginExtensionException {
        try {
            return ProvisionerSource.parse(config.getProvisionerSource());
        } catch (IllegalArgumentException e) {
            throw new JibPluginExtensionException(extensionClass, e.getMessage(), e);
        }
    }

    /**
     * There is no default, and no image is exempt: every one carries a provisioner. The payload
     * artifacts are published with each jeffrey-jib release, and
     * which Jeffrey release's binaries a given jeffrey-jib release bundles is a choice made when it
     * was cut — so a guessed version would silently pin an image to a provisioner nobody chose,
     * which is worse than a build that stops and asks.
     */
    private String payloadVersion(JeffreyJibConfig config) throws JibPluginExtensionException {
        String version = config.getPayloadVersion();
        if (!isBlank(version)) {
            return version;
        }
        throw new JibPluginExtensionException(
                extensionClass,
                "jeffrey-jib: '" + JeffreyJibConfig.PAYLOAD_VERSION + "' is required. Set it to the "
                        + "jeffrey-jib release whose payload artifacts this image should carry — normally "
                        + "the same version as the extension itself (for example 0.14.0); each payload "
                        + "records the Jeffrey release and async-profiler version it bundles, and the "
                        + "build log prints them.");
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
     * {@code argFile}, {@code profilerPath}, {@code projectName}, {@code provisionerSource},
     * {@code payloadVersion}. Null / empty values are ignored; unknown keys, and the removed
     * {@code provisionerPath}, are logged at WARN and otherwise ignored.
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
                                + "the one Jeffrey Hub reads; 'provisionerSource' chooses native or jar.");
                case JeffreyJibConfig.ARG_FILE -> config.setArgFile(value);
                case JeffreyJibConfig.PROFILER_PATH -> config.setProfilerPath(value);
                case JeffreyJibConfig.PROJECT_NAME -> config.setProjectName(value);
                case JeffreyJibConfig.PROVISIONER_SOURCE -> config.setProvisionerSource(value);
                case JeffreyJibConfig.PAYLOAD_VERSION -> config.setPayloadVersion(value);
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
