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
import pbouda.jeffrey.frameir.tree.AllocationTreeBuilder;
import pbouda.jeffrey.frameir.tree.BlockingTreeBuilder;
import pbouda.jeffrey.frameir.tree.SimpleTreeBuilder;
import pbouda.jeffrey.jfrparser.api.EventProcessor;

import java.util.List;
import java.util.function.Supplier;

public abstract class EventProcessors {

    public static Supplier<EventProcessor<Frame>> cpuSamples(Config config) {
        return () -> {
            SimpleTreeBuilder treeBuilder = new SimpleTreeBuilder(config.threadMode(), config.parseLocations());
            return new AsyncProfilerCpuEventProcessor(config.eventType(), config.primaryTimeRange(), treeBuilder);
        };
    }

    public static Supplier<EventProcessor<Frame>> wallClockSamples(Config config) {
        return () -> {
            SimpleTreeBuilder treeBuilder = new SimpleTreeBuilder(config.threadMode(), config.parseLocations());
            return new WallClockEventProcessor(config.primaryTimeRange(), treeBuilder);
        };
    }

    public static Supplier<EventProcessor<Frame>> allocationSamples(Config config) {
        return config.eventType().isObjectAllocationSamples()
                ? allocationSamples(Type.objectAllocationSamples(), config)
                : allocationSamples(Type.tlabAllocationSamples(), config);
    }

    public static Supplier<EventProcessor<Frame>> allocationSamples(List<Type> types, Config config) {
        return () -> {
            AllocationTreeBuilder treeBuilder = new AllocationTreeBuilder(config.threadMode(), config.parseLocations());
            return new AllocationEventProcessor(types, config.primaryTimeRange(), treeBuilder);
        };
    }

    public static Supplier<EventProcessor<Frame>> blocking(Config config) {
        return () -> {
            BlockingTreeBuilder treeBuilder = new BlockingTreeBuilder(config.threadMode(), config.parseLocations());
            return new BlockingEventProcessor(config.eventType(), config.primaryTimeRange(), treeBuilder);
        };
    }

    public static Supplier<EventProcessor<Frame>> resolve(Config config) {
        if (config.eventType().isAllocationEvent()) {
            return allocationSamples(config);
        } else if (config.eventType().isBlockingEvent()) {
            return blocking(config);
        } else {
            return cpuSamples(config);
        }
    }
}
