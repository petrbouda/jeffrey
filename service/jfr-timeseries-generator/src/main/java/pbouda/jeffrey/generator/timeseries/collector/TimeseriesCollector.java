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
import pbouda.jeffrey.jfrparser.jdk.Collector;

import java.util.function.Supplier;

public class TimeseriesCollector implements Collector<LongLongHashMap, ArrayNode> {

    @Override
    public Supplier<LongLongHashMap> empty() {
        return LongLongHashMap::new;
    }

    @Override
    public LongLongHashMap combiner(LongLongHashMap partial1, LongLongHashMap partial2) {
        if (partial1.size() > partial2.size()) {
            partial2.forEachKeyValue(partial1::addToValue);
            return partial1;
        } else {
            partial1.forEachKeyValue(partial2::addToValue);
            return partial2;
        }
    }

    @Override
    public ArrayNode finisher(LongLongHashMap combined) {
        return TimeseriesCollectorUtils.buildTimeseries(combined);
    }
}
