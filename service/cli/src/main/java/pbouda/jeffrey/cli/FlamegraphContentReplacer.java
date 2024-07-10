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

package pbouda.jeffrey.cli;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.CompressionUtils;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.ResourceUtils;

import java.util.Base64;

public abstract class FlamegraphContentReplacer {

    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();

    private static final String FLAMEGRAPH_HTML = "flamegraph/flamegraph.html";

    private static final String FLAMEGRAPH_TOKEN = "{{REPLACE_FLAMEGRAPH}}";
    private static final String TIMESERIES_TOKEN = "{{REPLACE_TIMESERIES}}";
    private static final String GRAPH_TYPE_TOKEN = "{{REPLACE_GRAPH_TYPE}}";

    private static final String PRIMARY_GRAPH_TYPE = "PRIMARY";
    private static final String DIFFERENTIAL_GRAPH_TYPE = "DIFFERENTIAL";

    public static String flamegraphOnly(JsonNode flamegraph) {
        String compressedFlamegraph = compressAndEncode(flamegraph);

        String template = ResourceUtils.readFromClasspath(FLAMEGRAPH_HTML);
        return template.replace(FLAMEGRAPH_TOKEN, compressedFlamegraph);
    }

    public static String primaryFlamegraphWithTimeseries(JsonNode flamegraph, JsonNode timeseries) {
        return flamegraphWithTimeseries(PRIMARY_GRAPH_TYPE, flamegraph, timeseries);
    }

    public static String flamegraphWithTimeseries(String graphType, JsonNode flamegraph, JsonNode timeseries) {
        String compressedFlamegraph = compressAndEncode(flamegraph);
        String compressedTimeseries = compressAndEncode(timeseries);

        String template = ResourceUtils.readFromClasspath(FLAMEGRAPH_HTML);
        return template
                .replace(GRAPH_TYPE_TOKEN, graphType)
                .replace(FLAMEGRAPH_TOKEN, compressedFlamegraph)
                .replace(TIMESERIES_TOKEN, compressedTimeseries);
    }

    private static String compressAndEncode(JsonNode data) {
        byte[] compressed = CompressionUtils.compressGzip(Json.toByteArray(data));
        return BASE64_ENCODER.encodeToString(compressed);
    }
}
