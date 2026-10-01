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

package cafe.jeffrey.heartbeat.spring.boot;

import cafe.jeffrey.heartbeat.JeffreyHeartbeat;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Starts reporting liveness to a Jeffrey Hub with no code on the application's side.
 *
 * <p>The bean is a {@link JeffreyHeartbeat}, and Spring closing it on context shutdown is what
 * writes the clean-exit marker — which is why this uses {@link JeffreyHeartbeat#start} rather than
 * {@code startFromEnvironment()}. The latter adds a JVM shutdown hook, and inside a container that
 * already has a lifecycle that would be a second thing racing to write the same file.</p>
 *
 * <p>On as soon as the dependency is on the class path. Nothing outside the application decides
 * whether it reports: the Provisioner only names the directory, through
 * {@code -Djeffrey.heartbeat.dir} in the argfile. The same jar runs unchanged on a developer's
 * laptop, where no directory is named and the library answers with an inert instance — which is
 * the property that makes it safe to leave the dependency in.</p>
 *
 * <p>{@code jeffrey.heartbeat.enabled=false} — in {@code application.yaml}, as a system property or
 * as {@code JEFFREY_HEARTBEAT_ENABLED} — turns it off without removing the dependency. That is the
 * application's decision alone. The hub needs no word about it: it holds a session to the
 * heartbeat deadline only once a heartbeat has appeared.</p>
 */
@AutoConfiguration
@ConditionalOnClass(JeffreyHeartbeat.class)
@ConditionalOnProperty(prefix = "jeffrey.heartbeat", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JeffreyHeartbeatProperties.class)
public class JeffreyHeartbeatAutoConfiguration {

    /**
     * {@code destroyMethod} is left to Spring's default, which calls {@link AutoCloseable#close()}
     * on a bean that implements it. Naming it explicitly would be the same thing said twice.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public JeffreyHeartbeat jeffreyHeartbeat(JeffreyHeartbeatProperties properties) {
        return JeffreyHeartbeat.start(properties.toSettings());
    }
}
