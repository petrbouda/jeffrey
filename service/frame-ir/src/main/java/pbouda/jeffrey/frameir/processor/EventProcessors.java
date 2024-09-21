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

import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.jfrparser.api.EventProcessor;

import java.util.List;
import java.util.function.Supplier;

public abstract class EventProcessors {

    private static final List<Type> ALLOC_TLAB_TYPES = List.of(
            Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
            Type.OBJECT_ALLOCATION_OUTSIDE_TLAB);

    private static final List<Type> ALLOC_SAMPLE_TYPES = List.of(Type.OBJECT_ALLOCATION_SAMPLE);

    public static Supplier<EventProcessor<Frame>> simple(Config config) {
        return () -> new SimpleEventProcessor(config.eventType(), config.primaryTimeRange(), config.threadMode());
    }

    public static Supplier<EventProcessor<Frame>> executionSamples(Config config) {
        return () -> new SimpleEventProcessor(Type.EXECUTION_SAMPLE, config.primaryTimeRange(), config.threadMode());
    }

    public static Supplier<EventProcessor<Frame>> allocationSamples(Config config) {
        if (config.eventType().isObjectAllocationSamples()) {
            return allocationSamples(Type.objectAllocationSamples(), config.primaryTimeRange(), config.threadMode());
        } else {
            return allocationSamples(Type.tlabAllocationSamples(), config.primaryTimeRange(), config.threadMode());
        }
    }

    public static Supplier<EventProcessor<Frame>> allocationSamples(
            List<Type> types, AbsoluteTimeRange timeRange, boolean threadMode) {

        return () -> new AllocationEventProcessor(types, timeRange, threadMode);
    }

    public static Supplier<EventProcessor<Frame>> blocking(Config config) {
        return () -> new BlockingEventProcessor(config.eventType(), config.primaryTimeRange(), config.threadMode());
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
