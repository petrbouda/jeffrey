package pbouda.jeffrey.init;

import pbouda.jeffrey.init.model.HeapDumpType;
import pbouda.jeffrey.init.model.RepositoryType;

/**
 * Immutable configuration options for initializing a Jeffrey project and session.
 */
public class InitOptions {

    private final boolean silent;
    private final String jeffreyHomePath;
    private final String workspacesDir;
    private final String workspaceId;
    private final String projectName;
    private final String projectLabel;
    private final String[] attributes;
    private final String profilerPath;
    private final String profilerConfig;
    private final RepositoryType repositoryType;
    private final boolean enablePerfCounters;
    private final HeapDumpType enableHeapDump;
    private final String enableJvmLogging;
    private final boolean exportJdkJavaOptions;
    private final String additionalJvmOptions;

    InitOptions(
            boolean silent,
            String jeffreyHomePath,
            String workspacesDir,
            String workspaceId,
            String projectName,
            String projectLabel,
            String[] attributes,
            String profilerPath,
            String profilerConfig,
            RepositoryType repositoryType,
            boolean enablePerfCounters,
            HeapDumpType enableHeapDump,
            String enableJvmLogging,
            boolean exportJdkJavaOptions,
            String additionalJvmOptions) {
        this.silent = silent;
        this.jeffreyHomePath = jeffreyHomePath;
        this.workspacesDir = workspacesDir;
        this.workspaceId = workspaceId;
        this.projectName = projectName;
        this.projectLabel = projectLabel;
        this.attributes = attributes;
        this.profilerPath = profilerPath;
        this.profilerConfig = profilerConfig;
        this.repositoryType = repositoryType;
        this.enablePerfCounters = enablePerfCounters;
        this.enableHeapDump = enableHeapDump;
        this.enableJvmLogging = enableJvmLogging;
        this.exportJdkJavaOptions = exportJdkJavaOptions;
        this.additionalJvmOptions = additionalJvmOptions;
    }

    public boolean silent() {
        return silent;
    }

    public String jeffreyHomePath() {
        return jeffreyHomePath;
    }

    public String workspacesDir() {
        return workspacesDir;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String projectName() {
        return projectName;
    }

    public String projectLabel() {
        return projectLabel;
    }

    public String[] attributes() {
        return attributes;
    }

    public String profilerPath() {
        return profilerPath;
    }

    public String profilerConfig() {
        return profilerConfig;
    }

    public RepositoryType repositoryType() {
        return repositoryType;
    }

    public boolean enablePerfCounters() {
        return enablePerfCounters;
    }

    public HeapDumpType enableHeapDump() {
        return enableHeapDump;
    }

    public String enableJvmLogging() {
        return enableJvmLogging;
    }

    public boolean exportJdkJavaOptions() {
        return exportJdkJavaOptions;
    }

    public String additionalJvmOptions() {
        return additionalJvmOptions;
    }

    public boolean useJeffreyHome() {
        return jeffreyHomePath != null;
    }
}
