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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import cafe.jeffrey.provisioner.config.ConfigLayers;
import cafe.jeffrey.provisioner.config.ConfigPaths;
import cafe.jeffrey.provisioner.config.ContainerLayers;
import cafe.jeffrey.provisioner.config.EnvironmentLayer;
import cafe.jeffrey.provisioner.config.VolumeConfigLayer;
import cafe.jeffrey.provisioner.feature.TracingJfrEvents;
import cafe.jeffrey.provisioner.model.HeapDumpType;
import cafe.jeffrey.provisioner.placeholder.EnvPlaceholderSource;
import cafe.jeffrey.provisioner.placeholder.Placeholders;
import cafe.jeffrey.shared.common.CliConstants;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.config.ConfigSource;
import cafe.jeffrey.shared.common.config.ConfigType;
import cafe.jeffrey.shared.common.config.ScopedConfigLayout;
import cafe.jeffrey.shared.common.model.RepositoryType;
import cafe.jeffrey.shared.common.model.repository.AppliedConfigLayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Provisioner initialization configuration, merged from four layers — highest priority first:
 * {@code JEFFREY_*} environment variables ({@link cafe.jeffrey.provisioner.config.EnvironmentLayer})
 * &gt; override HOCON file &gt; base HOCON file &gt; built-in defaults.
 *
 * <p>The rule is the same for every setting, flags included: the environment wins, then a
 * configuration file, then the built-in defaults. Precedence lives in the order the layers are
 * stacked and nowhere else, so there is no per-setting exception to remember.
 *
 * <p>The environment sits on top because of when each source is written. A configuration file is
 * baked into the image at build time, usually by jeffrey-jib; environment variables are set at
 * deploy time on the pod. Putting the file first would leave an operator no way to change a single
 * setting without rebuilding the image. A key a file spells out but leaves blank counts as
 * undeclared, so a file can pre-declare every key without shadowing the defaults.
 *
 * <p>The HOCON files are optional — a container can be fully configured through environment
 * variables alone ({@link #fromEnvironment()}), which removes the need to mount a config file for
 * the common case.
 */
public class InitConfig {

    /** Names the instance after the container when nothing else does. */
    private static final String ENV_HOSTNAME = "HOSTNAME";

    private static final Pattern PROJECT_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");


    /**
     * Every setting this tool reads, with the value that applies when no layer sets one. Also the
     * list {@code warnAboutUnknownKeys} checks a file against, and package-private so a test can
     * assert that every publishable configuration type names a key that appears here.
     */
    static final String DEFAULTS = """
            jeffrey-home = ""
            workspaces-dir = ""
            profiler-path = ""
            asprof-settings = ""
            repository-type = ""
            project {
                workspace-ref-id = ""
                name = ""
                label = ""
                instance-name = ""
            }
            attributes = {}
            perf-counters { enabled = false }
            # Lowers the JFR thresholds a span is read at. On by default: a provisioned JVM is one
            # being profiled on purpose, and the settings cost nothing in an application that emits
            # no spans. JEFFREY_TRACING_ENABLED=false opts a deployment out.
            # jfr-event-settings left empty means the built-in event list
            # (TracingJfrEvents.DEFAULT_SETTINGS); "none" opts out while leaving tracing on.
            tracing { enabled = true, jfr-event-settings = "" }
            heap-dump { enabled = false, type = "exit" }
            # Off by default, and deliberately the opposite of the other switches here. This one
            # is a claim about the application rather than about the JVM: it says the
            # jeffrey-heartbeat library is on its class path, which this tool cannot see and
            # cannot arrange. Declared wrongly it is not inert but actively misleading — the hub
            # holds the session to a deadline nothing will meet and finishes it at its start
            # timestamp, seconds after the JVM came up. Turn it on once the dependency is there.
            heartbeat { enabled = false }
            jdk-java-options { enabled = false }
            additional-jvm-options = ""
            debug-non-safepoints { enabled = true }
            env-file = ""
            arg-file = "/tmp/jvm.args"
            print-env = false
            provisioner-verbose = false
            """;

    /**
     * Creates an InitConfig from a base HOCON configuration file with an optional override file.
     *
     * @param baseConfigFile     path to the base HOCON configuration file
     * @param overrideConfigFile path to an override HOCON configuration file (nullable)
     * @return validated InitConfig instance
     */
    public static InitConfig fromHoconFile(Path baseConfigFile, Path overrideConfigFile) {
        return fromHoconFile(baseConfigFile, overrideConfigFile, System::getenv);
    }

    /**
     * Creates an InitConfig purely from {@code JEFFREY_*} environment variables and built-in
     * defaults — no HOCON file involved. This is the zero-file setup path: a container only
     * needs {@code JEFFREY_HOME} (usually baked by jeffrey-jib) and {@code JEFFREY_PROJECT_NAME}
     * to be fully configured.
     */
    public static InitConfig fromEnvironment() {
        return fromEnvironment(System::getenv);
    }

    static InitConfig fromEnvironment(Function<String, String> envLookup) {
        return merge(ConfigFactory.empty(), ConfigFactory.empty(), envLookup);
    }

    static InitConfig fromHoconFile(
            Path baseConfigFile, Path overrideConfigFile, Function<String, String> envLookup) {
        if (!Files.exists(baseConfigFile)) {
            throw new IllegalArgumentException("Base config file does not exist: " + baseConfigFile);
        }

        Config baseFile = ConfigFactory.parseFile(baseConfigFile.toFile());
        Config overrideFile = ConfigFactory.empty();
        if (overrideConfigFile != null) {
            if (!Files.exists(overrideConfigFile)) {
                throw new IllegalArgumentException("Override config file does not exist: " + overrideConfigFile);
            }
            overrideFile = ConfigFactory.parseFile(overrideConfigFile.toFile());
        }

        return merge(overrideFile, baseFile, envLookup);
    }

    /**
     * Gathers the container's own layers and resolves them with no published file yet, which is all
     * that is needed to learn where this run belongs. {@link #withVolumeLayers} does the second pass.
     */
    private static InitConfig merge(Config overrideFile, Config baseFile, Function<String, String> envLookup) {
        Config defaults = ConfigFactory.parseString(DEFAULTS);
        Config declaredOverride = ConfigLayers.withoutBlanks(overrideFile);
        Config declaredBase = ConfigLayers.withoutBlanks(baseFile);
        ConfigLayers.warnAboutUnknownKeys(declaredOverride.withFallback(declaredBase), defaults);

        ContainerLayers layers = new ContainerLayers(
                EnvironmentLayer.of(envLookup), declaredOverride, declaredBase, defaults);

        return create(layers, List.of(), envLookup);
    }

    /**
     * The same configuration read again with the hub-published files merged in. They can only be
     * found once the workspace and project directories are known, and those come from this
     * configuration, so the second pass is not an optimisation but the only possible order.
     *
     * <p>A new instance rather than a mutation: every value is resolved at construction, and half
     * of them would otherwise have to be recomputed by hand.</p>
     */
    public InitConfig withVolumeLayers(List<VolumeConfigLayer> volumeLayers) {
        return create(containerLayers, volumeLayers, envLookup);
    }

    private static InitConfig create(
            ContainerLayers layers, List<VolumeConfigLayer> volumeLayers, Function<String, String> envLookup) {

        InitConfig config = new InitConfig(layers, volumeLayers, envLookup);
        config.validate();
        return config;
    }

    /**
     * Which layer supplied the async-profiler command, decided by asking the layers rather than by
     * inspecting the merged value's origin: a layer either declares the key or it does not, and
     * that answer does not depend on how the config library describes where a value came from.
     */
    private static ConfigSource resolveCommandSource(
            ContainerLayers layers, List<VolumeConfigLayer> volumeLayers) {

        String path = ScopedConfigLayout.hoconPath(ConfigType.ASPROF_SETTINGS);
        if (layers.declares(path)) {
            return ConfigSource.CONTAINER;
        }
        ConfigSource source = ConfigSource.BUILT_IN;
        // Weakest first, so the most specific layer that declares the key is the one left standing
        for (VolumeConfigLayer layer : volumeLayers) {
            if (layer.config().hasPath(path)) {
                source = ConfigSource.ofScope(layer.scope());
            }
        }
        return source;
    }

    /** Kept so a second pass can restack the same container layers with the published files. */
    private final ContainerLayers containerLayers;
    private final Function<String, String> envLookup;

    private final ConfigSource profilerCommandSource;
    private final List<AppliedConfigLayer> appliedConfigLayers;

    private final String jeffreyHome;
    private final String workspacesDir;
    private final String profilerPath;
    private final String asprofSettings;
    private final String repositoryType;
    private final boolean heartbeatEnabled;
    private final String additionalJvmOptions;

    private final String workspaceRefId;
    private final String projectName;
    private final String projectLabel;
    private final String instanceName;
    private final Map<String, String> attributes;

    private final boolean perfCountersEnabled;
    private final boolean spanTracingEnabled;
    private final String tracingJfrEventSettings;
    private final boolean debugNonSafepointsEnabled;
    private final boolean jdkJavaOptionsEnabled;
    private final HeapDumpType heapDumpType;

    private final Path envFilePath;
    private final Path argFilePath;
    private final boolean printEnv;
    private final boolean provisionerVerbose;

    /**
     * Reads the merged configuration. Precedence was already settled by the layer merge, so every
     * setting is a plain read; what is left here is the resolution a value needs before anyone can
     * use it — placeholders, the profiler lookup, and the instance name.
     *
     * <p>Resolution happens once, at construction. It used to sit behind the getters, where
     * {@code getInstanceName()} handed out a fresh UUID on every call and the path getters hit the
     * filesystem on every call; both were correct only because the executor happened to ask once.
     */
    private InitConfig(
            ContainerLayers containerLayers,
            List<VolumeConfigLayer> volumeLayers,
            Function<String, String> envLookup) {

        this.containerLayers = containerLayers;
        this.envLookup = envLookup;
        this.profilerCommandSource = resolveCommandSource(containerLayers, volumeLayers);
        this.appliedConfigLayers = volumeLayers.stream()
                .map(VolumeConfigLayer::applied)
                .toList();

        Config resolved = containerLayers.resolve(volumeLayers);

        // Phase one of placeholder resolution: everything that can be answered without knowing
        // where this run will put its session. Kubernetes cannot expand $(SF_CLUSTER) for values
        // it did not declare itself, so <<ENV:SF_CLUSTER>> is resolved here instead. Values
        // carrying <<JEFFREY:...>> survive untouched for the JVM-args phase.
        Placeholders placeholders = Placeholders.of(new EnvPlaceholderSource(envLookup));

        this.jeffreyHome = nullIfBlank(resolved.getString(ConfigPaths.JEFFREY_HOME));
        this.workspacesDir = nullIfBlank(resolved.getString(ConfigPaths.WORKSPACES_DIR));
        this.repositoryType = nullIfBlank(resolved.getString(ConfigPaths.REPOSITORY_TYPE));
        this.asprofSettings = nullIfBlank(placeholders.resolve(resolved.getString(ConfigPaths.ASPROF_SETTINGS)));
        this.additionalJvmOptions =
                nullIfBlank(placeholders.resolve(resolved.getString(ConfigPaths.ADDITIONAL_JVM_OPTIONS)));

        // Null when nothing named a profiler: the application then starts without profiling rather
        // than failing. The path itself is baked into the image by the jeffrey-jib build extension,
        // or named explicitly by whoever provides their own async-profiler.
        this.profilerPath = nullIfBlank(placeholders.resolve(resolved.getString(ConfigPaths.PROFILER_PATH)));
        this.heartbeatEnabled = resolved.getBoolean(ConfigPaths.HEARTBEAT_ENABLED);

        this.projectName = nullIfBlank(placeholders.resolve(resolved.getString(ConfigPaths.PROJECT_NAME)));
        this.projectLabel = nullIfBlank(placeholders.resolve(resolved.getString(ConfigPaths.PROJECT_LABEL)));
        this.workspaceRefId = resolveWorkspaceRefId(
                placeholders.resolve(resolved.getString(ConfigPaths.PROJECT_WORKSPACE_REF_ID)));
        this.instanceName = resolveInstanceName(
                placeholders.resolve(resolved.getString(ConfigPaths.PROJECT_INSTANCE_NAME)), envLookup);
        this.attributes = resolveAttributes(
                resolved.getObject(ConfigPaths.ATTRIBUTES).unwrapped(), placeholders);

        this.perfCountersEnabled = resolved.getBoolean(ConfigPaths.PERF_COUNTERS_ENABLED);
        this.spanTracingEnabled = resolved.getBoolean(ConfigPaths.TRACING_ENABLED);
        this.tracingJfrEventSettings = valueOrDefault(
                resolved.getString(ConfigPaths.TRACING_JFR_EVENT_SETTINGS), TracingJfrEvents.DEFAULT_SETTINGS);
        this.debugNonSafepointsEnabled = resolved.getBoolean(ConfigPaths.DEBUG_NON_SAFEPOINTS_ENABLED);
        this.jdkJavaOptionsEnabled = resolved.getBoolean(ConfigPaths.JDK_JAVA_OPTIONS_ENABLED);
        this.heapDumpType = resolved.getBoolean(ConfigPaths.HEAP_DUMP_ENABLED)
                ? HeapDumpType.resolve(resolved.getString(ConfigPaths.HEAP_DUMP_TYPE))
                : null;

        this.envFilePath = toPath(resolved.getString(ConfigPaths.ENV_FILE));
        this.argFilePath = toPath(placeholders.resolve(resolved.getString(ConfigPaths.ARG_FILE)));
        this.printEnv = resolved.getBoolean(ConfigPaths.PRINT_ENV);
        this.provisionerVerbose = resolved.getBoolean(ConfigPaths.PROVISIONER_VERBOSE);
    }

    // ==================== Resolution ====================

    /**
     * Reference ID of the workspace on the target Jeffrey server. The workspace must already
     * exist — create it via the local UI's "Create Workspace" flow before pointing the provisioner
     * at it. Events for an unknown reference ID are dropped on the server side with a warning,
     * unless the hub opts into auto-creation via {@code jeffrey.hub.workspaces.auto-create=true},
     * in which case the workspace is created on the first incoming event.
     */
    private static String resolveWorkspaceRefId(String configured) {
        String value = nullIfBlank(configured);
        return value != null ? value : CliConstants.DEFAULT_WORKSPACE_REF_ID;
    }

    /** The configured name, the container's hostname, or a generated id — in that order. */
    private static String resolveInstanceName(String configured, Function<String, String> envLookup) {
        String value = nullIfBlank(configured);
        if (value != null) {
            return value;
        }
        String hostname = nullIfBlank(envLookup.apply(ENV_HOSTNAME));
        return hostname != null ? hostname : IDGenerator.generate();
    }

    /** Resolves placeholders in the keys and values of the {@code attributes} object. */
    private static Map<String, String> resolveAttributes(Map<String, Object> attributes, Placeholders placeholders) {
        Map<String, String> resolved = new HashMap<>();
        for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
            if (attribute.getValue() == null) {
                continue;
            }
            String value = attribute.getValue() instanceof String stringValue
                    ? placeholders.resolve(stringValue)
                    : attribute.getValue().toString();
            resolved.put(placeholders.resolve(attribute.getKey()), value);
        }
        return Map.copyOf(resolved);
    }

    private static Path toPath(String value) {
        String path = nullIfBlank(value);
        return path != null ? Path.of(path) : null;
    }

    private static String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static String valueOrDefault(String value, String fallback) {
        String configured = nullIfBlank(value);
        return configured != null ? configured : fallback;
    }

    // ==================== Accessors ====================

    public String getJeffreyHome() {
        return jeffreyHome;
    }

    public String getWorkspacesDir() {
        return workspacesDir;
    }

    /** Whether this run lays its directories out under {@code jeffrey-home}. */
    public boolean useJeffreyHome() {
        return jeffreyHome != null;
    }

    public String getProfilerPath() {
        return profilerPath;
    }

    /**
     * The async-profiler command as configured, before placeholders that need the session layout
     * and before the feature flags are appended. Null when nothing set it, which is what sends the
     * resolver to the built-in default.
     */
    public String getAsprofSettings() {
        return asprofSettings;
    }

    /** Which layer {@link #getAsprofSettings()} came from, for the session marker. */
    public ConfigSource getProfilerCommandSource() {
        return profilerCommandSource;
    }

    /** The hub-published files that were merged, weakest first; empty when none were found. */
    public List<AppliedConfigLayer> getAppliedConfigLayers() {
        return appliedConfigLayers;
    }

    public String getRepositoryType() {
        return repositoryType;
    }

    /**
     * Whether this session expects the {@code jeffrey-heartbeat} library to report liveness.
     *
     * <p>Declared rather than detected: whether the library is on the application's class path is
     * a build-time fact, and this tool only writes JVM arguments. It travels three ways — into the
     * argfile as {@code -Djeffrey.heartbeat.enabled}, into the {@code .env} for a deployment that
     * sources one, and into the session marker, so the hub knows whether to hold this session to
     * its heartbeat deadline.</p>
     *
     * <p><b>Off unless a deployment says otherwise.</b> An application that does not carry the
     * dependency reports nothing, and a session that claimed it would is finished at its own start
     * timestamp seconds after the JVM came up — so the default has to be the side that is merely
     * late rather than the side that is wrong. An undeclared session is closed when the instance's
     * next session appears instead.</p>
     */
    public boolean isHeartbeatEnabled() {
        return heartbeatEnabled;
    }

    public String getAdditionalJvmOptions() {
        return additionalJvmOptions;
    }

    public String getWorkspaceRefId() {
        return workspaceRefId;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getProjectLabel() {
        return projectLabel;
    }

    public String getInstanceName() {
        return instanceName;
    }

    /** Free-form metadata, empty rather than null when none was configured. */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    public boolean isPerfCountersEnabled() {
        return perfCountersEnabled;
    }

    /**
     * Whether this session records spans, which is what decides if the JFR thresholds a span is
     * read at are lowered. It says nothing about instrumentation: spans come from the application's own
     * {@code Tracer} calls and from the {@code jeffrey-tracing-*} instrumentation libraries.
     */
    public boolean isSpanTracingEnabled() {
        return spanTracingEnabled;
    }

    /**
     * The JFR event settings a traced session lowers its thresholds with, as
     * {@code -XX:StartFlightRecording} spells them. Never blank: an unset value falls back to
     * {@link TracingJfrEvents#DEFAULT_SETTINGS}, and {@code "none"} switches the recording off.
     */
    public String getTracingJfrEventSettings() {
        return tracingJfrEventSettings;
    }

    public boolean isDebugNonSafepointsEnabled() {
        return debugNonSafepointsEnabled;
    }

    public boolean isJdkJavaOptionsEnabled() {
        return jdkJavaOptionsEnabled;
    }

    /** The kind of heap dump to arm, or null when heap dumps are off. */
    public HeapDumpType resolveHeapDumpType() {
        return heapDumpType;
    }

    public RepositoryType resolveRepositoryType() {
        return repositoryType != null ? RepositoryType.resolve(repositoryType) : RepositoryType.ASYNC_PROFILER;
    }

    public Path getEnvFilePath() {
        return envFilePath;
    }

    public Path getArgFilePath() {
        return argFilePath;
    }

    public boolean isPrintEnv() {
        return printEnv;
    }

    public boolean isProvisionerVerbose() {
        return provisionerVerbose;
    }

    // ==================== Validation ====================

    private void validate() {
        if (jeffreyHome == null && workspacesDir == null) {
            throw new IllegalArgumentException("Either 'jeffrey-home' or 'workspaces-dir' must be specified");
        }

        if (jeffreyHome != null && workspacesDir != null) {
            throw new IllegalArgumentException("Cannot specify both 'jeffrey-home' and 'workspaces-dir'");
        }

        if (projectName == null) {
            throw new IllegalArgumentException(
                    "'project.name' must be specified (HOCON 'project.name' or env JEFFREY_PROJECT_NAME)");
        }

        if (!PROJECT_NAME_PATTERN.matcher(projectName).matches()) {
            throw new IllegalArgumentException(
                    "Project name can only contain alphanumeric characters, underscores, and dashes");
        }
    }
}
