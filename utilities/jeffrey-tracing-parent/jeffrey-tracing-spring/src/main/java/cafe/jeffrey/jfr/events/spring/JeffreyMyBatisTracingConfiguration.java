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
