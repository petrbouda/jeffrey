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

package cafe.jeffrey.hub.core.configuration;

import cafe.jeffrey.heartbeat.HeartbeatSettings;
import cafe.jeffrey.heartbeat.JeffreyHeartbeat;
import cafe.jeffrey.shared.common.HeartbeatConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reports the hub's own liveness when the hub itself runs as a profiled JVM.
 *
 * <p>The hub image is built with jeffrey-jib, so a pod that sets {@code JEFFREY_ENABLED=true} runs
 * the hub under the Provisioner like any other application. A session that declares
 * {@code heartbeat.enabled=true} is held to a heartbeat deadline, and a hub carrying no producer
 * would finish its own session seconds after start.</p>
 *
 * <p>Nothing here reads {@code JEFFREY_ENABLED}. The bean exists only when
 * {@value HeartbeatConstants#ENABLED_PROPERTY} is {@code true}, which the Provisioner writes into
 * the argfile on exactly that path, together with the directory to beat into. A hub started without
 * profiling has neither property and creates no bean; a session that declared no liveness gets
 * {@code false} and creates none either. Spring closes the bean on context shutdown, which writes
 * the clean-exit marker.</p>
 */
@Configuration
@ConditionalOnProperty(name = HeartbeatConstants.ENABLED_PROPERTY, havingValue = "true")
public class HeartbeatConfiguration {

    @Bean
    public JeffreyHeartbeat jeffreyHeartbeat(Clock clock) {
        return JeffreyHeartbeat.start(HeartbeatSettings.fromEnvironment(), clock);
    }
}
