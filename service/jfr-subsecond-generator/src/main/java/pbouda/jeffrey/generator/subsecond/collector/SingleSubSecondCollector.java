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
import pbouda.jeffrey.generator.subsecond.SingleResult;

import java.util.ArrayList;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class SingleSubSecondCollector implements Collector<SingleResult, SingleResult, JsonNode> {

    private long maxValue;

    @Override
    public Supplier<SingleResult> supplier() {
        return () -> new SingleResult(-1, new ArrayList<>());
    }

    @Override
    public BiConsumer<SingleResult, SingleResult> accumulator() {
        return (left, right) -> {
            left.columns().addAll(right.columns());
            this.maxValue = Math.max(this.maxValue, left.maxValue());
        };
    }

    @Override
    public BinaryOperator<SingleResult> combiner() {
        return (left, right) -> {
            left.columns().addAll(right.columns());
            this.maxValue = Math.max(this.maxValue, left.maxValue());
            return left;
        };
    }

    @Override
    public Function<SingleResult, JsonNode> finisher() {
        return SubSecondCollectorUtils.finisher(maxValue);
    }

    @Override
    public Set<Characteristics> characteristics() {
        return Set.of(Characteristics.UNORDERED);
    }
}
