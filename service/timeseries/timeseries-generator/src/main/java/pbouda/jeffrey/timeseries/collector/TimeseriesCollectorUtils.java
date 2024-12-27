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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.primitive.LongLongPair;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.timeseries.TimeseriesMaps;

import java.util.Comparator;

public abstract class TimeseriesCollectorUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ArrayNode buildTimeseries(LongLongHashMap values) {
        ArrayNode result = MAPPER.createArrayNode();
        MutableList<LongLongPair> sorted = values.keyValuesView()
                .toSortedList(Comparator.comparing(LongLongPair::getOne));
        for (LongLongPair pair : sorted) {
            ArrayNode timeSamples = MAPPER.createArrayNode();
            timeSamples.add(pair.getOne());
            timeSamples.add(pair.getTwo());
            result.add(timeSamples);
        }
        return result;
    }

    public static ArrayNode buildSplittableTimeseries(TimeseriesMaps maps) {
        return MAPPER.createArrayNode()
                .add(buildTimeseries(maps.values()))
                .add(buildTimeseries(maps.matchedValues()));
    }
}
