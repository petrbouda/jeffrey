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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigBeanFactory;
import com.typesafe.config.ConfigFactory;
import pbouda.jeffrey.init.model.HeapDumpType;
import pbouda.jeffrey.shared.common.IDGenerator;
import pbouda.jeffrey.shared.common.model.RepositoryType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Configuration class for HOCON-based initialization.
 * Used with {@link com.typesafe.config.ConfigBeanFactory} for automatic parsing.
 */
public class InitConfig {

    private static final String DEFAULT_PROFILER_RELATIVE_PATH = "libs/current/libasyncProfiler.so";
    private static final String DEFAULT_AGENT_RELATIVE_PATH = "libs/current/jeffrey-agent.jar";

    // Default configuration with all optional fields
    private static final String DEFAULTS = """
            silent = false
            jeffrey-home = ""
            workspaces-dir = ""
            profiler-path = ""
            profiler-config = ""
            repository-type = ""
            project {
                workspace-id = ""
                name = ""
                label = ""
                instance-id = ""
            }
            attributes = {}
            perf-counters { enabled = false }
            heap-dump { enabled = false, type = "exit" }
            jvm-logging { enabled = false, command = "" }
            messaging { enabled = false, max-age = "24h" }
            heartbeat { enabled = false, period = "5 s", agent-path = "" }
            jdk-java-options { enabled = false, additional-options = "" }
            """;

    /**
     * Creates an InitConfig from a base HOCON configuration file with an optional override file.
     * <p>
     * Priority order (highest to lowest): override config > base config > defaults.
     *
     * @param baseConfigFile     path to the base HOCON configuration file
     * @param overrideConfigFile path to an override HOCON configuration file (nullable)
     * @return validated InitConfig instance
     */
    public static InitConfig fromHoconFile(Path baseConfigFile, Path overrideConfigFile) {
        if (!Files.exists(baseConfigFile)) {
            throw new IllegalArgumentException("Base config file does not exist: " + baseConfigFile);
        }

        Config defaults = ConfigFactory.parseString(DEFAULTS);
        Config baseConfig = ConfigFactory.parseFile(baseConfigFile.toFile());

        Config resolved;
        if (overrideConfigFile != null) {
            if (!Files.exists(overrideConfigFile)) {
                throw new IllegalArgumentException("Override config file does not exist: " + overrideConfigFile);
            }
            Config overrideConfig = ConfigFactory.parseFile(overrideConfigFile.toFile());
            resolved = overrideConfig.withFallback(baseConfig).withFallback(defaults).resolve();
        } else {
            resolved = baseConfig.withFallback(defaults).resolve();
        }

        InitConfig config = ConfigBeanFactory.create(resolved, InitConfig.class);
        config.validate();

        return config;
    }

    private boolean silent;
    private String jeffreyHome;
    private String workspacesDir;
    private String profilerPath;
    private String profilerConfig;
    private String repositoryType;
    private ProjectConfig project;
    private PerfCountersConfig perfCounters;
    private HeapDumpConfig heapDump;
    private JvmLoggingConfig jvmLogging;
    private MessagingConfig messaging;
    private HeartbeatConfig heartbeat;
    private JdkJavaOptionsConfig jdkJavaOptions;
    private Map<String, Object> attributes;

