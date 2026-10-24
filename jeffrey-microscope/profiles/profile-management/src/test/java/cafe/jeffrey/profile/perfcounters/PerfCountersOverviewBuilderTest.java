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
import cafe.jeffrey.profile.perfcounters.PerfCountersOverview.Collector;
import cafe.jeffrey.profile.perfcounters.PerfCountersOverview.Generation;
import cafe.jeffrey.profile.perfcounters.PerfCountersOverview.UptimeBasis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfCountersOverviewBuilderTest {

    private static final long SECOND = 1_000_000_000L;

    /** What a G1 JVM writes, trimmed to the counters the overview reads. */
    private static Map<String, Object> g1() {
        Map<String, Object> counters = new HashMap<>();
        counters.put("java.property.java.vm.name", "OpenJDK 64-Bit Server VM");
        counters.put("java.property.java.version", "25.0.5");
        counters.put("sun.gc.policy.name", "GarbageFirst");
        counters.put("sun.gc.policy.collectors", 1L);
        counters.put("sun.rt.createVmBeginTime", 1_792_829_252_701L);
        counters.put("sun.rt.createVmEndTime", 1_792_829_252_789L);
        counters.put("sun.rt.applicationTime", 95 * SECOND);
        counters.put("sun.rt.safepointTime", 5 * SECOND);
        counters.put("sun.rt.safepoints", 120L);
        counters.put("sun.gc.collector.0.name", "G1 young collection pauses");
        counters.put("sun.gc.collector.0.invocations", 7L);
        counters.put("sun.gc.collector.0.time", 3 * SECOND);
        counters.put("sun.gc.collector.1.name", "G1 full collection pauses");
        counters.put("sun.gc.collector.1.invocations", 1L);
        counters.put("sun.gc.collector.1.time", 1 * SECOND);
        counters.put("sun.gc.collector.2.name", "G1 concurrent cycle pauses");
        counters.put("sun.gc.collector.2.invocations", 0L);
        counters.put("sun.gc.collector.2.time", 0L);
        counters.put("sun.gc.generation.0.name", "young");
        counters.put("sun.gc.generation.0.capacity", 58_720_256L);
        counters.put("sun.gc.generation.0.maxCapacity", 268_435_456L);
        counters.put("sun.gc.generation.0.space.0.name", "eden");
        counters.put("sun.gc.generation.0.space.0.used", 4_000_000L);
        counters.put("sun.gc.generation.0.space.1.name", "s0");
        counters.put("sun.gc.generation.0.space.1.used", 0L);
        counters.put("sun.gc.generation.0.space.2.name", "s1");
        counters.put("sun.gc.generation.0.space.2.used", 1_000_000L);
        counters.put("sun.gc.generation.1.name", "old");
        counters.put("sun.gc.generation.1.capacity", 184_549_376L);
        counters.put("sun.gc.generation.1.maxCapacity", 268_435_456L);
        counters.put("sun.gc.generation.1.space.0.name", "space");
        counters.put("sun.gc.generation.1.space.0.used", 107_906_432L);
        counters.put("sun.gc.metaspace.used", 9_680_800L);
        counters.put("sun.gc.metaspace.capacity", 9_895_936L);
        counters.put("java.threads.live", 6L);
        return counters;
    }

    private static PerfCountersOverview overview(Map<String, Object> counters) {
        return PerfCountersOverviewBuilder.build(PerfCounterValues.of(counters));
    }

    @Nested
    class Uptime {

        @Test
        void startsWhenTheJvmWasCreatedAndTakesItsInitTime() {
            PerfCountersOverview overview = overview(g1());

            assertEquals(1_792_829_252_701L, overview.runtime().startedAtEpochMs());
            assertEquals(88L, overview.runtime().vmInitMillis());
        }

        @Test
        void measuresUptimeToTheLastSafepointWithoutATimer() {
            PerfCountersOverview overview = overview(g1());

            assertEquals(100 * SECOND, overview.runtime().uptimeNanos());
            assertEquals(UptimeBasis.SAFEPOINTS, overview.runtime().uptimeBasis());
            assertEquals(1_792_829_252_701L + 100_000L, overview.runtime().endedAtEpochMs());
        }

        @Test
        void prefersTheTimerAnOlderJvmWrites() {
            Map<String, Object> counters = g1();
            counters.put("sun.os.hrt.ticks", 120 * SECOND);

            PerfCountersOverview overview = overview(counters);

            assertEquals(120 * SECOND, overview.runtime().uptimeNanos());
            assertEquals(UptimeBasis.TIMER, overview.runtime().uptimeBasis());
        }
    }

    @Nested
    class Gc {

        /** G1 says one collector in its policy counter and writes three. */
        @Test
        void findsEveryCollectorTheJvmWrote() {
            List<Collector> collectors = overview(g1()).gc().collectors();

            assertEquals(List.of("G1 young collection pauses", "G1 full collection pauses", "G1 concurrent cycle pauses"),
                    collectors.stream().map(Collector::name).toList());
        }

        @Test
        void totalsTheCollectorsAndTheirShareOfTheUptime() {
            PerfCountersOverview overview = overview(g1());

            assertEquals(8L, overview.gc().invocations());
            assertEquals(4 * SECOND, overview.gc().timeNanos());
            assertEquals(0.04, overview.gc().shareOfUptime(), 1e-9);
        }
    }

    @Nested
    class Memory {

        @Test
        void addsUpTheSpacesOfEachGeneration() {
            List<Generation> generations = overview(g1()).memory().generations();

            assertEquals(List.of(
                            new Generation("young", 5_000_000L, 58_720_256L, 268_435_456L),
                            new Generation("old", 107_906_432L, 184_549_376L, 268_435_456L)),
                    generations);
            assertEquals(9_680_800L, overview(g1()).memory().metaspace().usedBytes());
            assertNull(overview(g1()).memory().metaspace().maxCapacityBytes());
        }
    }

    /** Another collector, or a JVM started without some counters, leaves figures out rather than zero. */
    @Test
    void leavesOutWhatTheJvmDidNotWrite() {
        PerfCountersOverview overview = overview(Map.of("java.threads.live", 6L));

        assertEquals(6L, overview.threads().live());
        assertNull(overview.runtime().uptimeNanos());
        assertNull(overview.runtime().endedAtEpochMs());
        assertNull(overview.gc().invocations());
        assertNull(overview.gc().shareOfUptime());
        assertNull(overview.jvm().vmName());
        assertTrue(overview.memory().generations().isEmpty());
    }

    /** Counters read back from the profile's cache come as whatever number type JSON gave them. */
    @Test
    void readsCachedCountersOfAnyNumberType() {
        PerfCountersOverview overview = PerfCountersOverviewBuilder.build(PerfCounterValues.of(List.of(
                new PerfCounter("java.threads.live", 6),
                new PerfCounter("sun.gc.policy.name", "GarbageFirst"))));

        assertEquals(6L, overview.threads().live());
        assertEquals("GarbageFirst", overview.jvm().gcPolicy());
    }
}
