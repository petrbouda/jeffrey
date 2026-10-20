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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

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

    /** Files the options refer to, by name, to be written into the session directory beside them. */
    default Map<String, String> files() {
        return Map.of();
    }

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
     * JFR events Jeffrey records beside the profiler: the thresholds a traced session needs, and
     * whatever events the deployment lists in {@code jfr.add-event-settings}, carried by a recording of
     * their own. The deployment's entries come after the tracing ones, so they win where both name
     * the same setting.
     *
     * <p>The profiler starts its recording from a configuration that comes either from the
     * configured {@code profiler-command} or from the built-in default, so changing its settings
     * would mean rewriting whatever a deployment wrote. A second recording sidesteps that: JFR
     * enables an event while any active recording enables it and applies the most verbose
     * threshold across them, so this one switches events on and lowers thresholds for the
     * profiler's recording as well, without either knowing about the other. It cannot switch an
     * event off, which {@link EventSettingsJfc} refuses. It is also untouched by async-profiler,
     * which switches events off only in its own {@code jfrsync} recording — so lock waits keep
     * their spans while the lock engine runs.
     *
     * <p>The events all land in the same repository chunks, so the profiler's dumped
     * {@code .jfr} files carry them for analysis.
     *
     * <p>{@code settings=} names a configuration holding just these settings ({@link #FILE}). Both
     * shorter spellings are wrong: without {@code settings=} the recording runs the JVM's whole
     * {@code default.jfc} on top of the profiler's, switching back on what async-profiler switched
     * off for its own engines ({@code jdk.ExecutionSample}, allocation samples); and
     * {@code settings=none} drops the event settings given inline, so the recording carries nothing.
     * {@code maxage} bounds how long this recording pins repository chunks. The option is quoted
     * when the session path carries a space, which {@code JvmOptions} reads back as one option.
     *
     * @param tracingSettings the tracing thresholds, {@code null} when tracing is off, or
     *                        {@code "none"} when a deployment opted out of them
     * @param eventSettings   the deployment's own event settings, {@code null} when it lists none
     */
    record JfrEvents(String tracingSettings, String eventSettings) implements JvmFeature {

        public static final String FILE = ".jeffrey-events.jfc";

        /** Opts a deployment out of the tracing thresholds while leaving tracing itself on. */
        static final String DISABLED = "none";

        private static final String OPTIONS_PREFIX =
                "-XX:StartFlightRecording:name=jeffrey-events,maxage=30m,settings=";
        private static final String SETTINGS_SEPARATOR = ",";
        private static final String QUOTE = "\"";

        @Override
        public Optional<String> render(Path sessionPath, Placeholders placeholders) {
            if (settings().isEmpty()) {
                return Optional.empty();
            }
            String option = OPTIONS_PREFIX + placeholders.resolve(CURRENT_SESSION + "/" + FILE);
            boolean hasWhitespace = option.chars().anyMatch(Character::isWhitespace);
            return Optional.of(hasWhitespace ? QUOTE + option + QUOTE : option);
        }

        @Override
        public Map<String, String> files() {
            return settings().map(settings -> Map.of(FILE, EventSettingsJfc.render(settings))).orElse(Map.of());
        }

        /** The tracing settings, then the deployment's, or empty when neither lists anything. */
        private Optional<String> settings() {
            return Stream.of(tracingSettings, eventSettings)
                    .filter(settings -> settings != null && !settings.isBlank())
                    .filter(settings -> !DISABLED.equalsIgnoreCase(settings.trim()))
                    .reduce((tracing, deployment) -> tracing + SETTINGS_SEPARATOR + deployment);
        }
    }

    /**
     * Where this session's liveness files go, read alike by the Jeffrey agent ({@link JeffreyAgent})
     * and by the {@code jeffrey-heartbeat} library, whichever of them the JVM runs with.
     *
     * <p>Only the directory, never whether to report: both writers are on unless the application
     * itself switches them off. A JVM with neither ignores the property.
     *
     * <p>Carried as a JVM system property rather than left to the {@code .env} file, because the
     * {@code .env} reaches almost nobody: it is written only when a deployment names an
     * {@code env-file}, and the container entrypoint execs the JVM with the argfile without
     * sourcing a shell file at all. The argfile is the one channel every deployment path
     * delivers, and {@code HeartbeatSettings} already resolves a system property ahead of the
     * matching environment variable.
     *
     * <p>The directory is quoted because a session path may carry a space; {@code JvmOptions}
     * consumes the quotes when it splits and puts them back when it writes the argfile.
     */
    record Heartbeat() implements JvmFeature {

        private static final String DIRECTORY_OPTION = "-D" + HeartbeatConstants.DIRECTORY_PROPERTY
                + "=\"" + CURRENT_SESSION + "/" + HeartbeatConstants.HEARTBEAT_DIR + "\"";

        @Override
        public Optional<String> render(Path sessionPath, Placeholders placeholders) {
            return Optional.of(placeholders.resolve(DIRECTORY_OPTION));
        }
    }

    /**
     * The Jeffrey agent, which reports the JVM's liveness to the hub without the application
     * carrying {@code jeffrey-heartbeat}. It takes no arguments: it reads the directory from the
     * same {@code -Djeffrey.heartbeat.dir} that {@link Heartbeat} writes for the library.
     *
     * <p>A {@code null} jar means the agent is switched off ({@code jeffrey-agent.enabled = false})
     * or the jar {@code jeffrey-agent.path} names is not there, and renders nothing. The path is
     * quoted because it may carry a space, as a session path may in {@link Heartbeat}.
     */
    record JeffreyAgent(Path jar) implements JvmFeature {

        public static final String JAVA_AGENT_OPTION = "-javaagent:";

        public static JeffreyAgent disabled() {
            return new JeffreyAgent(null);
        }

        public boolean enabled() {
            return jar != null;
        }

        @Override
        public Optional<String> render(Path sessionPath, Placeholders placeholders) {
            if (!enabled()) {
                return Optional.empty();
            }
            return Optional.of(JAVA_AGENT_OPTION + "\"" + jar + "\"");
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
