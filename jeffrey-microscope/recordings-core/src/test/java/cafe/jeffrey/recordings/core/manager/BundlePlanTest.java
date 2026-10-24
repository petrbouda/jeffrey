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

package cafe.jeffrey.recordings.core.manager;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BundlePlanTest {

    private static final Path DIR = Path.of("/downloads/session-1");

    @Test
    void everyChunkIsOneItemNamedAfterTheSession() {
        BundlePlan plan = BundlePlan.of("checkout",
                List.of(DIR.resolve("chunk-1.jfr.lz4"), DIR.resolve("chunk-2.jfr.lz4")), List.of());

        assertEquals(1, plan.items().size());
        assertEquals("checkout", plan.items().getFirst().name());
        assertEquals(2, plan.items().getFirst().files().size());
    }

    @Test
    void eachHeapDumpIsAnItemOfItsOwnAfterTheRecording() {
        BundlePlan plan = BundlePlan.of("checkout", List.of(DIR.resolve("chunk-1.jfr")),
                List.of(DIR.resolve("heap-1.hprof"), DIR.resolve("heap-2.hprof.gz")));

        assertEquals(List.of("checkout", "heap-1.hprof", "heap-2.hprof.gz"),
                plan.items().stream().map(BundlePlan.Item::name).toList());
        assertEquals(List.of(), plan.files());
    }

    @Test
    void aPerfCountersSnapshotIsAnItemOfItsOwn() {
        BundlePlan plan = BundlePlan.of("checkout", List.of(DIR.resolve("chunk-1.jfr")),
                List.of(DIR.resolve("perf-counters.hsperfdata")));

        assertEquals(List.of("checkout", "perf-counters.hsperfdata"),
                plan.items().stream().map(BundlePlan.Item::name).toList());
        assertEquals(List.of(), plan.files());
    }

    @Test
    void logsAndCrashFilesStayFilesOfTheBundle() {
        List<Path> artifacts = List.of(
                DIR.resolve("gc.jvm-log"), DIR.resolve("app.log"), DIR.resolve("hs-jvm-err.log"));

        BundlePlan plan = BundlePlan.of("checkout", List.of(DIR.resolve("chunk-1.jfr")), artifacts);

        assertEquals(1, plan.items().size());
        assertEquals(artifacts, plan.files());
    }

    @Test
    void refusesADownloadWithoutRecordingFiles() {
        assertThrows(IllegalArgumentException.class,
                () -> BundlePlan.of("checkout", List.of(), List.of(DIR.resolve("heap.hprof"))));
    }
}
