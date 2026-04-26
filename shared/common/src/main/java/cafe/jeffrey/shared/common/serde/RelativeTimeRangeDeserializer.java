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

package cafe.jeffrey.shared.common.serde;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

public class RelativeTimeRangeDeserializer extends StdDeserializer<RelativeTimeRange> {

    public RelativeTimeRangeDeserializer() {
        super(RelativeTimeRange.class);
    }

    @Override
    public RelativeTimeRange deserialize(JsonParser jp, DeserializationContext context) {
        JsonNode node = context.readTree(jp);
        if (node == null) {
            throw new NullPointerException("RelativeTimeRange is null");
        }
        return new RelativeTimeRange(node.get("start").asLong(), node.get("end").asLong());
    }
}
