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

    @Bean(destroyMethod = "close")
    public JeffreyHeartbeat jeffreyHeartbeat(Clock clock) {
        return JeffreyHeartbeat.start(HeartbeatSettings.fromEnvironment(), clock);
    }
}
