package pbouda.jeffrey.init;

import pbouda.jeffrey.init.model.HeapDumpType;

import java.nio.file.Path;

public class FeatureBuilder {

    public static final String PERF_COUNTERS_FILE = "perf-counters.hsperfdata";
    public static final String STREAMING_REPO_DIR = "streaming-repo";

    /* Performance data JVM options */
    private static final String PERF_DATA_OPTIONS = "-XX:+UsePerfData -XX:PerfDataSaveFile="
            + Path.of(Replacements.CURRENT_SESSION, PERF_COUNTERS_FILE);

    /* Heap dump JVM Base options */
    private static final String HEAP_DUMP_BASE_OPTIONS = "-XX:+HeapDumpOnOutOfMemoryError "
            + "-XX:HeapDumpPath=" + Path.of(Replacements.CURRENT_SESSION, "heap-dump.hprof") + " ";

    /* Heap dump JVM Crash options */
    private static final String HEAP_DUMP_CRASH_OPTIONS = HEAP_DUMP_BASE_OPTIONS
            + "-XX:+CrashOnOutOfMemoryError "
            + "-XX:ErrorFile=" + Path.of(Replacements.CURRENT_SESSION, "hs-err.log");

    /* Heap dump JVM Exit options */
    private static final String HEAP_DUMP_EXIT_OPTIONS = HEAP_DUMP_BASE_OPTIONS
            + "-XX:+ExitOnOutOfMemoryError ";

    /* Messaging JFR options */
    private static final String MESSAGING_FLIGHT_RECORDER_OPTIONS =
            "-XX:FlightRecorderOptions:repository=" + Replacements.CURRENT_SESSION + "/" + STREAMING_REPO_DIR + ",preserve-repository=true";
    private static final String MESSAGING_START_RECORDING_TEMPLATE =
            "-XX:StartFlightRecording=name=jeffrey-streaming,maxage=%s,jeffrey.LogMessage#enabled=true,jeffrey.Alert#enabled=true";

    private boolean perfCountersEnabled;
    private HeapDumpType heapDumpType;
    private String jvmLogging;
    private boolean messagingEnabled;
    private String messagingMaxAge = "24h";
    private String additionalJvmOptions;

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

    public FeatureBuilder setMessagingEnabled(boolean enabled) {
        this.messagingEnabled = enabled;
        return this;
    }

    public FeatureBuilder setMessagingMaxAge(String maxAge) {
        this.messagingMaxAge = maxAge;
        return this;
    }

    public FeatureBuilder setAdditionalJvmOptions(String additionalJvmOptions) {
        this.additionalJvmOptions = additionalJvmOptions;
        return this;
    }

    public String build(Path currentSessionPath) {
        StringBuilder options = new StringBuilder();

        if (perfCountersEnabled) {
            options.append(PERF_DATA_OPTIONS.replace(Replacements.CURRENT_SESSION, currentSessionPath.toString()));
            options.append(" ");
        }

        if (heapDumpType != null) {
            String heapDumpOptions = switch (heapDumpType) {
                case CRASH -> HEAP_DUMP_CRASH_OPTIONS;
                case EXIT -> HEAP_DUMP_EXIT_OPTIONS;
            };
            options.append(heapDumpOptions.replace(Replacements.CURRENT_SESSION, currentSessionPath.toString()));
            options.append(" ");
        }

        if (jvmLogging != null && !jvmLogging.isBlank()) {
            options.append("-Xlog:");
            options.append(jvmLogging.replace(Replacements.CURRENT_SESSION, currentSessionPath.toString()));
            options.append(" ");
        }

        if (messagingEnabled) {
            options.append(MESSAGING_FLIGHT_RECORDER_OPTIONS.replace(Replacements.CURRENT_SESSION, currentSessionPath.toString()));
            options.append(" ");
            options.append(String.format(MESSAGING_START_RECORDING_TEMPLATE, messagingMaxAge));
            options.append(" ");
        }

        if (additionalJvmOptions != null && !additionalJvmOptions.isBlank()) {
            options.append(additionalJvmOptions);
            options.append(" ");
        }

        return options.toString().trim();
    }
}
