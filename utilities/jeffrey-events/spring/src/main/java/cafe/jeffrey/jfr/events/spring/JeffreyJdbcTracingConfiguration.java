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

import cafe.jeffrey.jfr.events.jdbc.datasource.StatementNaming;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Records every JDBC statement, wired <em>explicitly</em>.
 * <p>
 * A separate configuration from {@link JeffreyTracingConfiguration} rather than more beans inside
 * it: an application with no data source must be able to import the HTTP instrumentation without
 * this one's types being loaded, and a Spring Boot application gets this one only when
 * {@code javax.sql.DataSource} is actually on the classpath.
 *
 * <pre>{@code
 * @Import(JeffreyJdbcTracingConfiguration.class)
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
public class JeffreyJdbcTracingConfiguration {

    /**
     * Wraps every {@code DataSource} bean so its statements are recorded.
     * <p>
     * Statement naming defaults to verb and primary table; an application with better names — a
     * repository method, a mapper id — declares its own {@link StatementNaming} bean.
     */
    @Bean
    public static TracingDataSourceBeanPostProcessor jeffreyTracingDataSourceBeanPostProcessor(
            ObjectProvider<StatementNaming> naming) {

        return new TracingDataSourceBeanPostProcessor(naming.getIfAvailable(StatementNaming::verbAndTable));
    }
}
