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

package cafe.jeffrey.jfr.events.spring.boot;

import cafe.jeffrey.jfr.events.jdbc.datasource.TracingDataSource;
import cafe.jeffrey.jfr.events.spring.JeffreyJdbcTracingConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;

/**
 * Records every JDBC statement when the application has a data source at all.
 * <p>
 * Separate from the HTTP auto-configuration because the conditions differ: an application can have
 * one, both or neither, and {@code jeffrey.tracing.jdbc-enabled=false} turns this half off without
 * touching request tracing.
 * <p>
 * It also backs off entirely once the MyBatis interceptor is registered — which happens for any
 * application with a {@code SqlSessionFactory}, unless it says
 * {@code jeffrey.tracing.mybatis-enabled=false}. Both record the same statements, so running the
 * two would put every mapper call in the dashboard twice, once under its mapper method and once
 * under a name parsed out of its SQL. The trade-off is the reason that property exists: an
 * application that uses MyBatis <em>and</em> a plain {@code JdbcTemplate} loses the template's
 * statements, and gets them back by turning MyBatis off and leaving this half in charge.
 */
@AutoConfiguration
@ConditionalOnClass({DataSource.class, TracingDataSource.class})
@ConditionalOnProperty(prefix = "jeffrey.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "jeffrey.tracing", name = "jdbc-enabled", havingValue = "true", matchIfMissing = true)
// Named as a string, not a class literal: the interceptor implements a MyBatis type, so naming it
// by class would load org.apache.ibatis in an application that has no MyBatis at all.
@ConditionalOnMissingBean(type = JeffreyMyBatisTracingAutoConfiguration.INTERCEPTOR_TYPE)
@Import(JeffreyJdbcTracingConfiguration.class)
public class JeffreyJdbcTracingAutoConfiguration {
}
