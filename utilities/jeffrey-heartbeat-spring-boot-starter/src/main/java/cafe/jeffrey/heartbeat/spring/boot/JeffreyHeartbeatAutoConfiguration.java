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
 * <p>Nothing here is conditional on the application being provisioned. An application that is not
 * gets settings naming no directory, and the library answers with an inert instance — so the same
 * jar runs unchanged on a developer's laptop and under a Provisioner, which is the property that
 * makes it safe to leave the dependency in.</p>
 *
 * <p>{@code jeffrey.heartbeat.enabled=false} turns it off without removing the dependency. It is
 * also what the Provisioner passes for a session that declared no liveness, so that a jar carrying
 * this starter reports only where the hub was told to expect it — the session marker and the JVM
 * argument come from one setting, and a session cannot be held to a promise it never made.</p>
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
    @Bean
    @ConditionalOnMissingBean
    public JeffreyHeartbeat jeffreyHeartbeat(JeffreyHeartbeatProperties properties) {
        return JeffreyHeartbeat.start(properties.toSettings());
    }
}
