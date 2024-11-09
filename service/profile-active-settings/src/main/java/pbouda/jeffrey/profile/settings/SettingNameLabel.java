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

package pbouda.jeffrey.profile.settings;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import pbouda.jeffrey.common.Json;

import java.io.IOException;

@JsonSerialize(keyUsing = SettingNameLabel.SettingNameLabelSerializer.class)
@JsonDeserialize(keyUsing = SettingNameLabel.SettingNameLabelDeserializer.class)
public record SettingNameLabel(String name, String label) {

    public static class SettingNameLabelDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(String key, final DeserializationContext ignored) {
            return Json.read(key, SettingNameLabel.class);
        }

    }

    public static class SettingNameLabelSerializer extends JsonSerializer<SettingNameLabel> {
        @Override
        public void serialize(SettingNameLabel value, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            gen.writeFieldName(Json.toString(value));
        }
    }
}
