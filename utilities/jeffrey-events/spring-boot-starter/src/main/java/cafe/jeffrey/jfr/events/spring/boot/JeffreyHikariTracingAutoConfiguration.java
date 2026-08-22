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

import cafe.jeffrey.jfr.events.jdbc.hikari.JfrMetricsTrackerFactory;
import cafe.jeffrey.jfr.events.spring.JeffreyHikariTracingConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * Gives HikariCP pools a Jeffrey metrics tracker, which is what makes the connection-pool events
 * appear — they have shipped since the beginning with nothing to produce them.
 * <p>
 * Conditional on HikariCP being the pool in use, so an application on another pool loads none of
 * these types.
 */
@AutoConfiguration
@ConditionalOnClass({HikariDataSource.class, JfrMetricsTrackerFactory.class})
@ConditionalOnProperty(prefix = "jeffrey.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "jeffrey.tracing", name = "hikari-enabled", havingValue = "true", matchIfMissing = true)
@Import(JeffreyHikariTracingConfiguration.class)
public class JeffreyHikariTracingAutoConfiguration {
}
