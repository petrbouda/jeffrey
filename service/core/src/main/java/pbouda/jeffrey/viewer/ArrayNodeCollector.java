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

package pbouda.jeffrey.viewer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import pbouda.jeffrey.common.Collector;
import pbouda.jeffrey.common.Json;

import java.util.function.Supplier;

public class ArrayNodeCollector implements Collector<ArrayNode, ArrayNode> {

    @Override
    public Supplier<ArrayNode> empty() {
        return Json::createArray;
    }

    @Override
    public ArrayNode combiner(ArrayNode partial1, ArrayNode partial2) {
        partial1.addAll(partial2);
        return partial1;
    }

    @Override
    public ArrayNode finisher(ArrayNode combined) {
        return combined;
    }
}
