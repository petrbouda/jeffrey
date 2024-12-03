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

import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.processor.filter.EventProcessorFilter;
import pbouda.jeffrey.frameir.processor.filter.EventProcessorFilters;
import pbouda.jeffrey.frameir.tree.AllocationTreeBuilder;
import pbouda.jeffrey.frameir.tree.BlockingTreeBuilder;
import pbouda.jeffrey.frameir.tree.SimpleTreeBuilder;
import pbouda.jeffrey.jfrparser.api.EventProcessor;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

public abstract class EventProcessors {

    public static Supplier<EventProcessor<Frame>> simple(Config config) {
        return simple(config, () -> new SimpleTreeBuilder(config.threadMode(), config.parseLocations()));
    }

    public static Supplier<EventProcessor<Frame>> simple(Config config, Supplier<SimpleTreeBuilder> treeBuilder) {
        return simple(config, Duration.ZERO, treeBuilder);
    }

    public static Supplier<EventProcessor<Frame>> simple(
            Config config, Duration timeShift, Supplier<SimpleTreeBuilder> treeBuilder) {

        return () -> {
            EventProcessorFilter filter = EventProcessorFilters.resolveFilters(config);
            return new SimpleEventProcessor(
                    config.eventType(), config.timeRange(), timeShift, treeBuilder.get(), filter);
        };
    }

    public static Supplier<EventProcessor<Frame>> wallClockSamples(Config config) {
        return wallClockSamples(config, () -> new SimpleTreeBuilder(config.threadMode(), config.parseLocations()));
    }

    public static Supplier<EventProcessor<Frame>> wallClockSamples(
            Config config, Supplier<SimpleTreeBuilder> treeBuilder) {

        return wallClockSamples(config, Duration.ZERO, treeBuilder);
    }

    public static Supplier<EventProcessor<Frame>> wallClockSamples(
            Config config, Duration timeShift, Supplier<SimpleTreeBuilder> treeBuilder) {

        return () -> {
            EventProcessorFilter filter = EventProcessorFilters.resolveFilters(config);
            return new WallClockEventProcessor(config.timeRange(), timeShift, treeBuilder.get(), filter);
        };
    }

    public static Supplier<EventProcessor<Frame>> allocationSamples(Config config) {
        return allocationSamples(config, () -> new AllocationTreeBuilder(config.threadMode(), config.parseLocations()));
    }

    public static Supplier<EventProcessor<Frame>> allocationSamples(
            Config config, Supplier<AllocationTreeBuilder> treeBuilder) {

        return allocationSamples(config, Duration.ZERO, treeBuilder);
    }

    public static Supplier<EventProcessor<Frame>> allocationSamples(
            Config config, Duration timeShift, Supplier<AllocationTreeBuilder> treeBuilder) {

        List<Type> types = config.eventType().resolveAllocationTypes();
        return () -> new AllocationEventProcessor(types, config.timeRange(), timeShift, treeBuilder.get());
    }

    public static Supplier<EventProcessor<Frame>> blocking(Config config) {
        return blocking(config, () -> new BlockingTreeBuilder(config.threadMode(), config.parseLocations()));
    }

    public static Supplier<EventProcessor<Frame>> blocking(Config config, Supplier<BlockingTreeBuilder> treeBuilder) {
        return () -> {
            EventProcessorFilter filter = EventProcessorFilters.resolveFilters(config);
            return new BlockingEventProcessor(config.eventType(), config.timeRange(), treeBuilder.get(), filter);
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
