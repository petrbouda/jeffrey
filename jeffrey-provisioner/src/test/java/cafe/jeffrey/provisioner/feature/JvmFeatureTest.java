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
import cafe.jeffrey.provisioner.placeholder.JeffreyPlaceholderSource;
import cafe.jeffrey.provisioner.placeholder.Placeholders;
import cafe.jeffrey.shared.common.HeartbeatConstants;
import cafe.jeffrey.shared.common.JeffreyLayout;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmFeatureTest {

    private static final Path SESSION = Path.of("/tmp/sessions/session-123");

    private static final Placeholders PLACEHOLDERS =
            Placeholders.of(JeffreyPlaceholderSource.ofSession(SESSION));

    private static String render(JvmFeature feature) {
        return feature.render(SESSION, PLACEHOLDERS).orElse(null);
    }

    @Nested
    class DebugNonSafepoints {

        @Test
        void unlocksDiagnosticOptions() {
            assertEquals("-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints",
                    render(new JvmFeature.DebugNonSafepoints(true)));
        }

        @Test
        void rendersNothingWhenDisabled() {
            assertEquals(Optional.empty(), new JvmFeature.DebugNonSafepoints(false).render(SESSION, PLACEHOLDERS));
        }
    }

    @Nested
    class PerfCounters {

        @Test
        void writesTheCounterFileIntoTheSession() {
            assertEquals("-XX:+UsePerfData -XX:PerfDataSaveFile=" + SESSION + "/" + JvmFeature.PerfCounters.FILE,
                    render(new JvmFeature.PerfCounters(true)));
        }

        @Test
        void rendersNothingWhenDisabled() {
            assertEquals(Optional.empty(), new JvmFeature.PerfCounters(false).render(SESSION, PLACEHOLDERS));
        }
    }

    @Nested
    class HeapDump {

        @Test
        void exitTypeStopsTheJvmAfterDumping() {
            String options = render(new JvmFeature.HeapDump(HeapDumpType.EXIT));

            assertTrue(options.contains("-XX:+HeapDumpOnOutOfMemoryError"), options);
            assertTrue(options.contains("-XX:HeapDumpPath=" + SESSION + "/heap-dump.hprof.gz"), options);
            assertTrue(options.contains("-XX:+ExitOnOutOfMemoryError"), options);
        }

        @Test
        void crashTypeWritesAnErrorFileToo() {
            String options = render(new JvmFeature.HeapDump(HeapDumpType.CRASH));

            assertTrue(options.contains("-XX:+CrashOnOutOfMemoryError"), options);
            assertTrue(options.contains("-XX:ErrorFile=" + SESSION + "/hs-jvm-err.log"), options);
        }

        @Test
        void rendersNothingWithoutAType() {
            assertEquals(Optional.empty(), new JvmFeature.HeapDump(null).render(SESSION, PLACEHOLDERS));
        }
    }

    @Nested
    class JvmLogging {

        @Test
        void resolvesPlaceholdersInTheCommand() {
            assertEquals("-Xlog:jfr*=trace:file=" + SESSION + "/jfr.log",
                    render(new JvmFeature.JvmLogging("jfr*=trace:file=<<JEFFREY:CURRENT_SESSION>>/jfr.log")));
        }

        @Test
        void rendersNothingWithoutACommand() {
            assertEquals(Optional.empty(), new JvmFeature.JvmLogging(null).render(SESSION, PLACEHOLDERS));
            assertEquals(Optional.empty(), new JvmFeature.JvmLogging("  ").render(SESSION, PLACEHOLDERS));
        }
    }

    @Nested
    class Agent {

        private static final AppIdentity IDENTITY = new AppIdentity(
                "workspace-1", "project-1", "my-service", "My Service",
                "instance-1", "session-1", 3, Map.of("cluster", "blue"), 1700000000000L);

        @Test
        void loadsTheAgentAndPointsJfrAtTheStreamingRepository() {
            String options = render(new JvmFeature.Agent("/libs/jeffrey-agent.jar", false, IDENTITY));

            assertTrue(options.startsWith("-javaagent:/libs/jeffrey-agent.jar="), options);
            assertTrue(options.contains("-XX:FlightRecorderOptions=repository="
                    + SESSION + "/" + JeffreyLayout.STREAMING_REPO_DIR), options);
        }

        @Test
        void passesTheHeartbeatDirectoryFirst() {
            String options = render(new JvmFeature.Agent("/agent.jar", false, IDENTITY));

            assertTrue(options.contains(HeartbeatConstants.PARAM_DIR + "="
                    + SESSION.resolve(HeartbeatConstants.HEARTBEAT_DIR)), options);
        }

        @Test
        void addsTracingOnlyWhenAsked() {
            assertTrue(render(new JvmFeature.Agent("/agent.jar", true, IDENTITY)).contains("tracing.enabled=true"));
            assertTrue(!render(new JvmFeature.Agent("/agent.jar", false, IDENTITY)).contains("tracing.enabled"));
        }

        /** The streaming repository is useless without the agent that reads it. */
        @Test
        void rendersNothingWithoutAnAgentPath() {
            assertEquals(Optional.empty(), new JvmFeature.Agent(null, true, IDENTITY).render(SESSION, PLACEHOLDERS));
            assertEquals(Optional.empty(), new JvmFeature.Agent("  ", true, IDENTITY).render(SESSION, PLACEHOLDERS));
        }
    }

    @Nested
    class AdditionalOptions {

        @Test
        void resolvesPlaceholders() {
            assertEquals("-Xmx1g -Dlog=" + SESSION + "/app.log",
                    render(new JvmFeature.AdditionalOptions("-Xmx1g -Dlog=<<JEFFREY:CURRENT_SESSION>>/app.log")));
        }

        @Test
        void rendersNothingWhenEmpty() {
            assertEquals(Optional.empty(), new JvmFeature.AdditionalOptions(null).render(SESSION, PLACEHOLDERS));
        }
    }
}
