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

import cafe.jeffrey.provisioner.JvmOptions;
import cafe.jeffrey.provisioner.model.HeapDumpType;
import cafe.jeffrey.provisioner.placeholder.JeffreyPlaceholderSource;
import cafe.jeffrey.provisioner.placeholder.Placeholders;
import cafe.jeffrey.shared.common.HeartbeatConstants;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmFeatureTest {

    private static final Path SESSION = Path.of("/tmp/sessions/session-123");

    private static final Placeholders PLACEHOLDERS =
            Placeholders.of(JeffreyPlaceholderSource.ofSession(SESSION));

    private static String render(JvmFeature feature) {
        return feature.render(SESSION, PLACEHOLDERS).orElse(null);
    }

    @Nested
    class AsyncProfiler {

        @Test
        void loadsTheLibraryWithItsOptions() {
            assertEquals("-agentpath:/opt/lib.so=start,event=cpu",
                    render(new JvmFeature.AsyncProfiler("/opt/lib.so", "start,event=cpu")));
        }

        @Test
        void loadsTheLibraryAloneWhenThereAreNoOptions() {
            assertEquals("-agentpath:/opt/lib.so", render(new JvmFeature.AsyncProfiler("/opt/lib.so", null)));
        }

        @Test
        void resolvesTheSessionIntoTheOptions() {
            assertEquals("-agentpath:/opt/lib.so=start,file=" + SESSION + "/profile-%t.jfr",
                    render(new JvmFeature.AsyncProfiler(
                            "/opt/lib.so", "start,file=" + JvmFeature.CURRENT_SESSION + "/profile-%t.jfr")));
        }

        @Test
        void rendersNothingWhenDisabled() {
            assertEquals(Optional.empty(), JvmFeature.AsyncProfiler.disabled().render(SESSION, PLACEHOLDERS));
        }
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
    class JfrEvents {

        private static final String SETTINGS =
                "jdk.SocketRead#enabled=true,jdk.SocketRead#threshold=0ms";

        private static String configuration(JvmFeature.JfrEvents feature) {
            return feature.files().get(JvmFeature.JfrEvents.FILE);
        }

        @Test
        void startsARecordingFromItsOwnConfiguration() {
            String options = render(new JvmFeature.JfrEvents(SETTINGS, null));

            assertEquals("-XX:StartFlightRecording:name=jeffrey-events,maxage=30m,settings="
                    + SESSION + "/" + JvmFeature.JfrEvents.FILE, options);
        }

        @Test
        void writesTheSettingsIntoThatConfiguration() {
            String jfc = configuration(new JvmFeature.JfrEvents(SETTINGS, null));

            assertTrue(jfc.contains("<event name=\"jdk.SocketRead\">"), jfc);
            assertTrue(jfc.contains("<setting name=\"threshold\">0ms</setting>"), jfc);
        }

        /** The deployment's events come after the tracing ones, so its settings win. */
        @Test
        void appliesTheDeploymentsSettingsAfterTheTracingOnes() {
            String jfc = configuration(new JvmFeature.JfrEvents(
                    SETTINGS, "jdk.SocketRead#threshold=5ms,jdk.JavaExceptionThrow#stackTrace=true"));

            assertTrue(jfc.contains("<setting name=\"threshold\">5ms</setting>"), jfc);
            assertFalse(jfc.contains("<setting name=\"threshold\">0ms</setting>"), jfc);
            assertTrue(jfc.contains("<event name=\"jdk.JavaExceptionThrow\">"), jfc);
        }

        /** A deployment's own events are recorded with tracing off too. */
        @Test
        void recordsTheDeploymentsEventsWithoutTracing() {
            JvmFeature.JfrEvents feature = new JvmFeature.JfrEvents(null, "jdk.JavaExceptionThrow#stackTrace=true");

            assertTrue(render(feature).startsWith("-XX:StartFlightRecording:name=jeffrey-events,"));
            assertFalse(configuration(feature).contains("jdk.SocketRead"));
        }

        /** An unbounded recording would pin repository chunks for the life of the JVM. */
        @Test
        void boundsHowLongItPinsChunks() {
            String options = render(new JvmFeature.JfrEvents(SETTINGS, null));

            assertTrue(options.contains("maxage="), options);
        }

        /** A session path with a space must still reach the JVM as one option. */
        @Test
        void quotesASessionPathCarryingASpace() {
            Path session = Path.of("/data/my sessions/s1");
            Placeholders placeholders = Placeholders.of(JeffreyPlaceholderSource.ofSession(session));

            String options = new JvmFeature.JfrEvents(SETTINGS, null)
                    .render(session, placeholders).orElseThrow();

            assertEquals(List.of("-XX:StartFlightRecording:name=jeffrey-events,maxage=30m,"
                    + "settings=/data/my sessions/s1/" + JvmFeature.JfrEvents.FILE),
                    JvmOptions.split(options));
        }

        /** The default list is what a session gets when nothing overrides it. */
        @Test
        void defaultSettingsCoverEveryPromotedEvent() {
            String jfc = configuration(new JvmFeature.JfrEvents(TracingJfrEvents.DEFAULT_SETTINGS, null));

            for (TracingJfrEvents.EventThreshold event : TracingJfrEvents.DEFAULT_EVENTS) {
                assertTrue(jfc.contains("<event name=\"" + event.eventType() + "\">"), event.eventType());
            }
        }

        @Test
        void rendersNothingWithoutSettings() {
            JvmFeature.JfrEvents feature = new JvmFeature.JfrEvents(null, null);

            assertEquals(Optional.empty(), feature.render(SESSION, PLACEHOLDERS));
            assertEquals(Map.of(), feature.files());
            assertEquals(Map.of(), new JvmFeature.JfrEvents("  ", " ").files());
        }

        /** The documented opt-out: keep method tracing, drop its thresholds. */
        @Test
        void rendersNothingForTheNoneOptOut() {
            assertEquals(Optional.empty(),
                    new JvmFeature.JfrEvents("none", null).render(SESSION, PLACEHOLDERS));
            assertEquals(Map.of(), new JvmFeature.JfrEvents(" NONE ", null).files());
        }
    }

    @Nested
    class Heartbeat {

        @Test
        void namesTheHeartbeatDirectoryInsideTheSession() {
            assertEquals(
                    "-Djeffrey.heartbeat.dir=\"/tmp/sessions/session-123/.heartbeat\"",
                    render(new JvmFeature.Heartbeat()));
        }

        /**
         * The argfile is the only channel that reaches a JVM the container entrypoint execs: the
         * generated {@code .env} is opt-in and nothing sources it there.
         */
        @Test
        void carriesTheDirectoryAsASystemProperty() {
            assertTrue(render(new JvmFeature.Heartbeat())
                    .startsWith("-D" + HeartbeatConstants.DIRECTORY_PROPERTY + "="));
        }

        /**
         * Whether to report is the application's decision alone, so nothing here may switch the
         * library on or off.
         */
        @Test
        void neverDecidesWhetherTheApplicationReports() {
            assertFalse(render(new JvmFeature.Heartbeat()).contains("jeffrey.heartbeat.enabled"));
        }

        @Test
        void quotesTheDirectorySoASessionPathMayCarryASpace() {
            Path spaced = Path.of("/tmp/my sessions/session-123");
            JvmFeature.Heartbeat feature = new JvmFeature.Heartbeat();

            String rendered = feature
                    .render(spaced, Placeholders.of(JeffreyPlaceholderSource.ofSession(spaced)))
                    .orElseThrow();

            assertEquals("-Djeffrey.heartbeat.dir=\"/tmp/my sessions/session-123/.heartbeat\"", rendered);
            assertEquals(
                    java.util.List.of("-Djeffrey.heartbeat.dir=/tmp/my sessions/session-123/.heartbeat"),
                    JvmOptions.split(rendered),
                    "the quotes must survive the split into individual argfile lines");
        }
    }

    @Nested
    class JeffreyAgent {

        @Test
        void attachesTheJarTheAgentPathNames() {
            assertEquals("-javaagent:\"/opt/jeffrey/jeffrey-agent.jar\"",
                    render(new JvmFeature.JeffreyAgent(Path.of("/opt/jeffrey/jeffrey-agent.jar"))));
        }

        /** The JVM reads the quoted option as one, however the agent path is spelled. */
        @Test
        void survivesASpaceInTheAgentPath() {
            String option = render(new JvmFeature.JeffreyAgent(Path.of("/opt/my tools/jeffrey-agent.jar")));

            assertEquals(List.of("-javaagent:/opt/my tools/jeffrey-agent.jar"), JvmOptions.split(option));
        }

        @Test
        void rendersNothingWhenDisabled() {
            assertEquals(Optional.empty(), JvmFeature.JeffreyAgent.disabled().render(SESSION, PLACEHOLDERS));
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
