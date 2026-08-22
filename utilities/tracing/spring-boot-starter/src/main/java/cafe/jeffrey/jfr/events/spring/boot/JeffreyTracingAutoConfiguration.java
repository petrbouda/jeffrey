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

package cafe.jeffrey.jfr.events.spring.boot;

import cafe.jeffrey.jfr.events.servlet.HttpExchangeFilter;
import cafe.jeffrey.jfr.events.servlet.HttpExchangeSettings;
import cafe.jeffrey.jfr.events.spring.JeffreyTracingConfiguration;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Wires Jeffrey's instrumentation into a Spring Boot application with no code on the application's
 * side — the "one dependency, zero code" half of the split.
 * <p>
 * It imports {@link JeffreyTracingConfiguration} for the framework-portable beans and adds only
 * what needs Spring Boot: registering the filter with an order and URL patterns
 * ({@link FilterRegistrationBean} is a Boot type), and binding {@code jeffrey.tracing.*}.
 * <p>
 * Every bean is guarded with {@link ConditionalOnMissingBean}, so an application that also imports
 * {@link JeffreyTracingConfiguration} by hand — or declares its own filter, naming strategy or
 * settings — gets exactly one of each rather than two. Setting
 * {@code jeffrey.tracing.enabled=false} turns the whole thing off without removing the dependency.
 */
@AutoConfiguration
@ConditionalOnClass({Filter.class, HttpExchangeFilter.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "jeffrey.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JeffreyTracingProperties.class)
@Import(JeffreyTracingConfiguration.class)
public class JeffreyTracingAutoConfiguration {

    /**
     * Replaces the plain configuration's default settings with what {@code jeffrey.tracing.*} says.
     */
    @Bean
    @ConditionalOnMissingBean
    public HttpExchangeSettings jeffreyHttpExchangeSettings(JeffreyTracingProperties properties) {
        return properties.toSettings();
    }

    /**
     * Registers the filter first in the chain by default, so security, routing and data access all
     * happen inside the request's span.
     */
    @Bean
    @ConditionalOnMissingBean(name = "jeffreyHttpExchangeFilterRegistration")
    public FilterRegistrationBean<HttpExchangeFilter> jeffreyHttpExchangeFilterRegistration(
            HttpExchangeFilter filter, JeffreyTracingProperties properties) {

        FilterRegistrationBean<HttpExchangeFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setUrlPatterns(properties.urlPatterns());
        registration.setOrder(properties.order());
        return registration;
    }
}