    public boolean isSilent() {
        return silent;
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
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
    public String getWorkspaceId() {
        return project != null ? project.getWorkspaceId() : null;
    }

    public String getProjectName() {
        return project != null ? project.getName() : null;
    }

    public String getProjectLabel() {
        return project != null ? project.getLabel() : null;
    }

    /**
     * Returns the instance ID with fallback resolution:
     * 1. Config value if set
     * 2. HOSTNAME environment variable if set
     * 3. Generated UUID
     */
    public String getInstanceId() {
        // 1. Config value
        if (project != null && !isNullOrBlank(project.getInstanceId())) {
            return project.getInstanceId();
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
     * 2. Auto-resolved from jeffrey-home/libs/current/libasyncProfiler.so if it exists
     */
    public String getProfilerPath() {
        // 1. Explicit config value
        String explicit = nullIfBlank(profilerPath);
        if (explicit != null) {
            return explicit;
        }
        // 2. Auto-resolve from jeffrey-home
        if (useJeffreyHome()) {
            Path candidate = Path.of(jeffreyHome).resolve(DEFAULT_PROFILER_RELATIVE_PATH);
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

    public MessagingConfig getMessaging() {
        return messaging;
    }

    public void setMessaging(MessagingConfig messaging) {
        this.messaging = messaging;
    }

    public HeartbeatConfig getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(HeartbeatConfig heartbeat) {
        this.heartbeat = heartbeat;
    }

    public JdkJavaOptionsConfig getJdkJavaOptions() {
        return jdkJavaOptions;
    }

    public void setJdkJavaOptions(JdkJavaOptionsConfig jdkJavaOptions) {
        this.jdkJavaOptions = jdkJavaOptions;
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

    public static class MessagingConfig {
        private boolean enabled;
        private String maxAge = "24h";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(String maxAge) {
            this.maxAge = maxAge;
        }
    }

    public static class HeartbeatConfig {
        private boolean enabled;
        private String period = "5 s";
        private String agentPath;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPeriod() {
            return period;
        }

        public void setPeriod(String period) {
            this.period = period;
        }

        public String getAgentPath() {
            return agentPath;
        }

        public void setAgentPath(String agentPath) {
            this.agentPath = agentPath;
        }
    }

    public static class JdkJavaOptionsConfig {
        private boolean enabled;
        private String additionalOptions;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAdditionalOptions() {
            return additionalOptions;
        }

        public void setAdditionalOptions(String additionalOptions) {
            this.additionalOptions = additionalOptions;
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
     * Always returns true since getInstanceId() now has UUID fallback.
     */
    public boolean hasInstanceId() {
        return true;
    }

    public boolean isPerfCountersEnabled() {
        return perfCounters != null && perfCounters.isEnabled();
    }

    public boolean isMessagingEnabled() {
        return messaging != null && messaging.isEnabled();
    }

    public String getMessagingMaxAge() {
        if (messaging != null && !isNullOrBlank(messaging.getMaxAge())) {
            return messaging.getMaxAge();
        }
        return "24h";
    }

    public boolean isHeartbeatEnabled() {
        return heartbeat != null && heartbeat.isEnabled();
    }

    public String getHeartbeatPeriod() {
        if (heartbeat != null && !isNullOrBlank(heartbeat.getPeriod())) {
            return heartbeat.getPeriod();
        }
        return "5 s";
    }

    /**
     * Returns the agent JAR path with fallback resolution:
     * 1. Explicit config value if set
     * 2. Auto-resolved from jeffrey-home/libs/current/jeffrey-agent.jar if it exists
     */
    public String getAgentPath() {
        // 1. Explicit config value
        if (heartbeat != null && !isNullOrBlank(heartbeat.getAgentPath())) {
            return heartbeat.getAgentPath();
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
        if (jdkJavaOptions != null && !isNullOrBlank(jdkJavaOptions.getAdditionalOptions())) {
            return jdkJavaOptions.getAdditionalOptions();
        }
        return null;
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

        if (project == null || isNullOrBlank(project.getWorkspaceId())) {
            throw new IllegalArgumentException("'project.workspace-id' must be specified");
        }

        if (project == null || isNullOrBlank(project.getName())) {
            throw new IllegalArgumentException("'project.name' must be specified");
        }

        String projectName = project.getName();
        if (!projectName.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Project name can only contain alphanumeric characters, underscores, and dashes");
        }

        if (isMessagingEnabled() && !isNullOrBlank(profilerConfig)) {
            throw new IllegalArgumentException("Cannot specify both 'messaging.enabled' and 'profiler-config'");
        }

        if (isHeartbeatEnabled() && !isNullOrBlank(profilerConfig)) {
            throw new IllegalArgumentException("Cannot specify both 'heartbeat.enabled' and 'profiler-config'");
        }
    }

    // ==================== Project Config ====================

    public static class ProjectConfig {
        private String workspaceId;
        private String name;
        private String label;
        private String instanceId;

        public String getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
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

        public String getInstanceId() {
            return nullIfBlank(instanceId);
        }

        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }

        private static String nullIfBlank(String value) {
            return (value == null || value.isBlank()) ? null : value;
        }
    }
}
