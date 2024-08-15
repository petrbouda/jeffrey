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
import pbouda.jeffrey.jfrparser.jdk.Collector;

import java.util.function.Supplier;

public class SearchableTimeseriesCollector implements Collector<SearchMaps, ArrayNode> {

    @Override
    public Supplier<SearchMaps> empty() {
        return () -> new SearchMaps(new LongLongHashMap(), new LongLongHashMap());
    }

    @Override
    public SearchMaps combiner(SearchMaps left, SearchMaps right) {
        if (isFirstBigger(left, right)) {
            right.values().forEachKeyValue(left.values()::addToValue);
            right.matchedValues().forEachKeyValue(left.matchedValues()::addToValue);
            return left;
        } else {
            left.values().forEachKeyValue(right.values()::addToValue);
            left.matchedValues().forEachKeyValue(right.matchedValues()::addToValue);
            return right;
        }
    }

    @Override
    public ArrayNode finisher(SearchMaps combined) {
        return TimeseriesCollectorUtils.buildSearchableTimeseries(combined);
    }

    private static boolean isFirstBigger(SearchMaps first, SearchMaps second) {
        return first.values().size() + first.matchedValues().size()
                > second.values().size() + second.matchedValues().size();
    }
}
