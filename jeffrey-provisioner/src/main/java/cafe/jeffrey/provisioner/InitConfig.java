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
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigUtil;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provisioner.config.ConfigPaths;
import cafe.jeffrey.provisioner.config.EnvironmentLayer;
import cafe.jeffrey.provisioner.model.HeapDumpType;
import cafe.jeffrey.provisioner.placeholder.EnvPlaceholderSource;
import cafe.jeffrey.provisioner.placeholder.Placeholders;
import cafe.jeffrey.shared.common.CliConstants;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.model.RepositoryType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Provisioner initialization configuration, merged from four layers — highest priority first:
 * override HOCON file &gt; base HOCON file &gt; {@code JEFFREY_*} environment variables
 * ({@link cafe.jeffrey.provisioner.config.EnvironmentLayer}) &gt; built-in defaults.
 *
 * <p>The rule is the same for every setting, flags included: what a configuration file declares
 * wins, the environment fills in the rest, defaults answer what is left. Precedence lives in the
 * order the layers are stacked and nowhere else, so there is no per-setting exception to remember.
 * A key a file spells out but leaves blank counts as undeclared, which is what lets a file
 * pre-declare every key and still take values from the environment.
 *
 * <p>The HOCON files are optional — a container can be fully configured through environment
 * variables alone ({@link #fromEnvironment()}), which removes the need to mount a config file for
 * the common case.
 */
public class InitConfig {

    private static final Logger LOG = LoggerFactory.getLogger(InitConfig.class);

    private static final String DEFAULT_AGENT_RELATIVE_PATH = "libs/current/jeffrey-agent.jar";


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
            tracing { enabled = true }
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
        Config declared = withoutBlanks(files);
        warnAboutUnknownKeys(declared, defaults);

        Config resolved = declared
                .withFallback(environmentFor(declared, envLookup))
                .withFallback(defaults)
                .resolve();

        InitConfig config = fromConfig(resolved, envLookup);
        config.validate();
        return config;
    }

    /**
     * The environment layer, minus whichever location setting would contradict the file. The two
     * are mutually exclusive, so a config file that picks one must not be dragged into a conflict
     * by an image that bakes the other into {@code JEFFREY_HOME}.
     */
    private static Config environmentFor(Config declared, Function<String, String> envLookup) {
        Config environment = EnvironmentLayer.of(envLookup);
        if (declared.hasPath(ConfigPaths.WORKSPACES_DIR)) {
            return environment.withoutPath(ConfigPaths.JEFFREY_HOME);
        }
        if (declared.hasPath(ConfigPaths.JEFFREY_HOME)) {
            return environment.withoutPath(ConfigPaths.WORKSPACES_DIR);
        }
        return environment;
    }

    /**
     * Drops blank string entries so a file can pre-declare a key it does not set. Config files are
     * routinely written with every key spelled out and the unused ones left empty; without this a
     * merge would treat that empty string as a deliberate value and shadow the environment.
     */
    private static Config withoutBlanks(Config config) {
        List<String> blankPaths = new ArrayList<>();
        collectBlankPaths(config.root(), List.of(), blankPaths);

        Config stripped = config;
        for (String path : blankPaths) {
            stripped = stripped.withoutPath(path);
        }
        return stripped;
    }

    private static void collectBlankPaths(ConfigObject object, List<String> prefix, List<String> blankPaths) {
        for (Map.Entry<String, ConfigValue> entry : object.entrySet()) {
            List<String> path = new ArrayList<>(prefix);
            path.add(entry.getKey());

            if (entry.getValue() instanceof ConfigObject nested) {
                collectBlankPaths(nested, path, blankPaths);
            } else if (isBlankString(entry.getValue())) {
                blankPaths.add(ConfigUtil.joinPath(path));
            }
        }
    }

    /**
     * Whether the value is a blank string. An unresolved {@code ${...}} substitution cannot be
     * inspected yet and is reported as non-blank, leaving it to the full resolve after the merge.
     */
    private static boolean isBlankString(ConfigValue value) {
        try {
            return value.valueType() == ConfigValueType.STRING && ((String) value.unwrapped()).isBlank();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * HOCON discards keys nobody reads, which turns a typo — or a block documenting a feature that
     * was never implemented — into silence. Naming them costs one line and saves the bug report.
     */
    private static void warnAboutUnknownKeys(Config declared, Config defaults) {
        Set<String> known = defaults.root().keySet();
        for (String key : declared.root().keySet()) {
            if (!known.contains(key)) {
                LOG.warn("Ignoring unknown configuration key: key={} known={}", key, known);
            }
        }
    }

    /**
     * Reads the merged configuration. Every setting is a plain read now that precedence has already
     * been settled by the layer merge, so this method describes the shape of the configuration and
     * nothing else.
     */
    private static InitConfig fromConfig(Config resolved, Function<String, String> envLookup) {
        InitConfig config = new InitConfig();

        // Phase one of placeholder resolution: everything that can be answered without knowing
        // where this run will put its session. Kubernetes cannot expand $(SF_CLUSTER) for values
        // it did not declare itself, so <<ENV:SF_CLUSTER>> is resolved here instead. Values
        // carrying <<JEFFREY:...>> survive untouched for the JVM-args phase.
        Placeholders placeholders = Placeholders.of(new EnvPlaceholderSource(envLookup));

        config.setJeffreyHome(resolved.getString(ConfigPaths.JEFFREY_HOME));
        config.setWorkspacesDir(resolved.getString(ConfigPaths.WORKSPACES_DIR));
        config.setRepositoryType(resolved.getString(ConfigPaths.REPOSITORY_TYPE));
        config.setEnvFilePath(resolved.getString(ConfigPaths.ENV_FILE));
        config.setPrintEnv(resolved.getBoolean(ConfigPaths.PRINT_ENV));
        config.setProvisionerVerbose(resolved.getBoolean(ConfigPaths.PROVISIONER_VERBOSE));

        config.setProfilerPath(placeholders.resolve(resolved.getString(ConfigPaths.PROFILER_PATH)));
        config.setProfilerConfig(placeholders.resolve(resolved.getString(ConfigPaths.PROFILER_CONFIG)));
        config.setAgentPath(placeholders.resolve(resolved.getString(ConfigPaths.AGENT_PATH)));
        config.setArgFilePath(placeholders.resolve(resolved.getString(ConfigPaths.ARG_FILE)));
        config.setAdditionalJvmOptions(
                placeholders.resolve(resolved.getString(ConfigPaths.ADDITIONAL_JVM_OPTIONS)));

        ProjectConfig project = new ProjectConfig();
        project.setName(placeholders.resolve(resolved.getString(ConfigPaths.PROJECT_NAME)));
        project.setLabel(placeholders.resolve(resolved.getString(ConfigPaths.PROJECT_LABEL)));
        project.setWorkspaceRefId(placeholders.resolve(resolved.getString(ConfigPaths.PROJECT_WORKSPACE_REF_ID)));
        project.setInstanceName(placeholders.resolve(resolved.getString(ConfigPaths.PROJECT_INSTANCE_NAME)));
        config.setProject(project);

        config.setAttributes(
                resolveAttributes(resolved.getObject(ConfigPaths.ATTRIBUTES).unwrapped(), placeholders));

        PerfCountersConfig perfCounters = new PerfCountersConfig();
        perfCounters.setEnabled(resolved.getBoolean(ConfigPaths.PERF_COUNTERS_ENABLED));
        config.setPerfCounters(perfCounters);

        MethodTracingConfig methodTracing = new MethodTracingConfig();
        methodTracing.setEnabled(resolved.getBoolean(ConfigPaths.TRACING_ENABLED));
        config.setMethodTracing(methodTracing);

        JdkJavaOptionsConfig jdkJavaOptions = new JdkJavaOptionsConfig();
        jdkJavaOptions.setEnabled(resolved.getBoolean(ConfigPaths.JDK_JAVA_OPTIONS_ENABLED));
        config.setJdkJavaOptions(jdkJavaOptions);

        DebugNonSafepointsConfig debugNonSafepoints = new DebugNonSafepointsConfig();
        debugNonSafepoints.setEnabled(resolved.getBoolean(ConfigPaths.DEBUG_NON_SAFEPOINTS_ENABLED));
        config.setDebugNonSafepoints(debugNonSafepoints);

        HeapDumpConfig heapDump = new HeapDumpConfig();
        heapDump.setEnabled(resolved.getBoolean(ConfigPaths.HEAP_DUMP_ENABLED));
        heapDump.setType(resolved.getString(ConfigPaths.HEAP_DUMP_TYPE));
        config.setHeapDump(heapDump);

        JvmLoggingConfig jvmLogging = new JvmLoggingConfig();
        jvmLogging.setEnabled(resolved.getBoolean(ConfigPaths.JVM_LOGGING_ENABLED));
        jvmLogging.setCommand(placeholders.resolve(resolved.getString(ConfigPaths.JVM_LOGGING_COMMAND)));
        config.setJvmLogging(jvmLogging);

        return config;
    }

    /** Resolves placeholders in the keys and values of the HOCON {@code attributes} object. */
    private static Map<String, Object> resolveAttributes(Map<String, Object> attributes, Placeholders placeholders) {
        Map<String, Object> resolved = new HashMap<>();
        for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
            Object value = attribute.getValue() instanceof String stringValue
                    ? placeholders.resolve(stringValue)
                    : attribute.getValue();
            resolved.put(placeholders.resolve(attribute.getKey()), value);
        }
        return resolved;
    }

    private String envFilePath;
    private String argFilePath;
    private boolean printEnv;
    private boolean provisionerVerbose;

    private String jeffreyHome;
    private String workspacesDir;
    private String profilerPath;
    private String profilerConfig;
    private String repositoryType;
    private String agentPath;
    private ProjectConfig project;
    private PerfCountersConfig perfCounters;
    private MethodTracingConfig methodTracing;
    private HeapDumpConfig heapDump;
    private JvmLoggingConfig jvmLogging;
    private JdkJavaOptionsConfig jdkJavaOptions;
    private String additionalJvmOptions;
    private DebugNonSafepointsConfig debugNonSafepoints;
    private Map<String, Object> attributes;

    public Path getEnvFilePath() {
        String value = nullIfBlank(envFilePath);
        return value != null ? Path.of(value) : null;
    }

    public void setEnvFilePath(String envFilePath) {
        this.envFilePath = envFilePath;
    }

    public Path getArgFilePath() {
        String value = nullIfBlank(argFilePath);
        return value != null ? Path.of(value) : null;
    }

    public void setArgFilePath(String argFilePath) {
        this.argFilePath = argFilePath;
    }

    public boolean isPrintEnv() {
        return printEnv;
    }

    public void setPrintEnv(boolean printEnv) {
        this.printEnv = printEnv;
    }

    public boolean isProvisionerVerbose() {
        return provisionerVerbose;
    }

    public void setProvisionerVerbose(boolean provisionerVerbose) {
        this.provisionerVerbose = provisionerVerbose;
    }

    public String getJeffreyHome() {
        return nullIfBlank(jeffreyHome);
    }

    public void setJeffreyHome(String jeffreyHome) {
        this.jeffreyHome = jeffreyHome;
    }

    public String getWorkspacesDir() {
        return nullIfBlank(workspacesDir);
    }

    public void setWorkspacesDir(String workspacesDir) {
        this.workspacesDir = workspacesDir;
    }

    public ProjectConfig getProject() {
        return project;
    }

    public void setProject(ProjectConfig project) {
        this.project = project;
    }

    // Delegation methods for project fields (backwards compatible API)
    public String getWorkspaceRefId() {
        return project != null ? project.getWorkspaceRefId() : null;
    }

    public String getProjectName() {
        return project != null ? project.getName() : null;
    }

    public String getProjectLabel() {
        return project != null ? project.getLabel() : null;
    }

    /**
     * Returns the instance name with fallback resolution:
     * 1. Config value if set
     * 2. HOSTNAME environment variable if set
     * 3. Generated UUID
     */
    public String getInstanceName() {
        // 1. Config value
        if (project != null && !isNullOrBlank(project.getInstanceName())) {
            return project.getInstanceName();
        }
        // 2. HOSTNAME env var
        String hostname = System.getenv("HOSTNAME");
        if (!isNullOrBlank(hostname)) {
            return hostname;
        }
        // 3. Generate UUID
        return IDGenerator.generate();
    }

    /**
     * Returns the profiler path with fallback resolution:
     * 1. Explicit config value if set
     * 2. Auto-resolved from jeffrey-home/libs/current/libasyncProfiler-${arch}.so if it exists,
     *    where ${arch} matches the runtime JVM's architecture (amd64 / arm64). Skipped when
     *    the arch is unsupported (a warning was logged at class load).
     */
    public String getProfilerPath() {
        // 1. Explicit config value
        String explicit = nullIfBlank(profilerPath);
        if (explicit != null) {
            return explicit;
        }
        // 2. Auto-resolve from jeffrey-home (only when arch is recognized)
        if (useJeffreyHome() && DETECTED_ARCH != null) {
            Path candidate = Path.of(jeffreyHome)
                    .resolve("libs/current/libasyncProfiler-" + DETECTED_ARCH + ".so");
            if (Files.exists(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    public void setProfilerPath(String profilerPath) {
        this.profilerPath = profilerPath;
    }

    public String getProfilerConfig() {
        return nullIfBlank(profilerConfig);
    }

    public void setProfilerConfig(String profilerConfig) {
        this.profilerConfig = profilerConfig;
    }

    public String getRepositoryType() {
        return nullIfBlank(repositoryType);
    }

    public void setRepositoryType(String repositoryType) {
        this.repositoryType = repositoryType;
    }

    public void setAgentPath(String agentPath) {
        this.agentPath = agentPath;
    }

    private static String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    public PerfCountersConfig getPerfCounters() {
        return perfCounters;
    }

    public void setPerfCounters(PerfCountersConfig perfCounters) {
        this.perfCounters = perfCounters;
    }

    public HeapDumpConfig getHeapDump() {
        return heapDump;
    }

    public void setHeapDump(HeapDumpConfig heapDump) {
        this.heapDump = heapDump;
    }

    public JvmLoggingConfig getJvmLogging() {
        return jvmLogging;
    }

    public void setJvmLogging(JvmLoggingConfig jvmLogging) {
        this.jvmLogging = jvmLogging;
    }

    public JdkJavaOptionsConfig getJdkJavaOptions() {
        return jdkJavaOptions;
    }

    public void setJdkJavaOptions(JdkJavaOptionsConfig jdkJavaOptions) {
        this.jdkJavaOptions = jdkJavaOptions;
    }

    public DebugNonSafepointsConfig getDebugNonSafepoints() {
        return debugNonSafepoints;
    }

    public void setDebugNonSafepoints(DebugNonSafepointsConfig debugNonSafepoints) {
        this.debugNonSafepoints = debugNonSafepoints;
    }

    public Map<String, String> getAttributes() {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        Map<String, String> result = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getValue() != null) {
                result.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return result;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /** Whether the agent records {@code @Traced} methods as spans. */
    public static class MethodTracingConfig {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class PerfCountersConfig {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class HeapDumpConfig {
        private boolean enabled;
        private String type = "exit";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public static class JvmLoggingConfig {
        private boolean enabled;
        private String command;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }
    }

    public static class JdkJavaOptionsConfig {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class DebugNonSafepointsConfig {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    // ==================== Helper Methods ====================

    private static boolean isNullOrBlank(String value) {
        return value == null || value.isBlank();
    }

    public boolean useJeffreyHome() {
        return !isNullOrBlank(jeffreyHome);
    }

    /**
     * Always returns true since getInstanceName() now has UUID fallback.
     */
    public boolean hasInstanceName() {
        return true;
    }

    public boolean isPerfCountersEnabled() {
        return perfCounters != null && perfCounters.isEnabled();
    }

    public MethodTracingConfig getMethodTracing() {
        return methodTracing;
    }

    public void setMethodTracing(MethodTracingConfig methodTracing) {
        this.methodTracing = methodTracing;
    }

    public boolean isMethodTracingEnabled() {
        return methodTracing != null && methodTracing.isEnabled();
    }

    public boolean isDebugNonSafepointsEnabled() {
        return debugNonSafepoints != null && debugNonSafepoints.isEnabled();
    }

    /**
     * Returns the agent JAR path with fallback resolution:
     * 1. Explicit config value if set
     * 2. Auto-resolved from jeffrey-home/libs/current/jeffrey-agent.jar if it exists
     * 3. Returns null if neither is available — agent-dependent features are skipped downstream.
     */
    public String getAgentPath() {
        // 1. Explicit config value
        if (!isNullOrBlank(agentPath)) {
            return agentPath;
        }
        // 2. Auto-resolve from jeffrey-home
        if (useJeffreyHome()) {
            Path candidate = Path.of(jeffreyHome).resolve(DEFAULT_AGENT_RELATIVE_PATH);
            if (Files.exists(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    public boolean isJdkJavaOptionsEnabled() {
        return jdkJavaOptions != null && jdkJavaOptions.isEnabled();
    }

    public String getAdditionalJvmOptions() {
        return nullIfBlank(additionalJvmOptions);
    }

    public void setAdditionalJvmOptions(String additionalJvmOptions) {
        this.additionalJvmOptions = additionalJvmOptions;
    }

    public String getJvmLoggingCommand() {
        if (jvmLogging != null && jvmLogging.isEnabled() && !isNullOrBlank(jvmLogging.getCommand())) {
            return jvmLogging.getCommand();
        }
        return null;
    }

    public RepositoryType resolveRepositoryType() {
        return !isNullOrBlank(repositoryType) ? RepositoryType.resolve(repositoryType) : RepositoryType.ASYNC_PROFILER;
    }

    public HeapDumpType resolveHeapDumpType() {
        if (heapDump != null && heapDump.isEnabled()) {
            String type = heapDump.getType();
            return HeapDumpType.resolve(!isNullOrBlank(type) ? type : "exit");
        }
        return null;
    }

    // ==================== Validation ====================

    private void validate() {
        boolean hasJeffreyHome = !isNullOrBlank(jeffreyHome);
        boolean hasWorkspacesDir = !isNullOrBlank(workspacesDir);

        if (!hasJeffreyHome && !hasWorkspacesDir) {
            throw new IllegalArgumentException("Either 'jeffrey-home' or 'workspaces-dir' must be specified");
        }

        if (hasJeffreyHome && hasWorkspacesDir) {
            throw new IllegalArgumentException("Cannot specify both 'jeffrey-home' and 'workspaces-dir'");
        }

        if (isNullOrBlank(project.getName())) {
            throw new IllegalArgumentException(
                    "'project.name' must be specified (HOCON 'project.name' or env JEFFREY_PROJECT_NAME)");
        }

        String projectName = project.getName();
        if (!projectName.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Project name can only contain alphanumeric characters, underscores, and dashes");
        }

    }

    // ==================== Project Config ====================

    public static class ProjectConfig {
        /**
         * Reference ID of the workspace on the target Jeffrey server.
         * The workspace must already exist — create it via the local UI's
         * "Create Workspace" flow before pointing the provisioner at it. Events for an
         * unknown reference ID are dropped on the server side with a warning, unless
         * the hub opts into auto-creation via {@code jeffrey.hub.workspaces.auto-create=true},
         * in which case the workspace is created on the first incoming event.
         */
        private String workspaceRefId;
        private String name;
        private String label;
        private String instanceName;

        public String getWorkspaceRefId() {
            String value = nullIfBlank(workspaceRefId);
            return value != null ? value : CliConstants.DEFAULT_WORKSPACE_REF_ID;
        }

        public void setWorkspaceRefId(String workspaceRefId) {
            this.workspaceRefId = workspaceRefId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLabel() {
            return nullIfBlank(label);
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getInstanceName() {
            return nullIfBlank(instanceName);
        }

        public void setInstanceName(String instanceName) {
            this.instanceName = instanceName;
        }

        private static String nullIfBlank(String value) {
            return (value == null || value.isBlank()) ? null : value;
        }
    }
}
