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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PerfCountersCatalogTest {

    private static final PerfCountersCatalog CATALOG = PerfCountersCatalog.parse("""
            java.threads.live|count|Live threads.
            sun.gc.generation.0.space.1.*|bytes|Survivor space.
            """);

    @Test
    void describesACounterByItsName() {
        PerfCounter counter = CATALOG.describe(Map.of("java.threads.live", 6L)).getFirst();

        assertEquals("count", counter.datatype());
        assertEquals("Live threads.", counter.description());
    }

    @Test
    void describesEveryCounterUnderAWildcardEntry() {
        PerfCounter counter = CATALOG.describe(Map.of("sun.gc.generation.0.space.1.used", 1L)).getFirst();

        assertEquals("bytes", counter.datatype());
    }

    @Test
    void keepsACounterItCannotDescribeAndSortsByName() {
        List<PerfCounter> counters = CATALOG.describe(Map.of("zzz.custom", 1L, "java.threads.live", 6L));

        assertEquals(List.of("java.threads.live", "zzz.custom"), counters.stream().map(PerfCounter::key).toList());
        assertNull(counters.get(1).description());
    }

    @Test
    void theBundledCatalogLoads() {
        PerfCounter counter = PerfCountersCatalog.load().describe(Map.of("sun.rt.safepoints", 1L)).getFirst();

        assertEquals("count", counter.datatype());
    }
}
