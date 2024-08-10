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
import pbouda.jeffrey.generator.timeseries.SearchMaps;
import pbouda.jeffrey.jfrparser.jdk.CollectorFactory;

import java.util.stream.Collector;

public class SearchableTimeseriesCollectorFactory implements CollectorFactory<SearchMaps, ArrayNode> {

    @Override
    public Collector<SearchMaps, ?, ArrayNode> single() {
        return new SingleSearchableTimeseriesCollector();
    }

    @Override
    public Collector<SearchMaps, ?, ArrayNode> merging() {
        return new SearchableTimeseriesCollector();
    }
}
