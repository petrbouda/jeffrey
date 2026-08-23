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
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Names every MyBatis statement by the mapper method that issued it, for an application that has a
 * {@code SqlSessionFactory}.
 * <p>
 * The factory is the condition, not the jar: a transitive dependency is enough to put MyBatis on
 * the classpath, while the consequence of guessing wrong from that is severe. This configuration is
 * declared <b>before</b> {@link JeffreyJdbcTracingAutoConfiguration}, which finds the interceptor
 * registered and stands down — the rule that stops an application from recording every mapper call
 * twice, once under {@code UserMapper.selectById} and once under the name parsed out of its SQL.
 * Keyed on the classpath, that same rule would silently stop recording statements in an application
 * that has the jar and no mappers. A built {@code SqlSessionFactory} rules that out, and is
 * precisely what makes the interceptor work at all: mybatis-spring registers {@code Interceptor}
 * beans into the factory it builds, so no factory means an interceptor that records nothing.
 * <p>
 * One trade-off survives the condition and is what {@code jeffrey.tracing.mybatis-enabled=false} is
 * for: an application that uses mappers <em>and</em> a plain {@code JdbcTemplate} sees only the
 * mapper calls here, and gets everything back — under SQL-parsed names — by leaving the
 * {@code DataSource} wrapper in charge.
 * <p>
 * Like the HTTP half, it imports {@link JeffreyMyBatisTracingConfiguration} for the beans that need
 * no Spring Boot and adds only the {@code jeffrey.tracing.*} binding on top.
 */
@AutoConfiguration(
        afterName = JeffreyMyBatisTracingAutoConfiguration.MYBATIS_AUTO_CONFIGURATION,
        before = JeffreyJdbcTracingAutoConfiguration.class)
@ConditionalOnClass({Interceptor.class, JeffreyMyBatisInterceptor.class})
// The factory has to exist before this is evaluated, hence the ordering above.
@ConditionalOnBean(SqlSessionFactory.class)
@ConditionalOnProperty(prefix = "jeffrey.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "jeffrey.tracing", name = "mybatis-enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JeffreyTracingProperties.class)
@Import(JeffreyMyBatisTracingConfiguration.class)
public class JeffreyMyBatisTracingAutoConfiguration {

    /**
     * The interceptor's name, for the condition that makes the JDBC half stand down. A compile-time
     * constant, so referring to it loads no MyBatis type in an application that has none.
     */
    static final String INTERCEPTOR_TYPE = "cafe.jeffrey.jfr.events.mybatis.JeffreyMyBatisInterceptor";

    /**
     * Named as a string for the same reason as {@link #INTERCEPTOR_TYPE}: mybatis-spring-boot is not
     * a dependency of this starter, and an application can build its {@code SqlSessionFactory}
     * without it.
     */
    static final String MYBATIS_AUTO_CONFIGURATION = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration";

    /**
     * Replaces the plain configuration's default settings with what {@code jeffrey.tracing.*} says.
     */
    @Bean
    @ConditionalOnMissingBean
    public MyBatisStatementSettings jeffreyMyBatisStatementSettings(JeffreyTracingProperties properties) {
        return properties.toMyBatisSettings();
    }
}
