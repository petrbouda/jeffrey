package cafe.jeffrey.provisioner;

import cafe.jeffrey.provisioner.model.HeapDumpType;
import cafe.jeffrey.shared.common.AppInfoConstants;
import cafe.jeffrey.shared.common.CliConstants;
import cafe.jeffrey.shared.common.HeartbeatConstants;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

public class FeatureBuilder {

    public static final String PERF_COUNTERS_FILE = "perf-counters.hsperfdata";
    public static final String STREAMING_REPO_DIR = "streaming-repo";
    public static final String HEARTBEAT_DIR = HeartbeatConstants.HEARTBEAT_DIR;

    /* Performance data JVM options */
    private static final String PERF_DATA_OPTIONS = "-XX:+UsePerfData -XX:PerfDataSaveFile="
            + Path.of(CliConstants.CURRENT_SESSION, PERF_COUNTERS_FILE);

    /* Heap dump JVM Base options */
    private static final String HEAP_DUMP_BASE_OPTIONS = "-XX:+HeapDumpOnOutOfMemoryError "
            + "-XX:HeapDumpGzipLevel=1 "
            + "-XX:HeapDumpPath=" + Path.of(CliConstants.CURRENT_SESSION, "heap-dump.hprof.gz") + " ";

    /* Heap dump JVM Crash options */
    private static final String HEAP_DUMP_CRASH_OPTIONS = HEAP_DUMP_BASE_OPTIONS
            + "-XX:+CrashOnOutOfMemoryError "
            + "-XX:ErrorFile=" + Path.of(CliConstants.CURRENT_SESSION, "hs-jvm-err.log");

    /* Heap dump JVM Exit options */
    private static final String HEAP_DUMP_EXIT_OPTIONS = HEAP_DUMP_BASE_OPTIONS
            + "-XX:+ExitOnOutOfMemoryError ";

    /* Streaming JFR options (used by Jeffrey Agent for repository location) */
    private static final String STREAMING_FLIGHT_RECORDER_OPTIONS =
            "-XX:FlightRecorderOptions=repository=" + CliConstants.CURRENT_SESSION + "/" + STREAMING_REPO_DIR + "";

    /* Agent JVM option prefix (passes heartbeat directory as the first agent argument) */
    private static final String AGENT_OPTION_PREFIX = "-javaagent:%s=" + HeartbeatConstants.PARAM_DIR + "=%s";

    private static final String AGENT_ARG_SEPARATOR = ",";
    private static final String AGENT_ARG_ASSIGN = "=";

    /* Debug Non-Safepoints JVM options for more precise profiling */
    private static final String DEBUG_NON_SAFEPOINTS_OPTIONS = "-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints";

    private boolean debugNonSafepointsEnabled;
    private boolean perfCountersEnabled;
    private HeapDumpType heapDumpType;
    private String jvmLogging;
    private String agentPath;
    private String additionalJvmOptions;
    private AppIdentity appIdentity;

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

    public FeatureBuilder setAppIdentity(AppIdentity appIdentity) {
        this.appIdentity = appIdentity;
        return this;
    }

    public String build(Path currentSessionPath) {
        StringBuilder options = new StringBuilder();

        if (debugNonSafepointsEnabled) {
            options.append(DEBUG_NON_SAFEPOINTS_OPTIONS);
            options.append(" ");
        }

        if (perfCountersEnabled) {
            options.append(PERF_DATA_OPTIONS.replace(CliConstants.CURRENT_SESSION, currentSessionPath.toString()));
            options.append(" ");
        }

        if (heapDumpType != null) {
            String heapDumpOptions = switch (heapDumpType) {
                case CRASH -> HEAP_DUMP_CRASH_OPTIONS;
                case EXIT -> HEAP_DUMP_EXIT_OPTIONS;
            };
            options.append(heapDumpOptions.replace(CliConstants.CURRENT_SESSION, currentSessionPath.toString()));
            options.append(" ");
        }

        if (jvmLogging != null && !jvmLogging.isBlank()) {
            options.append("-Xlog:");
            options.append(jvmLogging.replace(CliConstants.CURRENT_SESSION, currentSessionPath.toString()));
            options.append(" ");
        }

        if (agentPath != null && !agentPath.isBlank()) {
            String heartbeatDirPath = currentSessionPath.resolve(HEARTBEAT_DIR).toString();
            StringBuilder agentOption = new StringBuilder(String.format(AGENT_OPTION_PREFIX, agentPath, heartbeatDirPath));
            appendAppIdentity(agentOption);
            options.append(agentOption);
            options.append(" ");
            options.append(STREAMING_FLIGHT_RECORDER_OPTIONS.replace(CliConstants.CURRENT_SESSION, currentSessionPath.toString()));
            options.append(" ");
        }

        if (additionalJvmOptions != null && !additionalJvmOptions.isBlank()) {
            options.append(additionalJvmOptions.replace(CliConstants.CURRENT_SESSION, currentSessionPath.toString()));
            options.append(" ");
        }

        return options.toString().trim();
    }

    /**
     * Appends the {@code jeffrey.AppInformation} identity as additional agent
     * arguments. The two free-form values (project label and serialized
     * attributes) are Base64-encoded because the agent splits its argument string
     * on {@code ','}; all other values are delimiter-safe.
     */
    private void appendAppIdentity(StringBuilder agentOption) {
        if (appIdentity == null) {
            return;
        }
        appendArg(agentOption, AppInfoConstants.PARAM_WORKSPACE_ID, appIdentity.workspaceId());
        appendArg(agentOption, AppInfoConstants.PARAM_PROJECT_ID, appIdentity.projectId());
        appendArg(agentOption, AppInfoConstants.PARAM_PROJECT_NAME, appIdentity.projectName());
        appendArg(agentOption, AppInfoConstants.PARAM_PROJECT_LABEL, encodeBase64(appIdentity.projectLabel()));
        appendArg(agentOption, AppInfoConstants.PARAM_INSTANCE_ID, appIdentity.instanceId());
        appendArg(agentOption, AppInfoConstants.PARAM_SESSION_ID, appIdentity.sessionId());
        appendArg(agentOption, AppInfoConstants.PARAM_SESSION_ORDER, Integer.toString(appIdentity.sessionOrder()));
        appendArg(agentOption, AppInfoConstants.PARAM_ATTRIBUTES, encodeBase64(serializeAttributes(appIdentity.attributes())));
        appendArg(agentOption, AppInfoConstants.PARAM_PROVISIONED_AT, Long.toString(appIdentity.provisionedAt()));
    }

    private static void appendArg(StringBuilder agentOption, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        agentOption.append(AGENT_ARG_SEPARATOR).append(key).append(AGENT_ARG_ASSIGN).append(value);
    }

    private static String serializeAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return "";
        }
        StringBuilder serialized = new StringBuilder();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (!serialized.isEmpty()) {
                serialized.append(AppInfoConstants.ATTRIBUTE_PAIR_SEPARATOR);
            }
            serialized.append(entry.getKey())
                    .append(AppInfoConstants.ATTRIBUTE_KV_SEPARATOR)
                    .append(entry.getValue());
        }
        return serialized.toString();
    }

    private static String encodeBase64(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
