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

import cafe.jeffrey.heartbeat.HeartbeatSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * The {@code jeffrey.heartbeat.*} configuration, and the bridge from it to the plain
 * {@link HeartbeatSettings} the library consumes.
 *
 * <p><b>Every value is optional, and that is the point.</b> A provisioned application configures
 * itself: the Provisioner exports {@code JEFFREY_HEARTBEAT_DIR} and {@code JEFFREY_HEARTBEAT_ENABLED},
 * and Spring's relaxed binding maps those onto {@code jeffrey.heartbeat.dir} and
 * {@code jeffrey.heartbeat.enabled} with nothing declared anywhere. These properties exist so a
 * deployment can override one of them in {@code application.yaml} without editing a generated file
 * — not because an application is expected to fill them in.</p>
 *
 * <p>An unset value is left unset rather than defaulted here, so that
 * {@link HeartbeatSettings#fromEnvironment()} still gets its turn: the library's own resolution
 * knows about {@code JEFFREY_CURRENT_SESSION}, the older variable that names the session directory
 * rather than the heartbeat folder inside it, and defaulting here would shadow it.</p>
 *
 * @param enabled  whether liveness is reported at all; the Provisioner sets this false when it
 *                 attached the Jeffrey agent, which already beats for that session
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
