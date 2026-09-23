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
 * <p>The whole configuration is gated on {@value #JEFFREY_ENABLED_PROPERTY} being {@code true},
 * with no default: that is the {@code JEFFREY_ENABLED} variable a jeffrey-jib pod sets, reaching
 * Spring through its relaxed binding of environment variables. A container without it contributes
 * no bean at all, so the same jar runs unchanged on a developer's laptop and under a Provisioner,
 * which is the property that makes it safe to leave the dependency in. Even when it is set, an
 * application the Provisioner did not touch gets settings naming no directory and the library
 * answers with an inert instance.</p>
 *
 * <p>{@code jeffrey.heartbeat.enabled=false} turns it off without removing the dependency. It is
 * also what the Provisioner passes for a session that declared no liveness, so that a jar carrying
 * this starter reports only where the hub was told to expect it — the session marker and the JVM
 * argument come from one setting, and a session cannot be held to a promise it never made.</p>
 */
@AutoConfiguration
@ConditionalOnClass(JeffreyHeartbeat.class)
@ConditionalOnProperty(name = JeffreyHeartbeatAutoConfiguration.JEFFREY_ENABLED_PROPERTY, havingValue = "true")
@ConditionalOnProperty(prefix = "jeffrey.heartbeat", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JeffreyHeartbeatProperties.class)
public class JeffreyHeartbeatAutoConfiguration {

    /**
     * The master switch of a jeffrey-jib container, {@code JEFFREY_ENABLED}, as Spring's relaxed
     * binding spells it. Deliberately not defaulted: an application that was never told profiling
     * is on must not report liveness for a session nobody provisioned.
     */
    static final String JEFFREY_ENABLED_PROPERTY = "jeffrey.enabled";

    /**
     * {@code destroyMethod} is left to Spring's default, which calls {@link AutoCloseable#close()}
     * on a bean that implements it. Naming it explicitly would be the same thing said twice.
     */
    @Bean
    @ConditionalOnMissingBean
    public JeffreyHeartbeat jeffreyHeartbeat(JeffreyHeartbeatProperties properties) {
        return JeffreyHeartbeat.start(properties.toSettings());
    }
}
