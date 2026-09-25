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
import cafe.jeffrey.jfr.events.jdbc.datasource.TracingDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Wraps every {@link DataSource} bean in a {@link TracingDataSource}, so statements are recorded
 * whatever issues them — JdbcTemplate, Hibernate, jOOQ or MyBatis all go through this interface.
 * <p>
 * A bean post-processor rather than a replacement bean definition: the application keeps declaring
 * its data source exactly as it did, and everything injected with one transparently gets the traced
 * view. The bean name becomes the statement group, which is what separates two pools in Jeffrey's
 * Database dashboard.
 */
public class TracingDataSourceBeanPostProcessor implements BeanPostProcessor {

    private final StatementNaming naming;

    public TracingDataSourceBeanPostProcessor(StatementNaming naming) {
        this.naming = Objects.requireNonNull(naming, "naming must not be null");
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // Not a TracingDataSource already: a context refreshed twice, or an application that wrapped
        // its own, must not end up recording every statement twice.
        if (bean instanceof DataSource dataSource && !(bean instanceof TracingDataSource)) {
            return new TracingDataSource(dataSource, beanName, naming);
        }
        return bean;
    }
}
