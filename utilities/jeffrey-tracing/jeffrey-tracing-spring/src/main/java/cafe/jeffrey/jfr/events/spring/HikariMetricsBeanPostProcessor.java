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
