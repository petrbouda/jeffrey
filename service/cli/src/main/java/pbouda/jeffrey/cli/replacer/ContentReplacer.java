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
import pbouda.jeffrey.common.CompressionUtils;
import pbouda.jeffrey.common.Json;

import java.util.Base64;

public abstract class ContentReplacer {

    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();

    public static final String USE_WEIGHT_TOKEN = "{{REPLACE_USE_WEIGHT}}";
    public static final String GRAPH_TYPE_TOKEN = "{{REPLACE_GRAPH_TYPE}}";
    public static final String EVENT_TYPE_TOKEN = "{{REPLACE_EVENT_TYPE}}";
    public static final String SEARCH_TOKEN = "{{REPLACE_SEARCH}}";
    public static final String WITH_TIMESERIES_TOKEN = "{{REPLACE_WITH_TIMESERIES}}";

    public static String enableUseWeight(String content) {
        return content.replace(USE_WEIGHT_TOKEN, "true");
    }

    public static String replaceSearch(String content, String value) {
        return content.replace(SEARCH_TOKEN, value);
    }

    public static String compressAndEncode(JsonNode data) {
        byte[] compressed = CompressionUtils.compressGzip(Json.toByteArray(data));
        return BASE64_ENCODER.encodeToString(compressed);
    }
}
