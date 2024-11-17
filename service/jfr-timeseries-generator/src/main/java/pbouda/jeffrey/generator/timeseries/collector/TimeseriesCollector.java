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
import pbouda.jeffrey.common.Collector;
import pbouda.jeffrey.common.ProfilingStartEnd;

import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

public class TimeseriesCollector implements Collector<LongLongHashMap, ArrayNode> {

    private final ProfilingStartEnd profilingStartEnd;

    public TimeseriesCollector() {
        this(null);
    }

    public TimeseriesCollector(ProfilingStartEnd profilingStartEnd) {
        this.profilingStartEnd = profilingStartEnd;
    }

    @Override
    public Supplier<LongLongHashMap> empty() {
        return LongLongHashMap::new;
    }

    @Override
    public LongLongHashMap combiner(LongLongHashMap partial1, LongLongHashMap partial2) {
        LongLongHashMap combined = new LongLongHashMap(partial1);
        partial2.forEachKeyValue(combined::addToValue);
        return combined;
    }

    @Override
    public ArrayNode finisher(LongLongHashMap combined) {
        if (profilingStartEnd == null) {
            return TimeseriesCollectorUtils.buildTimeseries(combined);
        } else {
            long start = profilingStartEnd.start()
                    .truncatedTo(ChronoUnit.SECONDS)
                    .getEpochSecond();
            long end = profilingStartEnd.end()
                    .truncatedTo(ChronoUnit.SECONDS)
                    .getEpochSecond();
            for (long i = start; i <= end; i++) {
                combined.getIfAbsentPut(i * 1000, 0);
            }

            return TimeseriesCollectorUtils.buildTimeseries(combined);
        }
    }
}
