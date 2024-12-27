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

package pbouda.jeffrey.calculated.nativeleak.raw;

import jdk.jfr.consumer.RecordedEvent;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;
import pbouda.jeffrey.calculated.nativeleak.collector.NativeAllocations;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.frameir.processor.FilterableEventProcessor;
import pbouda.jeffrey.frameir.processor.filter.EventProcessorFilter;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class NativeLeaksEventProcessor extends FilterableEventProcessor<NativeAllocations> {

    private static final List<Type> TYPES = List.of(Type.MALLOC, Type.FREE);

    private static final String ADDRESS_FIELD_NAME = "address";

    private final MutableLongSet deallocations = new LongHashSet();
    private final MutableLongObjectMap<RecordedEvent> allocations = new LongObjectHashMap<>();

    public NativeLeaksEventProcessor(
            AbsoluteTimeRange absoluteTimeRange,
            Duration timeShift,
            EventProcessorFilter filter) {

        super(TYPES, absoluteTimeRange, timeShift, filter);
    }

    @Override
    public ProcessableEvents processableEvents() {
        return new ProcessableEvents(List.of(Type.MALLOC, Type.FREE));
    }

    @Override
    protected Result processEvent(RecordedEvent event, Instant eventTime) {
        long address = event.getLong(ADDRESS_FIELD_NAME);
        if (Type.MALLOC.sameAs(event.getEventType())) {
            boolean removed = deallocations.remove(address);
            if (!removed) {
                allocations.put(address, event);
            }
        } else {
            RecordedEvent removed = allocations.remove(address);
            if (removed == null) {
                deallocations.add(address);
            }
        }

        return Result.CONTINUE;
    }

    @Override
    public NativeAllocations get() {
        return new NativeAllocations(allocations, deallocations);
    }
}
