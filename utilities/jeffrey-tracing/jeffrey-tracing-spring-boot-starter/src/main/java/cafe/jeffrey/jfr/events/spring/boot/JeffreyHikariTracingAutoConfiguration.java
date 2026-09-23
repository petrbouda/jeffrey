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
