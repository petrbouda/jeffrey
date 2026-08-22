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
 * It also backs off entirely once the MyBatis interceptor is registered — which happens only when
 * an application asks for it with {@code jeffrey.tracing.mybatis-enabled=true}. Both record the
 * same statements, so running the two would put every mapper call in the dashboard twice, once
 * under its mapper method and once under a name parsed out of its SQL. The trade-off comes with
 * that request: an application that uses MyBatis <em>and</em> a plain {@code JdbcTemplate} loses
 * the template's statements, and gets them back by leaving this half in charge.
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
