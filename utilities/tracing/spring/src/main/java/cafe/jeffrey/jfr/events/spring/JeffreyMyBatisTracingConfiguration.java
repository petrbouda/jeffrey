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

import cafe.jeffrey.jfr.events.mybatis.JeffreyMyBatisInterceptor;
import cafe.jeffrey.jfr.events.mybatis.MyBatisStatementSettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Records every MyBatis statement, wired <em>explicitly</em>.
 * <p>
 * Declaring the interceptor as a bean is the whole registration: mybatis-spring adds every
 * {@code Interceptor} bean to the {@code SqlSessionFactory} it builds.
 * <p>
 * Import this <b>or</b> {@link JeffreyJdbcTracingConfiguration}, never both — they would record the
 * same statement twice, once named by mapper method and once by parsed SQL. Prefer this one
 * wherever MyBatis is how the application talks to the database, because a statement id is a better
 * name than anything a {@code DataSource} proxy can derive.
 *
 * <pre>{@code
 * @Import(JeffreyMyBatisTracingConfiguration.class)
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
public class JeffreyMyBatisTracingConfiguration {

    /**
     * Parameter capture is on unless the application declares its own
     * {@link MyBatisStatementSettings} bean saying otherwise.
     */
    @Bean
    public JeffreyMyBatisInterceptor jeffreyMyBatisInterceptor(ObjectProvider<MyBatisStatementSettings> settings) {
        return new JeffreyMyBatisInterceptor(settings.getIfAvailable(MyBatisStatementSettings::defaults));
    }
}
