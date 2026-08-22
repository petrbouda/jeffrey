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

import cafe.jeffrey.jfr.events.servlet.HttpExchangeSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

import java.util.List;

/**
 * The {@code jeffrey.tracing.*} configuration.
 * <p>
 * A Boot-side type on purpose: {@code jeffrey-events-spring} stays free of Spring Boot, so the
 * plain {@link HttpExchangeSettings} it consumes is built from these bound values rather than being
 * annotated itself.
 *
 * @param enabled          whether the instrumentation is wired at all
 * @param urlPatterns      which requests the filter sees; {@code /*} covers everything
 * @param order            the filter's order in the chain — first by default, so security, routing
 *                         and data access all happen inside the span
 * @param captureQueryParams record query-string parameters on the event; off by default, because
 *                         query strings routinely carry tokens and personal data, and a recording
 *                         is a file that gets shared
 * @param capturePathParams  record the route's template variables on the event; off by default for
 *                         the same reason
 */
@ConfigurationProperties(prefix = "jeffrey.tracing")
public record JeffreyTracingProperties(
        Boolean enabled,
        List<String> urlPatterns,
        Integer order,
        Boolean captureQueryParams,
        Boolean capturePathParams) {

    private static final List<String> ALL_REQUESTS = List.of("/*");

    public JeffreyTracingProperties {
        enabled = enabled == null || enabled;
        urlPatterns = urlPatterns == null || urlPatterns.isEmpty() ? ALL_REQUESTS : List.copyOf(urlPatterns);
        order = order == null ? Ordered.HIGHEST_PRECEDENCE : order;
        captureQueryParams = captureQueryParams != null && captureQueryParams;
        capturePathParams = capturePathParams != null && capturePathParams;
    }

    /**
     * @return the framework-free settings the filter actually consumes
     */
    public HttpExchangeSettings toSettings() {
        return new HttpExchangeSettings(captureQueryParams, capturePathParams);
    }
}
