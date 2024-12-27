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

package pbouda.jeffrey.timeseries.collector;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.common.Collector;
import pbouda.jeffrey.timeseries.TimeseriesMaps;

import java.util.function.Supplier;

public class SplitTimeseriesCollector implements Collector<TimeseriesMaps, ArrayNode> {

    @Override
    public Supplier<TimeseriesMaps> empty() {
        return () -> new TimeseriesMaps(new LongLongHashMap(), new LongLongHashMap());
    }

    @Override
    public TimeseriesMaps combiner(TimeseriesMaps left, TimeseriesMaps right) {
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
    public ArrayNode finisher(TimeseriesMaps combined) {
        return TimeseriesCollectorUtils.buildSplittableTimeseries(combined);
    }

    private static boolean isFirstBigger(TimeseriesMaps first, TimeseriesMaps second) {
        return first.values().size() + first.matchedValues().size()
                > second.values().size() + second.matchedValues().size();
    }
}
