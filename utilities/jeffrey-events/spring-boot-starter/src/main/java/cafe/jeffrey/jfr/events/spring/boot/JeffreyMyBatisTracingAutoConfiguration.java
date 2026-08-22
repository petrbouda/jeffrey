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

import cafe.jeffrey.jfr.events.mybatis.JeffreyMyBatisInterceptor;
import cafe.jeffrey.jfr.events.mybatis.MyBatisStatementSettings;
import cafe.jeffrey.jfr.events.spring.JeffreyMyBatisTracingConfiguration;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Names every MyBatis statement by the mapper method that issued it, for an application that asks
 * with {@code jeffrey.tracing.mybatis-enabled=true}.
 * <p>
 * Asking is required because the MyBatis jar on the classpath says nothing about whether the
 * application uses it — a transitive dependency is enough to put it there — while the consequence
 * of guessing wrong is severe: this configuration is declared <b>before</b>
 * {@link JeffreyJdbcTracingAutoConfiguration}, which finds the interceptor registered and stands
 * down. That is what stops an application from recording every mapper call twice, once under
 * {@code UserMapper.selectById} and once under the name parsed out of its SQL. Guessed from the
 * classpath, the same rule would silently stop recording statements in an application that has the
 * jar and no mappers.
 * <p>
 * The default is therefore the {@code DataSource} wrapper, which records MyBatis statements too,
 * just under names read out of their SQL. Turning this on trades that for better names.
 * <p>
 * Like the HTTP half, it imports {@link JeffreyMyBatisTracingConfiguration} for the beans that need
 * no Spring Boot and adds only the {@code jeffrey.tracing.*} binding on top.
 */
@AutoConfiguration(before = JeffreyJdbcTracingAutoConfiguration.class)
@ConditionalOnClass({Interceptor.class, JeffreyMyBatisInterceptor.class})
@ConditionalOnProperty(prefix = "jeffrey.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "jeffrey.tracing", name = "mybatis-enabled", havingValue = "true")
@EnableConfigurationProperties(JeffreyTracingProperties.class)
@Import(JeffreyMyBatisTracingConfiguration.class)
public class JeffreyMyBatisTracingAutoConfiguration {

    /**
     * The interceptor's name, for the condition that makes the JDBC half stand down. A compile-time
     * constant, so referring to it loads no MyBatis type in an application that has none.
     */
    static final String INTERCEPTOR_TYPE = "cafe.jeffrey.jfr.events.mybatis.JeffreyMyBatisInterceptor";

    /**
     * Replaces the plain configuration's default settings with what {@code jeffrey.tracing.*} says.
     */
    @Bean
    @ConditionalOnMissingBean
    public MyBatisStatementSettings jeffreyMyBatisStatementSettings(JeffreyTracingProperties properties) {
        return properties.toMyBatisSettings();
    }
}
