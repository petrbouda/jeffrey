/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provisioner.feature;

import cafe.jeffrey.provisioner.model.HeapDumpType;
import cafe.jeffrey.provisioner.placeholder.Placeholders;
import cafe.jeffrey.shared.common.CliConstants;
import cafe.jeffrey.shared.common.HeartbeatConstants;

import java.nio.file.Path;
import java.util.Optional;

/**
 * One diagnostic capability, and the JVM options that switch it on.
 *
 * <p>A feature renders to nothing when it is not configured, so enabling one is adding a case here
 * rather than another branch in a shared method. The option templates are written against
 * {@code <<JEFFREY:CURRENT_SESSION>>} and resolved by the caller's {@link Placeholders}, which is
 * what points them at this run's session directory.
 *
 * <p>Sealed because the provisioner is compiled to a GraalVM native image: features are listed
 * explicitly in {@link JvmFeatures}, never discovered by reflection.
 */
public sealed interface JvmFeature {

    String CURRENT_SESSION = CliConstants.CURRENT_SESSION;

    /** The options this feature contributes, or empty when it is switched off. */
    Optional<String> render(Path sessionPath, Placeholders placeholders);

    /**
     * The async-profiler agent that records the session.
     *
     * <p>Unlike the other features it is on by default: switched off only when the library it
     * points at is missing, which {@code AsyncProfilerResolver} decides before this is built. A
     * {@code null} library means that, and renders nothing.
     *
     * <p>Both parts may carry {@code <<JEFFREY:...>>} placeholders, typically the session
     * directory in the {@code file=} option, resolved here against this run.
     */
    record AsyncProfiler(String library, String options) implements JvmFeature {

        public static final String AGENT_PATH_OPTION = "-agentpath:";
        public static final char OPTIONS_SEPARATOR = '=';

        public static AsyncProfiler disabled() {
            return new AsyncProfiler(null, null);
        }

        public boolean enabled() {
            return library != null;
        }

        @Override
        public Optional<String> render(Path sessionPath, Placeholders placeholders) {
            if (!enabled()) {
                return Optional.empty();
            }
            String agent = AGENT_PATH_OPTION + placeholders.resolve(library);
            if (options == null || options.isBlank()) {
                return Optional.of(agent);
            }
            return Optional.of(agent + OPTIONS_SEPARATOR + placeholders.resolve(options.trim()));
        }
    }

    /** More precise stack traces, by recording method information away from safepoints. */
    record DebugNonSafepoints(boolean enabled) implements JvmFeature {

        private static final String OPTIONS = "-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints";

        @Override
        public Optional<String> render(Path sessionPath, Placeholders placeholders) {
            return enabled ? Optional.of(OPTIONS) : Optional.empty();
        }
    }

    /** The hsperfdata file Jeffrey reads to detect that a session has finished. */
    record PerfCounters(boolean enabled) implements JvmFeature {

        public static final String FILE = "perf-counters.hsperfdata";

        private static final String OPTIONS =
                "-XX:+UsePerfData -XX:PerfDataSaveFile=" + CURRENT_SESSION + "/" + FILE;

        @Override
        public Optional<String> render(Path sessionPath, Placeholders placeholders) {
            return enabled ? Optional.of(placeholders.resolve(OPTIONS)) : Optional.empty();
        }
    }

    /** A heap dump on OutOfMemoryError, and what the JVM does afterwards. */
    record HeapDump(HeapDumpType type) implements JvmFeature {

        private static final String BASE_OPTIONS = "-XX:+HeapDumpOnOutOfMemoryError "
                + "-XX:HeapDumpGzipLevel=1 "
                + "-XX:HeapDumpPath=" + CURRENT_SESSION + "/heap-dump.hprof.gz ";

        private static final String CRASH_OPTIONS = BASE_OPTIONS
                + "-XX:+CrashOnOutOfMemoryError "
                + "-XX:ErrorFile=" + CURRENT_SESSION + "/hs-jvm-err.log";

        private static final String EXIT_OPTIONS = BASE_OPTIONS + "-XX:+ExitOnOutOfMemoryError";

        @Override
        public Optional<String> render(Path sessionPath, Placeholders placeholders) {
            if (type == null) {
                return Optional.empty();
            }
            String options = switch (type) {
                case CRASH -> CRASH_OPTIONS;
                case EXIT -> EXIT_OPTIONS;
            };
            return Optional.of(placeholders.resolve(options));
        }
    }

