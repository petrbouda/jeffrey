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

package pbouda.jeffrey.platform.configuration;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.shared.model.Type;
import pbouda.jeffrey.shared.model.time.RelativeTimeRange;
import pbouda.jeffrey.shared.serde.RelativeTimeRangeDeserializer;
import pbouda.jeffrey.shared.serde.RelativeTimeRangeSerializer;
import pbouda.jeffrey.shared.serde.TypeDeserializer;
import pbouda.jeffrey.shared.serde.TypeSerializer;

@Configuration
public class JacksonConfiguration {

    @Bean
    public com.fasterxml.jackson.databind.Module customSerializer() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(new TypeSerializer());
        module.addSerializer(new RelativeTimeRangeSerializer());
        module.addDeserializer(Type.class, new TypeDeserializer());
        module.addDeserializer(RelativeTimeRange.class, new RelativeTimeRangeDeserializer());
        return module;
    }
}
