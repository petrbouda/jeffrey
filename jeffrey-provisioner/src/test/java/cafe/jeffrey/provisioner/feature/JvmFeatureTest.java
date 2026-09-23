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
    class TracingEventThresholds {

        private static final String SETTINGS =
                "jdk.SocketRead#enabled=true,jdk.SocketRead#threshold=0ms";

        @Test
        void startsARecordingCarryingTheThresholds() {
            String options = render(new JvmFeature.TracingEventThresholds(true, SETTINGS));

            assertTrue(options.startsWith("-XX:StartFlightRecording:"), options);
            assertTrue(options.endsWith(SETTINGS), options);
        }

        /**
         * {@code settings=none} reads like the way to keep this recording bare, and instead makes
         * the JVM discard every event setting given with it — the recording then carries nothing.
         */
        @Test
        void neverNamesASettingsConfiguration() {
            String options = render(
                    new JvmFeature.TracingEventThresholds(true, TracingJfrEvents.DEFAULT_SETTINGS));

            assertTrue(!options.contains("settings="), options);
        }

        /** An unbounded recording would pin repository chunks for the life of the JVM. */
        @Test
        void boundsHowLongItPinsChunks() {
            String options = render(new JvmFeature.TracingEventThresholds(true, SETTINGS));

            assertTrue(options.contains("maxage="), options);
        }

        /** The default list is what a session gets when nothing overrides it. */
        @Test
        void defaultSettingsCoverEveryPromotedEvent() {
            String options = render(
                    new JvmFeature.TracingEventThresholds(true, TracingJfrEvents.DEFAULT_SETTINGS));

            assertTrue(options.contains("jdk.SocketRead#threshold=0ms"), options);
            assertTrue(options.contains("jdk.SocketWrite#threshold=0ms"), options);
            assertTrue(options.contains("jdk.FileRead#threshold=0ms"), options);
            assertTrue(options.contains("jdk.FileWrite#threshold=0ms"), options);
            assertTrue(options.contains("jdk.FileForce#threshold=0ms"), options);
            assertTrue(options.contains("jdk.JavaMonitorEnter#threshold=1ms"), options);
            assertTrue(options.contains("jdk.JavaMonitorWait#threshold=1ms"), options);
            assertTrue(options.contains("jdk.ThreadPark#threshold=1ms"), options);
            assertTrue(options.contains("jdk.ThreadSleep#threshold=1ms"), options);
            assertTrue(options.contains("jdk.VirtualThreadPinned#threshold=1ms"), options);
            assertTrue(options.contains("jdk.ZAllocationStall#threshold=0ms"), options);
        }

        /** A threshold is ignored for an event a custom profiler configuration switched off. */
        @Test
        void everyEventIsEnabledAlongsideItsThreshold() {
            String options = render(
                    new JvmFeature.TracingEventThresholds(true, TracingJfrEvents.DEFAULT_SETTINGS));

            for (TracingJfrEvents.EventThreshold event : TracingJfrEvents.DEFAULT_EVENTS) {
                assertTrue(options.contains(event.eventType() + "#enabled=true"), event.eventType());
            }
        }

        /**
         * Java 25 rate-limits socket and file I/O to 100 events a second, which a threshold does
         * not lift. The lift must be a numeric rate: JFR resolves {@code throttle} to the highest
         * parseable rate across the active recordings, and {@code off} never parses as one, so it
         * loses to the {@code 100/s} the profiler's {@code default.jfc} recording carries.
         * {@code jdk.FileForce} has no such limit, and naming a setting an event does not have
         * costs a JFR warning at startup.
         */
        @Test
        void liftsTheRateLimitOnlyWhereThereIsOneToLift() {
            String options = render(
                    new JvmFeature.TracingEventThresholds(true, TracingJfrEvents.DEFAULT_SETTINGS));

            assertTrue(options.contains("jdk.SocketRead#throttle=1000000/s"), options);
            assertTrue(options.contains("jdk.SocketWrite#throttle=1000000/s"), options);
            assertTrue(options.contains("jdk.FileRead#throttle=1000000/s"), options);
            assertTrue(options.contains("jdk.FileWrite#throttle=1000000/s"), options);
            assertTrue(!options.contains("jdk.FileForce#throttle"), options);
            assertTrue(!options.contains("jdk.ThreadPark#throttle"), options);
        }

        @Test
        void rendersNothingWhenTracingIsOff() {
            assertEquals(Optional.empty(),
                    new JvmFeature.TracingEventThresholds(false, SETTINGS).render(SESSION, PLACEHOLDERS));
        }

        @Test
        void rendersNothingWithoutSettings() {
            assertEquals(Optional.empty(),
                    new JvmFeature.TracingEventThresholds(true, null).render(SESSION, PLACEHOLDERS));
            assertEquals(Optional.empty(),
                    new JvmFeature.TracingEventThresholds(true, "  ").render(SESSION, PLACEHOLDERS));
        }

        /** The documented opt-out: keep method tracing, drop the extra recording. */
        @Test
        void rendersNothingForTheNoneOptOut() {
            assertEquals(Optional.empty(),
                    new JvmFeature.TracingEventThresholds(true, "none").render(SESSION, PLACEHOLDERS));
            assertEquals(Optional.empty(),
                    new JvmFeature.TracingEventThresholds(true, " NONE ").render(SESSION, PLACEHOLDERS));
        }
    }

    @Nested
    class Heartbeat {

        @Test
        void namesTheHeartbeatDirectoryInsideTheSession() {
            assertEquals(
                    "-Djeffrey.heartbeat.dir=\"/tmp/sessions/session-123/.heartbeat\" "
                            + "-Djeffrey.heartbeat.enabled=true",
                    render(new JvmFeature.Heartbeat(true)));
        }

        /**
         * The argfile is the only channel that reaches a JVM the container entrypoint execs: the
         * generated {@code .env} is opt-in and nothing sources it there. Carried only in the
         * environment, the library would sit inert while the session declared that it reports,
         * and the hub would finish that session at its own start timestamp.
         */
        @Test
        void carriesTheSettingsAsSystemProperties() {
            String rendered = render(new JvmFeature.Heartbeat(true));

            assertTrue(rendered.contains("-D" + HeartbeatConstants.DIRECTORY_PROPERTY + "="));
            assertTrue(rendered.contains("-D" + HeartbeatConstants.ENABLED_PROPERTY + "="));
        }

        /**
         * Unlike every other feature here, being switched off is not the same as having nothing to
         * say: a session that declared no liveness has to stand a library that is present down.
         */
        @Test
        void saysSoExplicitlyWhenTheSessionDeclaredNothing() {
            assertEquals("-Djeffrey.heartbeat.enabled=false", render(new JvmFeature.Heartbeat(false)));
        }

        @Test
        void namesNoDirectoryWhenTheSessionDeclaredNothing() {
            assertFalse(render(new JvmFeature.Heartbeat(false))
                    .contains(HeartbeatConstants.DIRECTORY_PROPERTY));
        }

        @Test
        void quotesTheDirectorySoASessionPathMayCarryASpace() {
            Path spaced = Path.of("/tmp/my sessions/session-123");
            JvmFeature.Heartbeat feature = new JvmFeature.Heartbeat(true);

            String rendered = feature
                    .render(spaced, Placeholders.of(JeffreyPlaceholderSource.ofSession(spaced)))
                    .orElseThrow();

            assertEquals(
                    "-Djeffrey.heartbeat.dir=\"/tmp/my sessions/session-123/.heartbeat\" "
                            + "-Djeffrey.heartbeat.enabled=true",
                    rendered);
            assertEquals(
                    java.util.List.of(
                            "-Djeffrey.heartbeat.dir=/tmp/my sessions/session-123/.heartbeat",
                            "-Djeffrey.heartbeat.enabled=true"),
                    JvmOptions.split(rendered),
                    "the quotes must survive the split into individual argfile lines");
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
