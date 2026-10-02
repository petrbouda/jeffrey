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

package cafe.jeffrey.agent;

import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Where and how often the agent beats, resolved the way {@code HeartbeatSettings} in
 * {@code jeffrey-heartbeat} resolves it: a system property first, then the matching environment
 * variable. The provisioner writes {@code -Djeffrey.heartbeat.dir} into the argfile, so one
 * setting serves the agent and the library alike, whichever of them the application runs with.
 *
 * <p>The names are copied rather than shared: the agent depends on none of Jeffrey's modules,
 * which keeps it off the profiled application's class path in every form but its own small jar.
 * They move together with {@code HeartbeatSettings} and {@code HeartbeatConstants}.
 *
 * @param directory where the liveness files go, or {@code null} when nothing names one
 * @param interval  time between two beats
 * @param enabled   {@code false} when the application switched liveness reporting off
 */
public record AgentSettings(Path directory, Duration interval, boolean enabled) {

    private static final System.Logger LOG = System.getLogger(AgentSettings.class.getName());

    static final String DIRECTORY_PROPERTY = "jeffrey.heartbeat.dir";
    static final String DIRECTORY_ENV = "JEFFREY_HEARTBEAT_DIR";
    static final String INTERVAL_PROPERTY = "jeffrey.heartbeat.interval";
    static final String INTERVAL_ENV = "JEFFREY_HEARTBEAT_INTERVAL";
    static final String ENABLED_PROPERTY = "jeffrey.heartbeat.enabled";
    static final String ENABLED_ENV = "JEFFREY_HEARTBEAT_ENABLED";
    static final String SESSION_PROPERTY = "jeffrey.current.session";
    static final String SESSION_ENV = "JEFFREY_CURRENT_SESSION";

    /** Directory inside the session directory that holds the liveness files. */
    static final String HEARTBEAT_DIR = ".heartbeat";

    /**
     * Set to {@code true} once the agent is beating. The {@code jeffrey-heartbeat} library reads it
     * and stays inert, so an application carrying the library and running with the agent has one
     * writer, not two. Must match {@code HeartbeatSettings.AGENT_ACTIVE_PROPERTY}.
     */
    static final String AGENT_ACTIVE_PROPERTY = "jeffrey.heartbeat.agent";

    /** Must match {@code HeartbeatConstants.DEFAULT_INTERVAL}, which the hub's staleness threshold is a multiple of. */
    static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);

    public AgentSettings {
        if (interval == null) {
            interval = DEFAULT_INTERVAL;
        }
        if (!interval.isPositive()) {
            throw new IllegalArgumentException("Heartbeat interval must be positive: interval=" + interval);
        }
    }

    static AgentSettings fromEnvironment() {
        return resolve(System::getProperty, System::getenv);
    }

    /** Over two arbitrary lookups, so a test can drive it without the real process environment. */
    static AgentSettings resolve(Function<String, String> properties, Function<String, String> environment) {
        boolean enabled = lookup(properties, ENABLED_PROPERTY, environment, ENABLED_ENV)
                .map(Boolean::parseBoolean)
                .orElse(true);

        Path directory = lookup(properties, DIRECTORY_PROPERTY, environment, DIRECTORY_ENV)
                .map(Path::of)
                .or(() -> lookup(properties, SESSION_PROPERTY, environment, SESSION_ENV)
                        .map(session -> Path.of(session).resolve(HEARTBEAT_DIR)))
                .orElse(null);

        Duration interval = lookup(properties, INTERVAL_PROPERTY, environment, INTERVAL_ENV)
                .flatMap(AgentSettings::parseMillis)
                .orElse(DEFAULT_INTERVAL);

        return new AgentSettings(directory, interval, enabled);
    }

    private static Optional<String> lookup(
            Function<String, String> properties, String property,
            Function<String, String> environment, String variable) {

        String fromProperty = properties.apply(property);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return Optional.of(fromProperty.strip());
        }
        String fromEnvironment = environment.apply(variable);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return Optional.of(fromEnvironment.strip());
        }
        return Optional.empty();
    }

    /** A malformed interval falls back to the default: it is no reason to stop an application. */
    private static Optional<Duration> parseMillis(String value) {
        try {
            long millis = Long.parseLong(value);
            if (millis <= 0) {
                LOG.log(Level.WARNING, "Heartbeat interval must be positive, using the default: value=" + value);
                return Optional.empty();
            }
            return Optional.of(Duration.ofMillis(millis));
        } catch (NumberFormatException e) {
            LOG.log(Level.WARNING, "Heartbeat interval is not a number of milliseconds, using the default: value=" + value);
            return Optional.empty();
        }
    }
}
