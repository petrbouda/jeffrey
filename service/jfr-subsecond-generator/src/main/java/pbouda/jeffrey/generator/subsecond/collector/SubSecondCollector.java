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

package pbouda.jeffrey.generator.subsecond.collector;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.Collector;
import pbouda.jeffrey.generator.subsecond.SecondColumn;
import pbouda.jeffrey.generator.subsecond.SingleResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SubSecondCollector implements Collector<SingleResult, JsonNode> {

    private long maxValue;

    private static long merge(List<SecondColumn> left, List<SecondColumn> right) {
        int size = Math.max(left.size(), right.size());

        long maxValue = -1;
        for (int i = 0; i < size; i++) {
            if (i < left.size() && i < right.size()) {
                // Both collections have the value for the given second
                left.get(i).merge(right.get(i));
            } else if (i < right.size()) {
                // Only the right collection has the value for the given second,
                // just add the given column to the left collection
                left.add(i, right.get(i));
            }

            maxValue = Math.max(maxValue, left.get(i).getMaxValue());
        }

        return maxValue;
    }

    @Override
    public Supplier<SingleResult> empty() {
        return () -> new SingleResult(-1, new ArrayList<>());
    }

    @Override
    public SingleResult combiner(SingleResult left, SingleResult right) {
        long maxValue = merge(left.columns(), right.columns());
        this.maxValue = Math.max(this.maxValue, maxValue);
        return left;
    }

    @Override
    public JsonNode finisher(SingleResult combined) {
        return SubSecondCollectorUtils.finisher(combined, Math.max(this.maxValue, combined.maxValue()));
    }
}
