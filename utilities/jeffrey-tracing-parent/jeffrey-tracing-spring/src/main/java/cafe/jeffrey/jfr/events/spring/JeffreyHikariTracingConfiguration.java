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
