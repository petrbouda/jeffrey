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

package pbouda.jeffrey.shared.serde;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import pbouda.jeffrey.shared.model.time.RelativeTimeRange;

import java.io.IOException;

public class RelativeTimeRangeSerializer extends StdSerializer<RelativeTimeRange> {

    public RelativeTimeRangeSerializer() {
        super(RelativeTimeRange.class);
    }

    @Override
    public void serialize(RelativeTimeRange timeRange, JsonGenerator gen, SerializerProvider serializerProvider) throws IOException {
        gen.writeStartObject();
        gen.writeNumberField("start", timeRange.start().toMillis());
        gen.writeNumberField("end", timeRange.end().toMillis());
        gen.writeEndObject();
    }
}
