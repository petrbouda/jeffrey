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

package pbouda.jeffrey.generator.timeseries.collector;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.generator.timeseries.SearchMaps;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class SearchableTimeseriesCollector implements Collector<SearchMaps, SearchMaps, ArrayNode> {

    @Override
    public Supplier<SearchMaps> supplier() {
        return () -> new SearchMaps(new LongLongHashMap(), new LongLongHashMap());
    }

    @Override
    public BiConsumer<SearchMaps, SearchMaps> accumulator() {
        return (left, right) -> {
            right.values().forEachKeyValue(left.values()::addToValue);
            right.matchedValues().forEachKeyValue(left.matchedValues()::addToValue);
        };
    }

    @Override
    public BinaryOperator<SearchMaps> combiner() {
        return (left, right) -> {
            if (isFirstBigger(left, right)) {
                right.values().forEachKeyValue(left.values()::addToValue);
                right.matchedValues().forEachKeyValue(left.matchedValues()::addToValue);
                return left;
            } else {
                left.values().forEachKeyValue(right.values()::addToValue);
                left.matchedValues().forEachKeyValue(right.matchedValues()::addToValue);
                return right;
            }
        };
    }

    @Override
    public Function<SearchMaps, ArrayNode> finisher() {
        return TimeseriesCollectorUtils::buildSearchableTimeseries;
    }

    @Override
    public Set<Characteristics> characteristics() {
        return Set.of(Characteristics.UNORDERED);
    }

    private static boolean isFirstBigger(SearchMaps first, SearchMaps second) {
        return first.values().size() + first.matchedValues().size()
                > second.values().size() + second.matchedValues().size();
    }
}
