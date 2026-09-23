/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.model.serde;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;

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
