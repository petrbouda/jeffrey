/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.jfrparser.jdk;

import cafe.jeffrey.jfr.events.trace.Tracer;
import cafe.jeffrey.shared.common.Schedulers;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Iterates over multiple recording files in parallel on the shared bulk pool
 * ({@link Schedulers#sharedBulkParallel()}, number of threads == number of processors).
 * The bulk pool is intentionally separate from the interactive pool so a large
 * import cannot queue ahead of latency-sensitive requests (flamegraphs, timeseries).
 *
 * @param <PARTIAL> result of the single recording file
 * @param <RESULT>  collected result of all recording files
 */
public class ParallelRecordingFileIterator<PARTIAL, RESULT> implements RecordingFileIterator<PARTIAL, RESULT> {

    private final List<Path> recordings;
    private final Function<Path, RecordingFileIterator<PARTIAL, PARTIAL>> singleFileIterator;

    public ParallelRecordingFileIterator(
            List<Path> recordings,
            Function<Path, RecordingFileIterator<PARTIAL, PARTIAL>> singleFileIterator) {

        this.recordings = recordings;
        this.singleFileIterator = singleFileIterator;
    }

    @Override
    public RESULT collect(Collector<PARTIAL, RESULT> collector) {
        List<PARTIAL> partials = _iterate(collector);
        PARTIAL combined = partialCombination(partials, collector);
        return collector.finisher(combined);
    }

    @Override
    public PARTIAL partialCollect(Collector<PARTIAL, ?> collector) {
        List<PARTIAL> partials = _iterate(collector);
        return partialCombination(partials, collector);
    }

    private List<PARTIAL> _iterate(Collector<PARTIAL, ?> collector) {
        List<CompletableFuture<PARTIAL>> futures = recordings.stream()
                .map(recording -> asyncExecution(recording, collector))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .join();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private CompletableFuture<PARTIAL> asyncExecution(Path recording, Collector<PARTIAL, ?> collector) {
        // fork captures the enclosing span here, on the submitting thread: the workers run on a
        // shared pool, which ScopedValue does not reach, so without it each file would parse under
        // a trace of its own rather than under the parse that spawned it.
        return CompletableFuture.supplyAsync(
                Tracer.fork(JdkRecordingIterators.SPAN_CHUNK_PARSE,
                        () -> singleFileIterator.apply(recording).partialCollect(collector)),
                Schedulers.sharedBulkParallel());
    }

    private PARTIAL partialCombination(List<PARTIAL> partials, Collector<PARTIAL, ?> collector) {
        if (partials.isEmpty()) {
            return collector.empty().get();
        } else {
            PARTIAL combined = partials.getFirst();
            for (int i = 1; i < partials.size(); i++) {
                combined = collector.combiner(combined, partials.get(i));
            }
            return combined;
        }
    }
}
