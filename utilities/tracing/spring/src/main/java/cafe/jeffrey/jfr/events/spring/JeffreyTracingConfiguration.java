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

package cafe.jeffrey.jfr.events.spring;

import cafe.jeffrey.jfr.events.servlet.HttpExchangeFilter;
import cafe.jeffrey.jfr.events.servlet.HttpExchangeSettings;
import cafe.jeffrey.jfr.events.servlet.HttpRequestNaming;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The beans that instrument a Spring application, wired <em>explicitly</em>.
 * <p>
 * Nothing here happens on its own. This module ships no
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so
 * having it on the classpath — even of a Spring Boot application — registers nothing at all. An
 * application asks for it:
 *
 * <pre>{@code
 * @Configuration
 * @Import(JeffreyTracingConfiguration.class)
 * class ObservabilityConfiguration {
 * }
 * }</pre>
 *
 * That is the point of the split. Applications that want instrumentation to appear by itself add
 * {@code jeffrey-tracing-spring-boot-starter}, whose auto-configuration imports this class and adds
 * the Boot-only pieces — filter registration order and URL patterns, and property binding. Keeping
 * those out of here is not tidiness: {@code FilterRegistrationBean} is a Spring Boot type, and
 * depending on it would make this module unusable from the plain Spring MVC applications it is
 * meant to serve.
 *
 * <h2>Registering the filter without Boot</h2>
 * Plain Spring MVC has no {@code FilterRegistrationBean}, so register the {@link HttpExchangeFilter}
 * bean the way that stack does — {@code web.xml}, or
 * {@code AbstractAnnotationConfigDispatcherServletInitializer#getServletFilters}. Register it first
 * in the chain, so everything the request does happens inside the span.
 */
@Configuration(proxyBeanMethods = false)
public class JeffreyTracingConfiguration {

    /**
     * Names requests by the matched Spring MVC handler pattern, which is what keeps HTTP span names
     * low-cardinality.
     */
    @Bean
    public HttpRequestNaming jeffreyHttpRequestNaming() {
        return new SpringMvcRequestNaming();
    }

    /**
     * The filter that makes each inbound request the root span of its trace.
     * <p>
     * Settings are taken from a bean when one exists and default to recording nothing beyond the
     * request's shape when none does. Deliberately not a {@code @Bean} of its own here: this
     * configuration is imported <em>by</em> the starter's auto-configuration, so a default settings
     * bean defined here would already be registered by the time the starter's
     * {@code @ConditionalOnMissingBean} was evaluated - and the bean bound from
     * {@code jeffrey.tracing.*} would silently lose to it.
     */
    @Bean
    public HttpExchangeFilter jeffreyHttpExchangeFilter(
            HttpRequestNaming naming, ObjectProvider<HttpExchangeSettings> settings) {
        return new HttpExchangeFilter(naming, settings.getIfAvailable(HttpExchangeSettings::defaults));
    }

    /**
     * Records outbound calls made through a {@code RestTemplate} this interceptor is added to.
     * <p>
     * It is only a bean here, not attached to anything: Spring Boot 4 removed
     * {@code RestTemplateCustomizer}, and a plain Spring application builds its own clients anyway.
     * Add it where the client is built — {@code restTemplate.getInterceptors().add(interceptor)}.
     */
    @Bean
    public JfrClientHttpRequestInterceptor jeffreyClientHttpRequestInterceptor() {
        return new JfrClientHttpRequestInterceptor();
    }
}
