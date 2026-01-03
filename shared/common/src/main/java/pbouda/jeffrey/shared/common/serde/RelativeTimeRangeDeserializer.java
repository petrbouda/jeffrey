/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.shared.common.serde;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import pbouda.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.io.IOException;

public class RelativeTimeRangeDeserializer extends StdDeserializer<RelativeTimeRange> {

    public RelativeTimeRangeDeserializer() {
        this(null);
    }

    public RelativeTimeRangeDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public RelativeTimeRange deserialize(JsonParser jp, DeserializationContext context) throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        if (node == null) {
            throw new NullPointerException("RelativeTimeRange is null");
        }
        return new RelativeTimeRange(node.get("start").asLong(), node.get("end").asLong());
    }
}
