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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Records connection-pool behaviour from HikariCP, wired <em>explicitly</em>.
 * <p>
 * Kept in its own configuration so that its types are loaded only by an application that actually
 * has HikariCP: referencing {@code HikariDataSource} from a shared configuration would break every
 * application using a different pool.
 *
 * <pre>{@code
 * @Import(JeffreyHikariTracingConfiguration.class)
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
public class JeffreyHikariTracingConfiguration {

    /**
     * Gives each Hikari pool a Jeffrey metrics tracker, which is the only thing that makes the
     * pool events - acquire, borrow, create, timeout, and the periodic gauge - appear.
     */
    @Bean
    public static HikariMetricsBeanPostProcessor jeffreyHikariMetricsBeanPostProcessor() {
        return new HikariMetricsBeanPostProcessor();
    }
}
