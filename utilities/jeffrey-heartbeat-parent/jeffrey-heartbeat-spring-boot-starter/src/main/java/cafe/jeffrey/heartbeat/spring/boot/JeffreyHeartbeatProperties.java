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

import cafe.jeffrey.heartbeat.HeartbeatSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * The {@code jeffrey.heartbeat.*} configuration, and the bridge from it to the plain
 * {@link HeartbeatSettings} the library consumes.
 *
 * <p><b>Every value is optional, and that is the point.</b> A provisioned application configures
 * itself: the Provisioner passes {@code -Djeffrey.heartbeat.dir} and
 * {@code -Djeffrey.heartbeat.enabled} in the argfile the JVM starts with, which Spring reads as
 * ordinary properties, and exports the matching {@code JEFFREY_HEARTBEAT_*} variables for a
 * deployment that sources the generated {@code .env} instead. These properties exist so a
 * deployment can override one of them in {@code application.yaml} without editing a generated file
 * — not because an application is expected to fill them in.</p>
 *
 * <p>An unset value is left unset rather than defaulted here, so that
 * {@link HeartbeatSettings#fromEnvironment()} still gets its turn: the library's own resolution
 * knows about {@code JEFFREY_CURRENT_SESSION}, the older variable that names the session directory
 * rather than the heartbeat folder inside it, and defaulting here would shadow it.</p>
 *
 * @param enabled  whether liveness is reported at all; the Provisioner sets it from what the
 *                 session declared through {@code heartbeat.enabled}
 * @param dir      where the liveness files go, when something other than the session directory
 * @param interval how often the heartbeat is rewritten
 */
@ConfigurationProperties(prefix = "jeffrey.heartbeat")
public record JeffreyHeartbeatProperties(Boolean enabled, Path dir, Duration interval) {

    /**
     * These properties over what the environment says, and the library's resolution for anything
     * left unset.
     */
    public HeartbeatSettings toSettings() {
        HeartbeatSettings resolved = HeartbeatSettings.fromEnvironment();
        return new HeartbeatSettings(
                dir != null ? dir : resolved.directory(),
                interval != null ? interval : resolved.interval(),
                enabled != null ? enabled : resolved.enabled());
    }
}
