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

package pbouda.jeffrey.cli.replacer;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.ResourceUtils;

public abstract class FlamegraphContentReplacer {

    private static final String HTML_SOURCE = "flamegraph/flamegraph.html";

    private static final String FLAMEGRAPH_TOKEN = "{{REPLACE_FLAMEGRAPH}}";
    private static final String TIMESERIES_TOKEN = "{{REPLACE_TIMESERIES}}";

    public static String flamegraphOnly(
            GraphType graphType, JsonNode flamegraph, String eventType) {

        String compressedFlamegraph = ContentReplacer.compressAndEncode(flamegraph);

        return ResourceUtils.readFromClasspath(HTML_SOURCE)
                .replace(ContentReplacer.EVENT_TYPE_TOKEN, eventType)
                .replace(ContentReplacer.GRAPH_TYPE_TOKEN, graphType.name())
                .replace(ContentReplacer.WITH_TIMESERIES_TOKEN, "false")
                .replace(FLAMEGRAPH_TOKEN, compressedFlamegraph);
    }

    public static String withTimeseries(
            GraphType graphType, JsonNode flamegraph, JsonNode timeseries, String eventType) {

        String compressedFlamegraph = ContentReplacer.compressAndEncode(flamegraph);
        String compressedTimeseries = ContentReplacer.compressAndEncode(timeseries);

        return ResourceUtils.readFromClasspath(HTML_SOURCE)
                .replace(ContentReplacer.EVENT_TYPE_TOKEN, eventType)
                .replace(ContentReplacer.GRAPH_TYPE_TOKEN, graphType.name())
                .replace(ContentReplacer.WITH_TIMESERIES_TOKEN, "true")
                .replace(FLAMEGRAPH_TOKEN, compressedFlamegraph)
                .replace(TIMESERIES_TOKEN, compressedTimeseries);
    }
}
