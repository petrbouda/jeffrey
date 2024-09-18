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
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.frameir.DiffFrame;
import pbouda.jeffrey.frameir.DiffTreeGenerator;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.processor.AllocationEventProcessor;
import pbouda.jeffrey.frameir.processor.SimpleEventProcessor;
import pbouda.jeffrey.frameir.tree.AllocationTreeBuilder;
import pbouda.jeffrey.frameir.tree.SimpleTreeBuilder;
import pbouda.jeffrey.generator.flamegraph.collector.FrameCollectorFactories;
import pbouda.jeffrey.jfrparser.api.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public abstract class DifferentialRecordingIterators {

    private static final List<Type> ALLOC_TLAB_TYPES = List.of(
            Type.OBJECT_ALLOCATION_IN_NEW_TLAB, Type.OBJECT_ALLOCATION_OUTSIDE_TLAB);

    private static final List<Type> ALLOC_SAMPLE_TYPES = List.of(Type.OBJECT_ALLOCATION_SAMPLE);

    public static DiffFrame allocation(Config config) {
        List<Type> allocationType = resolveAllocationType(config);
        return generate(config,
                () -> new AllocationEventProcessor(allocationType, config.primaryTimeRange(), allocTreeBuilder()),
                () -> new AllocationEventProcessor(allocationType, config.secondaryTimeRange(), allocTreeBuilder())
        );
    }

    public static DiffFrame simple(Config config) {
        List<Type> types = List.of(config.eventType());
        return generate(config,
                () -> new SimpleEventProcessor(types, config.primaryTimeRange(), simpleTreeBuilder()),
                () -> new SimpleEventProcessor(types, config.secondaryTimeRange(), simpleTreeBuilder())
        );
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

    private static List<Type> resolveAllocationType(Config config) {
        if (config.eventType().isAllocationTlab()) {
            return ALLOC_TLAB_TYPES;
        } else if (config.eventType().isAllocationSamples()) {
            return ALLOC_SAMPLE_TYPES;
        } else {
            throw new IllegalArgumentException("Unsupported allocation type: " + config.eventType());
        }
    }

    private static AllocationTreeBuilder allocTreeBuilder() {
        return new AllocationTreeBuilder(true, false, null);
    }

    private static SimpleTreeBuilder simpleTreeBuilder() {
        return new SimpleTreeBuilder(true, false);
    }
}
