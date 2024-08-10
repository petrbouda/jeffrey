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

package pbouda.jeffrey.jfr.event;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class AllEventsCollector implements Collector<
        MutableMap<String, EventTypeCollector>, MutableMap<String, EventTypeCollector>, List<EventSummary>> {


    @Override
    public Supplier<MutableMap<String, EventTypeCollector>> supplier() {
        return Maps.mutable::empty;
    }

    @Override
    public BiConsumer<MutableMap<String, EventTypeCollector>, MutableMap<String, EventTypeCollector>> accumulator() {
        return (left, right) -> {
            right.forEachKeyValue((key, value) -> {
                left.compute(key, (k, v) -> v == null ? value : v.merge(value));
            });
        };
    }

    @Override
    public BinaryOperator<MutableMap<String, EventTypeCollector>> combiner() {
        return (left, right) -> {
            right.forEachKeyValue((key, value) -> {
                left.compute(key, (k, v) -> v == null ? value : v.merge(value));
            });
            return left;
        };
    }

    @Override
    public Function<MutableMap<String, EventTypeCollector>, List<EventSummary>> finisher() {
        return collectors -> collectors.values().stream()
                .map(EventTypeCollector::buildSummary)
                .toList();
    }

    @Override
    public Set<Characteristics> characteristics() {
        return Set.of(Characteristics.UNORDERED);
    }
}
