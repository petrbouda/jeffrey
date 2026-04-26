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

package pbouda.jeffrey.local.core.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Wires Jeffrey's Spring MVC web infrastructure: the Jackson 3 message
 * converter, the exception resolver, and the request-logging / JFR filters.
 */
@Configuration
public class WebInfrastructureConfig {

    @Bean
    public WebMvcConfigurer jeffreyWebMvcConfigurer(JacksonJson3HttpMessageConverter jacksonConverter) {
        return new WebMvcConfigurer() {
            @Override
            public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
                converters.add(0, jacksonConverter);
            }
        };
    }

    @Bean
    public JacksonJson3HttpMessageConverter jacksonJson3HttpMessageConverter() {
        return new JacksonJson3HttpMessageConverter();
    }

    @Bean
    public HandlerExceptionResolver jeffreyExceptionResolver() {
        return new JeffreyExceptionResolver();
    }

    @Bean
    public FilterRegistrationBean<JeffreyRequestLoggingFilter> jeffreyRequestLoggingFilter() {
        FilterRegistrationBean<JeffreyRequestLoggingFilter> bean = new FilterRegistrationBean<>(new JeffreyRequestLoggingFilter());
        bean.addUrlPatterns("/api/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<JeffreyJfrHttpEventFilter> jeffreyJfrHttpEventFilter() {
        FilterRegistrationBean<JeffreyJfrHttpEventFilter> bean = new FilterRegistrationBean<>(new JeffreyJfrHttpEventFilter());
        bean.addUrlPatterns("/api/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }
}
