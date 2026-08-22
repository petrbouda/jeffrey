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

import cafe.jeffrey.jfr.events.mybatis.MyBatisStatementSettings;
import cafe.jeffrey.jfr.events.servlet.HttpExchangeSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

import java.util.List;

/**
 * The {@code jeffrey.tracing.*} configuration.
 * <p>
 * A Boot-side type on purpose: {@code jeffrey-tracing-spring} stays free of Spring Boot, so the
 * plain {@link HttpExchangeSettings} it consumes is built from these bound values rather than being
 * annotated itself.
 *
 * @param enabled          whether the instrumentation is wired at all
 * @param urlPatterns      which requests the filter sees; {@code /*} covers everything
 * @param order            the filter's order in the chain — first by default, so security, routing
 *                         and data access all happen inside the span
 * @param jdbcEnabled      whether every {@code DataSource} bean is wrapped to record statements —
 *                         ignored once {@code mybatisEnabled} is on, since the two would record
 *                         every mapper call twice
 * @param hikariEnabled    whether HikariCP pools get a Jeffrey metrics tracker
 * @param mybatisEnabled   whether MyBatis statements are recorded by their mapper method instead of
 *                         through the {@code DataSource} wrapper. Off by default and asked for
 *                         explicitly: MyBatis on the classpath is not evidence that the application
 *                         uses it, and backing the wrapper off on that guess would silently stop
 *                         recording statements. Turned on, it does take over from {@code
 *                         jdbcEnabled}, because a mapper method is a better name than one parsed
 *                         out of SQL
 * @param mybatisCaptureParameters record the values a MyBatis statement was bound with; on by
 *                         default, because they are what separates the slow call from the
 *                         thousands sharing its SQL — turn it off for an application whose mappers
 *                         take values it would not paste into a bug report, since a recording is a
 *                         file that gets shared
 * @param mybatisMaxParameterLength longest parameter value recorded before truncation
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
        Boolean jdbcEnabled,
        Boolean hikariEnabled,
        Boolean mybatisEnabled,
        Boolean mybatisCaptureParameters,
        Integer mybatisMaxParameterLength,
        Boolean captureQueryParams,
        Boolean capturePathParams) {

    private static final List<String> ALL_REQUESTS = List.of("/*");

    public JeffreyTracingProperties {
        enabled = enabled == null || enabled;
        urlPatterns = urlPatterns == null || urlPatterns.isEmpty() ? ALL_REQUESTS : List.copyOf(urlPatterns);
        order = order == null ? Ordered.HIGHEST_PRECEDENCE : order;
        jdbcEnabled = jdbcEnabled == null || jdbcEnabled;
        hikariEnabled = hikariEnabled == null || hikariEnabled;
        mybatisEnabled = mybatisEnabled != null && mybatisEnabled;
        mybatisCaptureParameters = mybatisCaptureParameters == null || mybatisCaptureParameters;
        mybatisMaxParameterLength = mybatisMaxParameterLength == null
                ? MyBatisStatementSettings.defaults().maxValueLength()
                : mybatisMaxParameterLength;
        captureQueryParams = captureQueryParams != null && captureQueryParams;
        capturePathParams = capturePathParams != null && capturePathParams;
    }

    /**
     * @return the framework-free settings the filter actually consumes
     */
    public HttpExchangeSettings toSettings() {
        return new HttpExchangeSettings(captureQueryParams, capturePathParams);
    }

    /**
     * @return the framework-free settings the MyBatis interceptor actually consumes
     */
    public MyBatisStatementSettings toMyBatisSettings() {
        return new MyBatisStatementSettings(mybatisCaptureParameters, mybatisMaxParameterLength);
    }
}
