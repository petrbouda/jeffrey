package pbouda.jeffrey.init.command;

import pbouda.jeffrey.init.InitExecutor;
import pbouda.jeffrey.init.InitOptions;
import pbouda.jeffrey.init.InitOptionsBuilder;
import pbouda.jeffrey.init.Replacements;
import pbouda.jeffrey.init.model.HeapDumpType;
import pbouda.jeffrey.init.model.HeapDumpTypeConverter;
import pbouda.jeffrey.init.model.RepositoryType;
import pbouda.jeffrey.init.model.RepositoryTypeConverter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = InitCommand.COMMAND_NAME, description = "Initialize Jeffrey project and current session. Creates a ENV file with variables to source in the shell.", mixinStandardHelpOptions = true)
public class InitCommand implements Runnable {

    public static final String COMMAND_NAME = "init";

    @Option(names = {"--silent"}, description = "Suppress output. Only create the variable without printing the output for sourcing.")
    private boolean silent = false;

    @Option(names = {"--jeffrey-home"}, description = "Jeffrey HOME directory path. Automatically creates 'workspaces' directory in Jeffrey home (Otherwise, --workspaces-dir must be provided).")
    private String jeffreyHomePath;

    @Option(names = {"--workspaces-dir"}, description = "Workspaces directory path. It's taken as a directory for storing projects' sessions data (Otherwise, --jeffrey-home must be provided).")
    private String workspacesDir;

    @Option(names = {"--workspace-id"}, description = "Workspace ID, where the project belongs to.", required = true)
    private String workspaceId;

    @Option(names = {"--project-name"}, description = "Project Name should be a unique identifier for the given project to know that a deployment belongs to the particular service", required = true)
    private String projectName;

    @Option(names = {"--project-label"}, description = "Human-readable label of the project", required = true)
    private String projectLabel;

    @Option(names = {"--attribute"}, description = "Key-value pair attributes delimited by slash ('/') to be added to the project. Can be specified multiple times.")
    private String[] attributes;

    @Option(names = {"--profiler-path"}, description = "Custom path to the profiler agent")
    private String profilerPath;

    @Option(names = {"--profiler-config"}, description = "Custom profiler configuration")
    private String profilerConfig;

    @Option(names = {"--repository-type"}, description = "Type of repository for the project (only ASPROF)", defaultValue = "ASPROF", converter = RepositoryTypeConverter.class)
    private RepositoryType repositoryType;

    @Option(names = {"--enable-perf-counters"}, description = "Enable PerfCounters (can be used to detect finished session)", defaultValue = "false")
    private boolean enablePerfCounters;

    @Option(names = {"--enable-heap-dump"},
            description = {
                    "Enable HeapDump feature to capture heap dump on OutOfMemoryError",
                    "Use EXIT to enable ExitOnOutOfMemoryError",
                    "Use CRASH to enable CrashOnOutOfMemoryError with a crash dump file",
            },
            converter = HeapDumpTypeConverter.class)
    private HeapDumpType enableHeapDump;

    @Option(
            names = {"--enable-jvm-logging"},
            description = {
                    "Enable JVM Logging, provide command with " + Replacements.CURRENT_SESSION + " placeholder for file",
                    "e.g. 'jfr*=trace:file=" + Replacements.CURRENT_SESSION + "/jfr-jvm.log::filecount=5,filesize=5m'",
                    "Use '-jvm.log' suffix to automatically recognize the file as a JVM log file by Jeffrey"
            })
    private String enableJvmLogging;

    @Option(names = {"--export-jdk-java-options"}, description = "Also export JDK_JAVA_OPTIONS with the same content as JEFFREY_PROFILER_CONFIG")
    private boolean exportJdkJavaOptions = false;

    @Option(names = {"--additional-jvm-options"}, description = "Additional JVM options to append to the profiler configuration (e.g. '-Xmx3g -Xms3g -XX:+UseG1GC')")
    private String additionalJvmOptions;

    @Override
    public void run() {
        try {
            InitOptions options = InitOptionsBuilder.create()
                    .silent(silent)
                    .jeffreyHomePath(jeffreyHomePath)
                    .workspacesDir(workspacesDir)
                    .workspaceId(workspaceId)
                    .projectName(projectName)
                    .projectLabel(projectLabel)
                    .attributes(attributes)
                    .profilerPath(profilerPath)
                    .profilerConfig(profilerConfig)
                    .repositoryType(repositoryType)
                    .enablePerfCounters(enablePerfCounters)
                    .enableHeapDump(enableHeapDump)
                    .enableJvmLogging(enableJvmLogging)
                    .exportJdkJavaOptions(exportJdkJavaOptions)
                    .additionalJvmOptions(additionalJvmOptions)
                    .build();

            new InitExecutor().execute(options);
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
        }
    }
}
