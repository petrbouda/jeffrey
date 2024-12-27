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

package pbouda.jeffrey.calculated.nativeleak.collector;

import jdk.jfr.consumer.RecordedEvent;
import org.eclipse.collections.api.factory.primitive.LongObjectMaps;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import pbouda.jeffrey.common.Collector;

import java.util.List;
import java.util.function.Supplier;

public class NativeLeakCollector implements Collector<NativeAllocations, NativeLeaks> {

    @Override
    public Supplier<NativeAllocations> empty() {
        return NativeAllocations::new;
    }

    @Override
    public NativeAllocations combiner(NativeAllocations partial1, NativeAllocations partial2) {
        MutableLongObjectMap<RecordedEvent> allocations = LongObjectMaps.mutable.empty();
        allocations.putAll(partial1.allocations());
        allocations.putAll(partial2.allocations());

        MutableLongSet deallocations = LongSets.mutable.empty();
        deallocations.addAll(partial1.deallocations());
        deallocations.addAll(partial2.deallocations());

        return new NativeAllocations(allocations, deallocations);
    }

    @Override
    public NativeLeaks finisher(NativeAllocations combined) {
        combined.deallocations().forEach(combined.allocations()::remove);
        return new NativeLeaks(List.copyOf(combined.allocations().values()));
    }
}