    /**
     * The JFR thresholds a traced session needs, carried by a recording of their own.
     *
     * <p>The profiler starts its recording from a stock JFR configuration that keeps these events
     * above the durations a trace is read at, and its options come either from the configured
     * {@code profiler-command} or from the built-in default, so lowering the thresholds by
     * rewriting them would mean parsing whatever a deployment wrote. A second recording sidesteps
     * that: JFR applies the
     * most verbose setting across every active recording, so this one lowers the thresholds for the
     * profiler's recording as well without either knowing about the other.
     *
     * <p>The events all land in the same repository chunks, so the profiler's dumped
     * {@code .jfr} files carry them for analysis.
     *
     * <p>No {@code settings=} is given, which leaves this recording on the JVM's default
     * configuration — the same one the profiler records with, so the union is unchanged by it.
     * Naming {@code settings=none} to keep the recording bare is what a reader expects here and is
     * exactly wrong: it drops the event settings spelled out beside it and the recording carries
     * nothing at all. {@code maxage} bounds how long this recording pins repository chunks.
     */
    record TracingEventThresholds(boolean tracingEnabled, String eventSettings) implements JvmFeature {

        /** Opts a deployment out while leaving tracing itself on. */
        static final String DISABLED = "none";

        private static final String OPTIONS_PREFIX =
                "-XX:StartFlightRecording:name=jeffrey-tracing-thresholds,maxage=30m,";

        @Override
        public Optional<String> render(Path sessionPath, Placeholders placeholders) {
            if (!tracingEnabled || eventSettings == null || eventSettings.isBlank()) {
                return Optional.empty();
            }
            if (DISABLED.equalsIgnoreCase(eventSettings.trim())) {
                return Optional.empty();
            }
            return Optional.of(OPTIONS_PREFIX + eventSettings.trim());
        }
    }

    /**
     * Where the {@code jeffrey-heartbeat} library writes this session's liveness files, and
     * whether it writes them at all.
     *
     * <p>Carried as JVM system properties rather than left to the {@code .env} file, because the
     * {@code .env} reaches almost nobody: it is written only when a deployment names an
     * {@code env-file}, and the container entrypoint execs the JVM with the argfile without
     * sourcing a shell file at all. The argfile is the one channel every deployment path
     * delivers, and {@code HeartbeatSettings} already resolves a system property ahead of the
     * matching environment variable. Without this the library would sit inert in a provisioned
     * container while the session declared that it reports — and the hub would finish that
     * session at its start timestamp, seconds after the JVM came up.
     *
     * <p>The switched-off case renders an explicit {@code false} rather than nothing, which is
     * why this feature departs from the "off means empty" convention beside it: a session that
     * declared no liveness has to stand the library down even where the application carries the
     * dependency and an environment variable left over from another run says to report.
     *
     * <p>The directory is quoted because a session path may carry a space; {@code JvmOptions}
     * consumes the quotes when it splits and puts them back when it writes the argfile.
     */
    record Heartbeat(boolean enabled) implements JvmFeature {

        private static final String DIRECTORY_OPTION = "-D" + HeartbeatConstants.DIRECTORY_PROPERTY
                + "=\"" + CURRENT_SESSION + "/" + HeartbeatConstants.HEARTBEAT_DIR + "\"";

        private static final String ENABLED_OPTION = "-D" + HeartbeatConstants.ENABLED_PROPERTY + "=true";
        private static final String DISABLED_OPTION = "-D" + HeartbeatConstants.ENABLED_PROPERTY + "=false";

        private static final String OPTION_SEPARATOR = " ";

        @Override
        public Optional<String> render(Path sessionPath, Placeholders placeholders) {
            if (!enabled) {
                return Optional.of(DISABLED_OPTION);
            }
            return Optional.of(placeholders.resolve(DIRECTORY_OPTION) + OPTION_SEPARATOR + ENABLED_OPTION);
        }
    }

    /** Whatever else the deployment asked for. */
    record AdditionalOptions(String options) implements JvmFeature {

        @Override
        public Optional<String> render(Path sessionPath, Placeholders placeholders) {
            if (options == null || options.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(placeholders.resolve(options));
        }
    }
}
