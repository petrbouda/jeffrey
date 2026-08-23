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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provisioner.config.ConfigLayers;
import cafe.jeffrey.provisioner.config.ConfigPaths;
import cafe.jeffrey.provisioner.config.EnvironmentLayer;
import cafe.jeffrey.provisioner.feature.TracingJfrEvents;
import cafe.jeffrey.provisioner.model.HeapDumpType;
import cafe.jeffrey.provisioner.placeholder.EnvPlaceholderSource;
import cafe.jeffrey.provisioner.placeholder.Placeholders;
import cafe.jeffrey.shared.common.CliConstants;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.model.RepositoryType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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

    private static final Logger LOG = LoggerFactory.getLogger(InitConfig.class);

    private static final String DEFAULT_AGENT_RELATIVE_PATH = "libs/current/jeffrey-agent.jar";

    private static final String BUNDLED_PROFILER_DIR = "libs/current";
    private static final String BUNDLED_PROFILER_PREFIX = "libasyncProfiler-";
    private static final String BUNDLED_PROFILER_SUFFIX = ".so";

    /** Names the instance after the container when nothing else does. */
    private static final String ENV_HOSTNAME = "HOSTNAME";

    private static final Pattern PROJECT_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");


    /**
     * Detected runtime arch, null on unsupported platforms. Computed once at class load so
     * the warning is logged at most once per JVM. When null, profiler auto-resolve is skipped
     * and {@link #getProfilerPath()} returns null — the application starts without profiling
     * rather than failing.
     */
    private static final String DETECTED_ARCH = detectArch();

    /**
     * Maps the JVM's os.arch to the binary suffix used for bundled native artifacts
     * (provisioner-${arch}, libasyncProfiler-${arch}.so). Mirrors the case mapping in
     * jeffrey-entrypoint.sh so the auto-resolved path matches what CopyLibsInitializer
     * places in the shared volume. Returns null on unsupported platforms.
     */
    static String detectArch() {
        String osArch = System.getProperty("os.arch");
        return switch (osArch) {
            case "x86_64", "amd64" -> "amd64";
            case "aarch64", "arm64" -> "arm64";
            default -> {
                LOG.warn("Unsupported os.arch for bundled native profiler: os_arch={} - "
                        + "Jeffrey profiling is disabled, application will start without profiling", osArch);
                yield null;
            }
        };
    }

    // Default configuration with all optional fields
    private static final String DEFAULTS = """
            jeffrey-home = ""
            workspaces-dir = ""
            profiler-path = ""
            profiler-config = ""
            repository-type = ""
            project {
                workspace-ref-id = ""
                name = ""
                label = ""
                instance-name = ""
            }
            attributes = {}
            perf-counters { enabled = false }
            # On by default, unlike the agent's own parameter: a provisioned JVM is one being
            # profiled on purpose, and the weaver is inert without Java 25 and jeffrey-events on
            # the class path. JEFFREY_TRACING_ENABLED=false opts a deployment out.
            # jfr-event-settings left empty means the built-in event list
            # (TracingJfrEvents.DEFAULT_SETTINGS); "none" opts out while leaving tracing on.
            tracing { enabled = true, jfr-event-settings = "" }
            heap-dump { enabled = false, type = "exit" }
            jvm-logging { enabled = false, command = "" }
            agent-path = ""
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
        return merge(ConfigFactory.empty(), envLookup);
    }

    static InitConfig fromHoconFile(
            Path baseConfigFile, Path overrideConfigFile, Function<String, String> envLookup) {
        if (!Files.exists(baseConfigFile)) {
            throw new IllegalArgumentException("Base config file does not exist: " + baseConfigFile);
        }

        Config files = ConfigFactory.parseFile(baseConfigFile.toFile());
        if (overrideConfigFile != null) {
            if (!Files.exists(overrideConfigFile)) {
                throw new IllegalArgumentException("Override config file does not exist: " + overrideConfigFile);
            }
            files = ConfigFactory.parseFile(overrideConfigFile.toFile()).withFallback(files);
        }

        return merge(files, envLookup);
    }

    /**
     * Stacks the three sources into one configuration. Precedence is expressed by the order of the
     * layers and nowhere else, so every setting obeys the same rule: what a file declares wins,
     * the environment fills the rest, built-in defaults answer what is left.
     */
    private static InitConfig merge(Config files, Function<String, String> envLookup) {
        Config defaults = ConfigFactory.parseString(DEFAULTS);
        Config declared = ConfigLayers.withoutBlanks(files);
        ConfigLayers.warnAboutUnknownKeys(declared, defaults);

        Config environment = EnvironmentLayer.of(envLookup);
        Config resolved = environment
                .withFallback(filesFor(environment, declared))
                .withFallback(defaults)
                .resolve();

        InitConfig config = new InitConfig(resolved, envLookup);
        config.validate();
        return config;
    }

    /**
     * The file layer, minus the location settings when the environment names one of its own.
     * {@code jeffrey-home} and {@code workspaces-dir} are mutually exclusive, so the winning layer
     * has to take the pair as a unit — otherwise a file that picks one and an environment that
     * picks the other produce a config that fails validation instead of one overriding the other.
     */
    private static Config filesFor(Config environment, Config declared) {
        if (environment.hasPath(ConfigPaths.JEFFREY_HOME) || environment.hasPath(ConfigPaths.WORKSPACES_DIR)) {
            return declared
                    .withoutPath(ConfigPaths.JEFFREY_HOME)
                    .withoutPath(ConfigPaths.WORKSPACES_DIR);
        }
        return declared;
    }

    private final String jeffreyHome;
    private final String workspacesDir;
    private final String profilerPath;
    private final String profilerConfig;
    private final String repositoryType;
    private final String agentPath;
    private final String additionalJvmOptions;

    private final String workspaceRefId;
    private final String projectName;
    private final String projectLabel;
    private final String instanceName;
    private final Map<String, String> attributes;

    private final boolean perfCountersEnabled;
    private final boolean methodTracingEnabled;
    private final String tracingJfrEventSettings;
    private final boolean debugNonSafepointsEnabled;
    private final boolean jdkJavaOptionsEnabled;
    private final HeapDumpType heapDumpType;
    private final String jvmLoggingCommand;

    private final Path envFilePath;
    private final Path argFilePath;
    private final boolean printEnv;
    private final boolean provisionerVerbose;

    /**
     * Reads the merged configuration. Precedence was already settled by the layer merge, so every
     * setting is a plain read; what is left here is the resolution a value needs before anyone can
     * use it — placeholders, the profiler and agent lookups, and the instance name.
     *
     * <p>Resolution happens once, at construction. It used to sit behind the getters, where
     * {@code getInstanceName()} handed out a fresh UUID on every call and the path getters hit the
     * filesystem on every call; both were correct only because the executor happened to ask once.
     */
    private InitConfig(Config resolved, Function<String, String> envLookup) {
        // Phase one of placeholder resolution: everything that can be answered without knowing
        // where this run will put its session. Kubernetes cannot expand $(SF_CLUSTER) for values
        // it did not declare itself, so <<ENV:SF_CLUSTER>> is resolved here instead. Values
        // carrying <<JEFFREY:...>> survive untouched for the JVM-args phase.
        Placeholders placeholders = Placeholders.of(new EnvPlaceholderSource(envLookup));

        this.jeffreyHome = nullIfBlank(resolved.getString(ConfigPaths.JEFFREY_HOME));
        this.workspacesDir = nullIfBlank(resolved.getString(ConfigPaths.WORKSPACES_DIR));
        this.repositoryType = nullIfBlank(resolved.getString(ConfigPaths.REPOSITORY_TYPE));
        this.profilerConfig = nullIfBlank(placeholders.resolve(resolved.getString(ConfigPaths.PROFILER_CONFIG)));
        this.additionalJvmOptions =
                nullIfBlank(placeholders.resolve(resolved.getString(ConfigPaths.ADDITIONAL_JVM_OPTIONS)));

        this.profilerPath = resolveProfilerPath(
                placeholders.resolve(resolved.getString(ConfigPaths.PROFILER_PATH)), jeffreyHome);
        this.agentPath = resolveAgentPath(
                placeholders.resolve(resolved.getString(ConfigPaths.AGENT_PATH)), jeffreyHome);

        this.projectName = nullIfBlank(placeholders.resolve(resolved.getString(ConfigPaths.PROJECT_NAME)));
        this.projectLabel = nullIfBlank(placeholders.resolve(resolved.getString(ConfigPaths.PROJECT_LABEL)));
        this.workspaceRefId = resolveWorkspaceRefId(
                placeholders.resolve(resolved.getString(ConfigPaths.PROJECT_WORKSPACE_REF_ID)));
        this.instanceName = resolveInstanceName(
                placeholders.resolve(resolved.getString(ConfigPaths.PROJECT_INSTANCE_NAME)), envLookup);
        this.attributes = resolveAttributes(
                resolved.getObject(ConfigPaths.ATTRIBUTES).unwrapped(), placeholders);

        this.perfCountersEnabled = resolved.getBoolean(ConfigPaths.PERF_COUNTERS_ENABLED);
        this.methodTracingEnabled = resolved.getBoolean(ConfigPaths.TRACING_ENABLED);
        this.tracingJfrEventSettings = valueOrDefault(
                resolved.getString(ConfigPaths.TRACING_JFR_EVENT_SETTINGS), TracingJfrEvents.DEFAULT_SETTINGS);
        this.debugNonSafepointsEnabled = resolved.getBoolean(ConfigPaths.DEBUG_NON_SAFEPOINTS_ENABLED);
        this.jdkJavaOptionsEnabled = resolved.getBoolean(ConfigPaths.JDK_JAVA_OPTIONS_ENABLED);
        this.heapDumpType = resolved.getBoolean(ConfigPaths.HEAP_DUMP_ENABLED)
                ? HeapDumpType.resolve(resolved.getString(ConfigPaths.HEAP_DUMP_TYPE))
                : null;
        this.jvmLoggingCommand = resolved.getBoolean(ConfigPaths.JVM_LOGGING_ENABLED)
                ? nullIfBlank(placeholders.resolve(resolved.getString(ConfigPaths.JVM_LOGGING_COMMAND)))
                : null;

        this.envFilePath = toPath(resolved.getString(ConfigPaths.ENV_FILE));
        this.argFilePath = toPath(placeholders.resolve(resolved.getString(ConfigPaths.ARG_FILE)));
        this.printEnv = resolved.getBoolean(ConfigPaths.PRINT_ENV);
        this.provisionerVerbose = resolved.getBoolean(ConfigPaths.PROVISIONER_VERBOSE);
    }

    // ==================== Resolution ====================

    /**
     * The explicit path, or the profiler bundled under {@code jeffrey-home} for this machine's
     * architecture. Null when neither is available — the application then starts without profiling
     * rather than failing.
     */
    private static String resolveProfilerPath(String explicitPath, String jeffreyHome) {
        String explicit = nullIfBlank(explicitPath);
        if (explicit != null) {
            return explicit;
        }
        if (jeffreyHome == null || DETECTED_ARCH == null) {
            return null;
        }
        Path candidate = Path.of(jeffreyHome).resolve(BUNDLED_PROFILER_DIR)
                .resolve(BUNDLED_PROFILER_PREFIX + DETECTED_ARCH + BUNDLED_PROFILER_SUFFIX);
        return Files.exists(candidate) ? candidate.toString() : null;
    }

    /**
     * The explicit path, or the agent JAR bundled under {@code jeffrey-home}. Null when neither is
     * available — agent-dependent features are skipped downstream.
     */
    private static String resolveAgentPath(String explicitPath, String jeffreyHome) {
        String explicit = nullIfBlank(explicitPath);
        if (explicit != null) {
            return explicit;
        }
        if (jeffreyHome == null) {
            return null;
        }
        Path candidate = Path.of(jeffreyHome).resolve(DEFAULT_AGENT_RELATIVE_PATH);
        return Files.exists(candidate) ? candidate.toString() : null;
    }

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

    public String getProfilerConfig() {
        return profilerConfig;
    }

    public String getRepositoryType() {
        return repositoryType;
    }

    public String getAgentPath() {
        return agentPath;
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

    /** Whether the agent records {@code @Traced} methods as spans. */
    public boolean isMethodTracingEnabled() {
        return methodTracingEnabled;
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

    /** The {@code -Xlog} command, or null when JVM logging is off. */
    public String getJvmLoggingCommand() {
        return jvmLoggingCommand;
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
