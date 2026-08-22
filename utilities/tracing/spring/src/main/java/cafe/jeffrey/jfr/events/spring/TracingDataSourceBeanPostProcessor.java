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
