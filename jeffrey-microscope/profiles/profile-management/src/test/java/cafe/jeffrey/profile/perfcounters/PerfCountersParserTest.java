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

package cafe.jeffrey.profile.perfcounters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PerfCountersParserTest {

    @TempDir
    Path dir;

    @Nested
    class Format {

        @Test
        void readsNumbersAndStrings() {
            Path file = new PerfDataFixture()
                    .number("java.threads.live", 12)
                    .text("sun.gc.policy.name", "GarbageFirst")
                    .writeTo(dir.resolve("app.hsperfdata"));

            Map<String, Object> counters = PerfCountersParser.parse(file);

            assertEquals(12L, counters.get("java.threads.live"));
            assertEquals("GarbageFirst", counters.get("sun.gc.policy.name"));
        }

        @Test
        void refusesAFileThatIsNotPerfData() throws IOException {
            Path file = Files.writeString(dir.resolve("gc.log"), "[0.001s][info][gc] Using G1");

            assertThrows(IllegalArgumentException.class, () -> PerfCountersParser.parse(file));
        }

        @Test
        void refusesAnotherVersionOfTheFormat() {
            Path file = new PerfDataFixture()
                    .version(1)
                    .number("java.threads.live", 12)
                    .writeTo(dir.resolve("old.hsperfdata"));

            assertThrows(IllegalArgumentException.class, () -> PerfCountersParser.parse(file));
        }

        @Test
        void refusesATruncatedFile() throws IOException {
            Path whole = new PerfDataFixture()
                    .number("java.threads.live", 12)
                    .writeTo(dir.resolve("whole.hsperfdata"));
            byte[] bytes = Files.readAllBytes(whole);
            Path cut = Files.write(dir.resolve("cut.hsperfdata"), Arrays.copyOf(bytes, bytes.length - 12));

            assertThrows(IllegalArgumentException.class, () -> PerfCountersParser.parse(cut));
        }
    }

    @Nested
    class Ticks {

        @Test
        void areConvertedToNanosecondsByTheFilesOwnFrequency() {
            Path file = new PerfDataFixture()
                    .frequency(1_000_000)
                    .ticks("sun.rt.safepointTime", 2_500)
                    .number("sun.rt.safepoints", 2_500)
                    .writeTo(dir.resolve("app.hsperfdata"));

            Map<String, Object> counters = PerfCountersParser.parse(file);

            assertEquals(2_500_000L, counters.get("sun.rt.safepointTime"));
            assertEquals(2_500L, counters.get("sun.rt.safepoints"), "a count is not a tick");
        }

        /** A whole number of nanoseconds per tick is zero above 1 GHz, which erased every duration. */
        @Test
        void keepTheirValueOnATimerFasterThanOneGigahertz() {
            assertEquals(1_000_000_000L, PerfCountersParser.toNanos(3_000_000_000L, 3_000_000_000L));
            assertEquals(1L, PerfCountersParser.toNanos(3, 3_000_000_000L));
        }

        @Test
        void doNotOverflowOnALongRun() {
            long thirtyDaysOfTicks = 30L * 24 * 3600 * 2_400_000_000L;

            assertEquals(30L * 24 * 3600 * 1_000_000_000L, PerfCountersParser.toNanos(thirtyDaysOfTicks, 2_400_000_000L));
        }

        @Test
        void stayAsTheyAreWithoutAFrequency() {
            Path file = new PerfDataFixture()
                    .ticks("sun.rt.safepointTime", 2_500)
                    .writeTo(dir.resolve("app.hsperfdata"));

            assertEquals(2_500L, PerfCountersParser.parse(file).get("sun.rt.safepointTime"));
        }
    }

    /**
     * The test JVM's own snapshot, which is what HotSpot really writes. Skipped where the JVM keeps
     * none ({@code -XX:-UsePerfData}, or a temp directory it cannot write).
     */
    @Nested
    class LiveJvm {

        @Test
        void readsTheRunningJvmsOwnSnapshot() {
            Path own = Path.of(System.getProperty("java.io.tmpdir"),
                    "hsperfdata_" + System.getProperty("user.name"),
                    Long.toString(ProcessHandle.current().pid()));
            assumeTrue(Files.isReadable(own), "the JVM keeps no perf-data file");

            Map<String, Object> counters = PerfCountersParser.parse(own);
            PerfCountersOverview overview = PerfCountersOverviewBuilder.build(PerfCounterValues.of(counters));

            assertEquals(System.getProperty("java.vm.name"), overview.jvm().vmName());
            // createVm* are wall-clock epoch milliseconds, not ticks.
            Instant started = ProcessHandle.current().info().startInstant().orElseThrow();
            Instant now = Clock.systemUTC().instant();
            assertTrue(Math.abs(overview.runtime().startedAtEpochMs() - started.toEpochMilli()) < 5_000,
                    "started=" + overview.runtime().startedAtEpochMs() + " expected≈" + started.toEpochMilli());
            assertTrue(overview.runtime().uptimeNanos() > 0);
            assertTrue(overview.runtime().uptimeNanos() / 1_000_000 <= now.toEpochMilli() - started.toEpochMilli() + 1_000);
            assertFalse(overview.gc().collectors().isEmpty());
            assertInstanceOf(Long.class, counters.get("java.threads.live"));
        }
    }
}
