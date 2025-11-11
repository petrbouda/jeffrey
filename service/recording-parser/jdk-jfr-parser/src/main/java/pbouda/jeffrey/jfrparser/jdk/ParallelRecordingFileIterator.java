/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.jfrparser.jdk;

import pbouda.jeffrey.common.Schedulers;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Uses ForkJoinPool.commonPool() intentionally to avoid creating a new thread pool
 * and to leverage the parallelism of the common pool (number of threads == number of processors)
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
                .map(future -> asyncExecution(future, collector))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .join();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private CompletableFuture<PARTIAL> asyncExecution(
            Path recording,
            Collector<PARTIAL, ?> collector) {

        return CompletableFuture.supplyAsync(
                () -> singleFileIterator.apply(recording).partialCollect(collector), Schedulers.sharedParallel());
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
