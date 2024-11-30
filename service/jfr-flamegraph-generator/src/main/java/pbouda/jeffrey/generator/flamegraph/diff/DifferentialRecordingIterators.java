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

package pbouda.jeffrey.generator.flamegraph.diff;

import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.Schedulers;
import pbouda.jeffrey.frameir.DiffFrame;
import pbouda.jeffrey.frameir.DiffTreeGenerator;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.processor.EventProcessors;
import pbouda.jeffrey.frameir.tree.AllocationTreeBuilder;
import pbouda.jeffrey.frameir.tree.SimpleTreeBuilder;
import pbouda.jeffrey.generator.flamegraph.collector.FrameCollectorFactories;
import pbouda.jeffrey.jfrparser.api.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public abstract class DifferentialRecordingIterators {

    public static DiffFrame allocation(Config config) {
        return generate(config,
                EventProcessors.allocationSamples(config, allocTreeBuilder()),
                EventProcessors.allocationSamples(config, config.timeShift(), allocTreeBuilder()));
    }

    public static DiffFrame wallClock(Config config) {
        return generate(
                config,
                EventProcessors.wallClockSamples(config, simpleTreeBuilder()),
                EventProcessors.wallClockSamples(config, config.timeShift(), simpleTreeBuilder()));
    }

    public static DiffFrame simple(Config config) {
        return generate(config,
                EventProcessors.simple(config, simpleTreeBuilder()),
                EventProcessors.simple(config, config.timeShift(), simpleTreeBuilder()));
    }

    private static DiffFrame generate(
            Config config,
            Supplier<? extends EventProcessor<Frame>> primarySupplier,
            Supplier<? extends EventProcessor<Frame>> secondarySupplier) {

        CompletableFuture<Frame> primaryFuture = CompletableFuture.supplyAsync(() -> {
            return JdkRecordingIterators.automaticAndCollect(
                    config.primaryRecordings(), primarySupplier, FrameCollectorFactories.frame());
        }, Schedulers.parallel());

        CompletableFuture<Frame> secondaryFuture = CompletableFuture.supplyAsync(() -> {
            return JdkRecordingIterators.automaticAndCollect(
                    config.secondaryRecordings(), secondarySupplier, FrameCollectorFactories.frame());
        }, Schedulers.parallel());

        CompletableFuture.allOf(primaryFuture, secondaryFuture).join();

        return new DiffTreeGenerator(primaryFuture.join(), secondaryFuture.join())
                .generate();
    }

    private static AllocationTreeBuilder allocTreeBuilder() {
        return new AllocationTreeBuilder(true, false, false, null);
    }

    private static SimpleTreeBuilder simpleTreeBuilder() {
        return new SimpleTreeBuilder(true, false, false);
    }
}
