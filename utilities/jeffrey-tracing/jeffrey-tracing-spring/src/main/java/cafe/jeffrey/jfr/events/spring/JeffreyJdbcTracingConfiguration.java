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
