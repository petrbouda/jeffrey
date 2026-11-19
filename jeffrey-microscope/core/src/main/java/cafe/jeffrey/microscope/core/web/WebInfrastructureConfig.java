/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.core.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Wires Jeffrey's Spring MVC web infrastructure: the Jackson 3 message
 * converter, the exception handler, and the request-logging filter.
 * <p>
 * The JFR HTTP filter is no longer registered here: it ships in
 * {@code jeffrey-tracing-spring-boot-starter}, which registers it from
 * {@code jeffrey.tracing.*}. Jeffrey consumes the same artifact it publishes,
 * so the filter has one implementation rather than one per adopter.
 */
@Configuration
public class WebInfrastructureConfig {

    @Bean
    public JeffreyExceptionHandler jeffreyExceptionHandler() {
        return new JeffreyExceptionHandler();
    }

    @Bean
    public FilterRegistrationBean<JeffreyRequestLoggingFilter> jeffreyRequestLoggingFilter() {
        FilterRegistrationBean<JeffreyRequestLoggingFilter> bean = new FilterRegistrationBean<>(new JeffreyRequestLoggingFilter());
        bean.addUrlPatterns("/api/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
