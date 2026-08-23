/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.provisioner.feature;

import cafe.jeffrey.provisioner.AppIdentity;
import cafe.jeffrey.provisioner.model.HeapDumpType;
import cafe.jeffrey.provisioner.placeholder.Placeholders;
import cafe.jeffrey.shared.common.CliConstants;
import cafe.jeffrey.shared.common.HeartbeatConstants;
import cafe.jeffrey.shared.common.JeffreyLayout;

import java.nio.file.Path;
import java.util.List;
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

    /** A user-supplied {@code -Xlog} command. */
    record JvmLogging(String command) implements JvmFeature {

        private static final String PREFIX = "-Xlog:";

        @Override
        public Optional<String> render(Path sessionPath, Placeholders placeholders) {
            if (command == null || command.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(PREFIX + placeholders.resolve(command));
        }
    }

    /**
     * The Jeffrey agent, plus the JFR repository it streams from. The two travel together: the
     * agent is what reads the repository, so pointing the JVM at one without loading the other
     * leaves recordings nobody collects.
     */
    record Agent(String agentPath, boolean methodTracingEnabled, AppIdentity identity) implements JvmFeature {

        private static final String AGENT_OPTION_PREFIX = "-javaagent:";
        private static final String AGENT_ARGS_SEPARATOR = "=";

        private static final String STREAMING_OPTIONS = "-XX:FlightRecorderOptions=repository="
                + CURRENT_SESSION + "/" + JeffreyLayout.STREAMING_REPO_DIR;

        @Override
        public Optional<String> render(Path sessionPath, Placeholders placeholders) {
            if (agentPath == null || agentPath.isBlank()) {
                return Optional.empty();
            }

            String heartbeatDir = sessionPath.resolve(HeartbeatConstants.HEARTBEAT_DIR).toString();
            String agentOption = AGENT_OPTION_PREFIX + agentPath + AGENT_ARGS_SEPARATOR
                    + AgentArguments.of(heartbeatDir, methodTracingEnabled, identity);

            return Optional.of(String.join(" ", List.of(agentOption, placeholders.resolve(STREAMING_OPTIONS))));
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
