package pbouda.jeffrey.init;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import pbouda.jeffrey.init.model.HeapDumpType;
import pbouda.jeffrey.init.model.RepositoryType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Builder for creating {@link InitOptions} instances.
 * Supports building from CLI options or from a HOCON configuration file.
 */
public class InitOptionsBuilder {

    private boolean silent;
    private String jeffreyHomePath;
    private String workspacesDir;
    private String workspaceId;
    private String projectName;
    private String projectLabel;
    private String[] attributes;
    private String profilerPath;
    private String profilerConfig;
    private RepositoryType repositoryType = RepositoryType.ASYNC_PROFILER;
    private boolean enablePerfCounters;
    private HeapDumpType enableHeapDump;
    private String enableJvmLogging;
    private boolean exportJdkJavaOptions;
    private String additionalJvmOptions;

    private InitOptionsBuilder() {
    }

    /**
     * Creates an empty builder for populating from CLI options.
     */
    public static InitOptionsBuilder create() {
        return new InitOptionsBuilder();
    }

    /**
     * Creates a builder pre-populated from a HOCON configuration file.
     *
     * @param configFile path to the HOCON configuration file
     * @return builder with values loaded from the file
     */
    public static InitOptionsBuilder fromHoconFile(Path configFile) {
        if (!Files.exists(configFile)) {
            throw new IllegalArgumentException("Config file does not exist: " + configFile);
        }

        Config config = ConfigFactory.parseFile(configFile.toFile()).resolve();

        InitOptionsBuilder builder = new InitOptionsBuilder();
        builder.silent = getBooleanOrDefault(config, "silent", false);
        builder.jeffreyHomePath = getStringOrNull(config, "jeffrey-home");
        builder.workspacesDir = getStringOrNull(config, "workspaces-dir");
        builder.workspaceId = getStringOrNull(config, "workspace-id");
        builder.projectName = getStringOrNull(config, "project-name");
        builder.projectLabel = getStringOrNull(config, "project-label");
        builder.profilerPath = getStringOrNull(config, "profiler-path");
        builder.profilerConfig = getStringOrNull(config, "profiler-config");
        builder.enablePerfCounters = getBooleanOrDefault(config, "enable-perf-counters", false);
        builder.exportJdkJavaOptions = getBooleanOrDefault(config, "export-jdk-java-options", false);
        builder.enableJvmLogging = getStringOrNull(config, "enable-jvm-logging");
        builder.additionalJvmOptions = getStringOrNull(config, "additional-jvm-options");

        String repoType = getStringOrNull(config, "repository-type");
        if (repoType != null) {
            builder.repositoryType = RepositoryType.resolve(repoType);
        }

        String heapDump = getStringOrNull(config, "enable-heap-dump");
        if (heapDump != null) {
            builder.enableHeapDump = HeapDumpType.resolve(heapDump);
        }

        if (config.hasPath("attributes")) {
            Config attributesConfig = config.getConfig("attributes");
            Set<Map.Entry<String, Object>> entries = attributesConfig.root().unwrapped().entrySet();
            builder.attributes = entries.stream()
                    .map(e -> e.getKey() + "/" + e.getValue())
                    .toArray(String[]::new);
        }

        return builder;
    }

    private static String getStringOrNull(Config config, String path) {
        if (config.hasPath(path)) {
            String value = config.getString(path);
            return value.isBlank() ? null : value;
        }
        return null;
    }

    private static boolean getBooleanOrDefault(Config config, String path, boolean defaultValue) {
        return config.hasPath(path) ? config.getBoolean(path) : defaultValue;
    }

    public InitOptionsBuilder silent(boolean silent) {
        this.silent = silent;
        return this;
    }

    public InitOptionsBuilder jeffreyHomePath(String jeffreyHomePath) {
        this.jeffreyHomePath = jeffreyHomePath;
        return this;
    }

    public InitOptionsBuilder workspacesDir(String workspacesDir) {
        this.workspacesDir = workspacesDir;
        return this;
    }

    public InitOptionsBuilder workspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
        return this;
    }

    public InitOptionsBuilder projectName(String projectName) {
        this.projectName = projectName;
        return this;
    }

    public InitOptionsBuilder projectLabel(String projectLabel) {
        this.projectLabel = projectLabel;
        return this;
    }

    public InitOptionsBuilder attributes(String[] attributes) {
        this.attributes = attributes;
        return this;
    }

    public InitOptionsBuilder profilerPath(String profilerPath) {
        this.profilerPath = profilerPath;
        return this;
    }

    public InitOptionsBuilder profilerConfig(String profilerConfig) {
        this.profilerConfig = profilerConfig;
        return this;
    }

    public InitOptionsBuilder repositoryType(RepositoryType repositoryType) {
        this.repositoryType = repositoryType;
        return this;
    }

    public InitOptionsBuilder enablePerfCounters(boolean enablePerfCounters) {
        this.enablePerfCounters = enablePerfCounters;
        return this;
    }

    public InitOptionsBuilder enableHeapDump(HeapDumpType enableHeapDump) {
        this.enableHeapDump = enableHeapDump;
        return this;
    }

    public InitOptionsBuilder enableJvmLogging(String enableJvmLogging) {
        this.enableJvmLogging = enableJvmLogging;
        return this;
    }

    public InitOptionsBuilder exportJdkJavaOptions(boolean exportJdkJavaOptions) {
        this.exportJdkJavaOptions = exportJdkJavaOptions;
        return this;
    }

    public InitOptionsBuilder additionalJvmOptions(String additionalJvmOptions) {
        this.additionalJvmOptions = additionalJvmOptions;
        return this;
    }

    /**
     * Builds the {@link InitOptions} instance after validating all required fields.
     *
     * @return validated InitOptions instance
     * @throws IllegalArgumentException if validation fails
     */
    public InitOptions build() {
        validate();
        return new InitOptions(
                silent,
                jeffreyHomePath,
                workspacesDir,
                workspaceId,
                projectName,
                projectLabel,
                attributes,
                profilerPath,
                profilerConfig,
                repositoryType,
                enablePerfCounters,
                enableHeapDump,
                enableJvmLogging,
                exportJdkJavaOptions,
                additionalJvmOptions
        );
    }

    private void validate() {
        if (jeffreyHomePath == null && workspacesDir == null) {
            throw new IllegalArgumentException("Either 'jeffrey-home' or 'workspaces-dir' must be specified");
        }

        if (jeffreyHomePath != null && workspacesDir != null) {
            throw new IllegalArgumentException("Cannot specify both 'jeffrey-home' and 'workspaces-dir'");
        }

        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("'workspace-id' must be specified");
        }

        if (projectName == null || projectName.isBlank()) {
            throw new IllegalArgumentException("'project-name' must be specified");
        }

        if (projectLabel == null || projectLabel.isBlank()) {
            throw new IllegalArgumentException("'project-label' must be specified");
        }

        if (!projectName.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Project name can only contain alphanumeric characters, underscores, and dashes");
        }
    }
}
