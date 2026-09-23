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

package cafe.jeffrey.microscope.core.configuration;

import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.microscope.model.serde.RelativeTimeRangeDeserializer;
import cafe.jeffrey.microscope.model.serde.RelativeTimeRangeSerializer;
import cafe.jeffrey.microscope.model.serde.TypeDeserializer;
import cafe.jeffrey.microscope.model.serde.TypeSerializer;

@Configuration
public class JacksonConfiguration {

    @Bean
    public JacksonModule customSerializer() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(new TypeSerializer());
        module.addSerializer(new RelativeTimeRangeSerializer());
        module.addDeserializer(Type.class, new TypeDeserializer());
        module.addDeserializer(RelativeTimeRange.class, new RelativeTimeRangeDeserializer());
        return module;
    }
}
