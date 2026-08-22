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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;

/**
 * Records every JDBC statement when the application has a data source at all.
 * <p>
 * Separate from the HTTP auto-configuration because the conditions differ: an application can have
 * one, both or neither, and {@code jeffrey.tracing.jdbc-enabled=false} turns this half off without
 * touching request tracing.
 */
@AutoConfiguration
@ConditionalOnClass({DataSource.class, TracingDataSource.class})
@ConditionalOnProperty(prefix = "jeffrey.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "jeffrey.tracing", name = "jdbc-enabled", havingValue = "true", matchIfMissing = true)
@Import(JeffreyJdbcTracingConfiguration.class)
public class JeffreyJdbcTracingAutoConfiguration {
}
