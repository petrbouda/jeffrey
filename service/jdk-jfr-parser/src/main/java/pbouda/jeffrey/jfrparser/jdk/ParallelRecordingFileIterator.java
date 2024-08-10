/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * Uses ForkJoinPool.commonPool() intentionally to avoid creating a new thread pool
 * and to leverage the parallelism of the common pool (number of threads == number of processors)
 *
 * @param <PARTIAL> result of the single recording file
 * @param <RESULT>  collected result of all recording files
 */
public class ParallelRecordingFileIterator<PARTIAL, RESULT> implements RecordingFileIterator<PARTIAL, RESULT> {

    private final List<Path> recordings;
    private final Supplier<? extends EventProcessor<PARTIAL>> processorSupplier;

    public ParallelRecordingFileIterator(
            List<Path> recordings,
            Supplier<? extends EventProcessor<PARTIAL>> processorSupplier) {

        this.recordings = recordings;
        this.processorSupplier = processorSupplier;
    }

    public RESULT collect(Collector<PARTIAL, ?, RESULT> collector) {
        List<CompletableFuture<PARTIAL>> futures = recordings.stream()
                .map(this::asyncExecution)
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .join();

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(collector);
    }

    private CompletableFuture<PARTIAL> asyncExecution(Path recording) {
        return CompletableFuture.supplyAsync(() -> {
            return new SingleRecordingFileIterator<PARTIAL, PARTIAL>(recording, processorSupplier.get())
                    .collect(new IdentityCollector());
        });
    }

    /**
     * {@link SingleRecordingFileIterator} needs to provide its partial result directly without any aggregation.
     */
    private class IdentityCollector implements Collector<PARTIAL, AtomicReference<PARTIAL>, PARTIAL> {

        @Override
        public Supplier<AtomicReference<PARTIAL>> supplier() {
            return AtomicReference::new;
        }

        @Override
        public BiConsumer<AtomicReference<PARTIAL>, PARTIAL> accumulator() {
            return AtomicReference::set;
        }

        @Override
        public BinaryOperator<AtomicReference<PARTIAL>> combiner() {
            return (first, second) -> first;
        }

        @Override
        public Function<AtomicReference<PARTIAL>, PARTIAL> finisher() {
            return AtomicReference::get;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Set.of(Characteristics.UNORDERED);
        }
    }
}
