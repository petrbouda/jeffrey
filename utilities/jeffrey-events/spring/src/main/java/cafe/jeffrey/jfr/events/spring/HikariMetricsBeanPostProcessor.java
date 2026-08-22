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

import cafe.jeffrey.jfr.events.jdbc.hikari.JfrMetricsTrackerFactory;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Gives every HikariCP pool a Jeffrey metrics tracker, which is what makes the connection-pool
 * events appear at all.
 * <p>
 * Runs <em>before</em> initialisation, and deliberately: the pool has not started yet, and
 * {@link TracingDataSourceBeanPostProcessor} replaces the bean with a {@link javax.sql.DataSource}
 * wrapper afterwards — by then it is no longer a {@link HikariDataSource} to look at.
 * <p>
 * A pool that already has a tracker keeps it. An application that configured its own metrics is
 * making a deliberate choice, and silently replacing it would be the instrumentation deciding
 * something that is not its to decide.
 */
public class HikariMetricsBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof HikariDataSource pool
                && !pool.isRunning()
                && pool.getMetricsTrackerFactory() == null) {

            pool.setMetricsTrackerFactory(new JfrMetricsTrackerFactory());
        }
        return bean;
    }
}
