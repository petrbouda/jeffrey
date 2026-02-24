package pbouda.jeffrey.init;

import pbouda.jeffrey.init.model.HeapDumpType;
import pbouda.jeffrey.shared.common.AgentConstants;

import java.nio.file.Path;

public class FeatureBuilder {

    public static final String PERF_COUNTERS_FILE = "perf-counters.hsperfdata";
    public static final String STREAMING_REPO_DIR = "streaming-repo";

    /* Performance data JVM options */
    private static final String PERF_DATA_OPTIONS = "-XX:+UsePerfData -XX:PerfDataSaveFile="
            + Path.of(AgentConstants.CURRENT_SESSION, PERF_COUNTERS_FILE);

    /* Heap dump JVM Base options */
    private static final String HEAP_DUMP_BASE_OPTIONS = "-XX:+HeapDumpOnOutOfMemoryError "
            + "-XX:HeapDumpGzipLevel=1 "
            + "-XX:HeapDumpPath=" + Path.of(AgentConstants.CURRENT_SESSION, "heap-dump.hprof.gz") + " ";

    /* Heap dump JVM Crash options */
    private static final String HEAP_DUMP_CRASH_OPTIONS = HEAP_DUMP_BASE_OPTIONS
            + "-XX:+CrashOnOutOfMemoryError "
            + "-XX:ErrorFile=" + Path.of(AgentConstants.CURRENT_SESSION, "hs-jvm-err.log");

    /* Heap dump JVM Exit options */
    private static final String HEAP_DUMP_EXIT_OPTIONS = HEAP_DUMP_BASE_OPTIONS
            + "-XX:+ExitOnOutOfMemoryError ";

    /* Streaming JFR options (used by Jeffrey Agent for repository location) */
    private static final String STREAMING_FLIGHT_RECORDER_OPTIONS =
            "-XX:FlightRecorderOptions:repository=" + AgentConstants.CURRENT_SESSION + "/" + STREAMING_REPO_DIR + ",preserve-repository=true";

    /* Agent JVM option template */
    private static final String AGENT_OPTION_TEMPLATE = "-javaagent:%s";

    /* Debug Non-Safepoints JVM options for more precise profiling */
    private static final String DEBUG_NON_SAFEPOINTS_OPTIONS = "-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints";

    private boolean debugNonSafepointsEnabled;
    private boolean perfCountersEnabled;
    private HeapDumpType heapDumpType;
    private String jvmLogging;
    private String agentPath;
    private String additionalJvmOptions;

    public FeatureBuilder setDebugNonSafepointsEnabled(boolean enabled) {
        this.debugNonSafepointsEnabled = enabled;
        return this;
    }

    public FeatureBuilder setPerfCountersEnabled(boolean enabled) {
        this.perfCountersEnabled = enabled;
        return this;
    }

    public FeatureBuilder setHeapDumpEnabled(HeapDumpType heapDumpType) {
        this.heapDumpType = heapDumpType;
        return this;
    }

    public FeatureBuilder setJvmLogging(String jvmLogging) {
        this.jvmLogging = jvmLogging;
        return this;
    }

    public FeatureBuilder setAgentPath(String agentPath) {
        this.agentPath = agentPath;
        return this;
    }

    public FeatureBuilder setAdditionalJvmOptions(String additionalJvmOptions) {
        this.additionalJvmOptions = additionalJvmOptions;
        return this;
    }

    public String build(Path currentSessionPath) {
        StringBuilder options = new StringBuilder();

        if (debugNonSafepointsEnabled) {
            options.append(DEBUG_NON_SAFEPOINTS_OPTIONS);
            options.append(" ");
        }

        if (perfCountersEnabled) {
            options.append(PERF_DATA_OPTIONS.replace(AgentConstants.CURRENT_SESSION, currentSessionPath.toString()));
            options.append(" ");
        }

        if (heapDumpType != null) {
            String heapDumpOptions = switch (heapDumpType) {
                case CRASH -> HEAP_DUMP_CRASH_OPTIONS;
                case EXIT -> HEAP_DUMP_EXIT_OPTIONS;
            };
            options.append(heapDumpOptions.replace(AgentConstants.CURRENT_SESSION, currentSessionPath.toString()));
            options.append(" ");
        }

        if (jvmLogging != null && !jvmLogging.isBlank()) {
            options.append("-Xlog:");
            options.append(jvmLogging.replace(AgentConstants.CURRENT_SESSION, currentSessionPath.toString()));
            options.append(" ");
        }

        if (agentPath != null && !agentPath.isBlank()) {
            options.append(String.format(AGENT_OPTION_TEMPLATE, agentPath));
            options.append(" ");
            options.append(STREAMING_FLIGHT_RECORDER_OPTIONS.replace(AgentConstants.CURRENT_SESSION, currentSessionPath.toString()));
            options.append(" ");
        }

        if (additionalJvmOptions != null && !additionalJvmOptions.isBlank()) {
            options.append(additionalJvmOptions);
            options.append(" ");
        }

        return options.toString().trim();
    }
}
