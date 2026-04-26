/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package pbouda.jeffrey.server.core.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pbouda.jeffrey.shared.common.Json;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Wires the server's Spring MVC web infrastructure: Jackson 3 message converter
 * and the generic exception resolver.
 */
@Configuration
public class WebInfrastructureConfig {

    @Bean
    public WebMvcConfigurer jeffreyWebMvcConfigurer(JacksonJsonHttpMessageConverter jacksonConverter) {
        return new WebMvcConfigurer() {
            @Override
            public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
                converters.add(0, jacksonConverter);
            }
        };
    }

    /**
     * Spring 7's native Jackson 3 ({@code tools.jackson}) converter, configured
     * with the shared {@link Json#mapper()} so custom serializers apply at the
     * HTTP boundary.
     */
    @Bean
    public JacksonJsonHttpMessageConverter jacksonJsonHttpMessageConverter() {
        return new JacksonJsonHttpMessageConverter((JsonMapper) Json.mapper());
    }

    @Bean
    public HandlerExceptionResolver jeffreyExceptionResolver() {
        return new JeffreyExceptionResolver();
    }
}
