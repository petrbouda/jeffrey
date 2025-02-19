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

package pbouda.jeffrey.frameir.processor;

import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.time.AbsoluteTimeRange;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.processor.filter.EventProcessorFilter;
import pbouda.jeffrey.frameir.processor.filter.EventProcessorFilters;
import pbouda.jeffrey.frameir.tree.AllocationTreeBuilder;
import pbouda.jeffrey.frameir.tree.BlockingTreeBuilder;
import pbouda.jeffrey.frameir.tree.SimpleTreeBuilder;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

public abstract class EventProcessors {

    public static Supplier<EventProcessor<Frame>> simple(Config config) {
        boolean threadMode = config.graphParameters().threadMode();
        boolean parseLocations = config.graphParameters().parseLocations();

        return simple(config, () -> new SimpleTreeBuilder(threadMode, parseLocations));
    }

    public static Supplier<EventProcessor<Frame>> simple(Config config, Supplier<SimpleTreeBuilder> treeBuilder) {
        return simple(config, Duration.ZERO, treeBuilder);
    }

    public static Supplier<EventProcessor<Frame>> simple(
            Config config, Duration timeShift, Supplier<SimpleTreeBuilder> treeBuilder) {

        return () -> {
            EventProcessorFilter filter = EventProcessorFilters.resolveFilters(config);
            AbsoluteTimeRange absoluteTimeRange =
                    config.timeRange().toAbsoluteTimeRange(config.primaryStartEnd().start());

            return new SimpleEventProcessor(
                    config.eventType(), absoluteTimeRange, timeShift, treeBuilder.get(), filter);
        };
    }

    public static Supplier<EventProcessor<Frame>> wallClockSamples(Config config) {
        boolean threadMode = config.graphParameters().threadMode();
        boolean parseLocations = config.graphParameters().parseLocations();

        return wallClockSamples(config, () -> new SimpleTreeBuilder(threadMode, parseLocations));
    }

    public static Supplier<EventProcessor<Frame>> wallClockSamples(
            Config config, Supplier<SimpleTreeBuilder> treeBuilder) {

        return wallClockSamples(config, Duration.ZERO, treeBuilder);
    }

    public static Supplier<EventProcessor<Frame>> wallClockSamples(
            Config config, Duration timeShift, Supplier<SimpleTreeBuilder> treeBuilder) {

        return () -> {
            AbsoluteTimeRange absoluteTimeRange =
                    config.timeRange().toAbsoluteTimeRange(config.primaryStartEnd().start());

            EventProcessorFilter filter = EventProcessorFilters.resolveFilters(config);
            return new WallClockEventProcessor(absoluteTimeRange, timeShift, treeBuilder.get(), filter);
        };
    }

    public static Supplier<EventProcessor<Frame>> allocationSamples(Config config) {
        boolean threadMode = config.graphParameters().threadMode();
        boolean parseLocations = config.graphParameters().parseLocations();

        return allocationSamples(config, () -> new AllocationTreeBuilder(threadMode, parseLocations));
    }

    public static Supplier<EventProcessor<Frame>> allocationSamples(
            Config config, Supplier<AllocationTreeBuilder> treeBuilder) {

        return allocationSamples(config, Duration.ZERO, treeBuilder);
    }

    public static Supplier<EventProcessor<Frame>> allocationSamples(
            Config config, Duration timeShift, Supplier<AllocationTreeBuilder> treeBuilder) {
        AbsoluteTimeRange absoluteTimeRange =
                config.timeRange().toAbsoluteTimeRange(config.primaryStartEnd().start());

        List<Type> types = config.eventType().resolveGroupedTypes();
        return () -> new AllocationEventProcessor(types, absoluteTimeRange, timeShift, treeBuilder.get());
    }

    public static Supplier<EventProcessor<Frame>> mallocSamples(Config config) {
        boolean threadMode = config.graphParameters().threadMode();
        boolean parseLocations = config.graphParameters().parseLocations();

        return mallocSamples(config, () -> new SimpleTreeBuilder(threadMode, parseLocations));
    }

    public static Supplier<EventProcessor<Frame>> mallocSamples(
            Config config, Supplier<SimpleTreeBuilder> treeBuilder) {

        return mallocSamples(config, Duration.ZERO, treeBuilder);
    }

    public static Supplier<EventProcessor<Frame>> mallocSamples(
            Config config, Duration timeShift, Supplier<SimpleTreeBuilder> treeBuilder) {

        return () -> {
            AbsoluteTimeRange absoluteTimeRange =
                    config.timeRange().toAbsoluteTimeRange(config.primaryStartEnd().start());

            EventProcessorFilter filter = EventProcessorFilters.resolveFilters(config);
            return new MallocEventProcessor(absoluteTimeRange, timeShift, treeBuilder.get(), filter);
        };
    }

    public static Supplier<EventProcessor<Frame>> blocking(Config config) {
        boolean threadMode = config.graphParameters().threadMode();
        boolean parseLocations = config.graphParameters().parseLocations();

        return blocking(config, () -> new BlockingTreeBuilder(threadMode, parseLocations));
    }

    public static Supplier<EventProcessor<Frame>> blocking(Config config, Supplier<BlockingTreeBuilder> treeBuilder) {
        return () -> {
            AbsoluteTimeRange absoluteTimeRange =
                    config.timeRange().toAbsoluteTimeRange(config.primaryStartEnd().start());

            EventProcessorFilter filter = EventProcessorFilters.resolveFilters(config);
            return new BlockingEventProcessor(config.eventType(), absoluteTimeRange, treeBuilder.get(), filter);
        };
    }

    public static Supplier<EventProcessor<Frame>> nativeLeaks(Config config) {
        boolean threadMode = config.graphParameters().threadMode();
        boolean parseLocations = config.graphParameters().parseLocations();

        return nativeLeaks(config, () -> new SimpleTreeBuilder(threadMode, parseLocations));
    }

    public static Supplier<EventProcessor<Frame>> nativeLeaks(
            Config config, Supplier<SimpleTreeBuilder> treeBuilder) {

        return nativeLeaks(config, Duration.ZERO, treeBuilder);
    }

    public static Supplier<EventProcessor<Frame>> nativeLeaks(
            Config config, Duration timeShift, Supplier<SimpleTreeBuilder> treeBuilder) {

        return () -> {
            AbsoluteTimeRange absoluteTimeRange =
                    config.timeRange().toAbsoluteTimeRange(config.primaryStartEnd().start());

            EventProcessorFilter filter = EventProcessorFilters.resolveFilters(config);
            return new MallocEventProcessor(absoluteTimeRange, timeShift, treeBuilder.get(), filter);
        };
    }

    public static Supplier<EventProcessor<Frame>> resolve(Config config) {
        if (config.eventType().isAllocationEvent()) {
            return allocationSamples(config);
        } else if (config.eventType().isBlockingEvent()) {
            return blocking(config);
        } else {
            return simple(config);
        }
    }
}
