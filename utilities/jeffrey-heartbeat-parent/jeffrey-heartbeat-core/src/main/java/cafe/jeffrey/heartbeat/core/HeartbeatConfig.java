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
package cafe.jeffrey.heartbeat.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Where to write liveness files, how often, and whether to write them at all, resolved with an
 * explicit system property winning over the matching environment variable. The property comes
 * first because the Provisioner writes it into the argfile, the one channel that reaches a JVM the
 * container entrypoint execs.
 *
 * @param directory where the liveness files go, or {@code null} when nothing said
 * @param interval  time between two beats
 * @param enabled   {@code false} when the application switched liveness reporting off
 */
public record HeartbeatConfig(Path directory, Duration interval, boolean enabled) {

    public HeartbeatConfig {
        if (interval == null) {
            interval = HeartbeatContract.DEFAULT_INTERVAL;
        }
        if (!interval.isPositive()) {
            throw new IllegalArgumentException("Heartbeat interval must be positive: interval=" + interval);
        }
    }

    /**
     * Never throws: a value that cannot be parsed falls back to the default with a warning, because
     * failing an application's startup over a liveness setting is worse than not reporting liveness.
     */
    public static HeartbeatConfig resolve(
            Function<String, String> properties, Function<String, String> environment, HeartbeatLog log) {

        boolean enabled = lookup(properties, HeartbeatContract.ENABLED_PROPERTY, environment, HeartbeatContract.ENABLED_ENV)
                .map(Boolean::parseBoolean)
                .orElse(true);

        Path directory = lookup(properties, HeartbeatContract.DIRECTORY_PROPERTY, environment, HeartbeatContract.DIRECTORY_ENV)
                .map(Path::of)
                .or(() -> lookup(properties, HeartbeatContract.SESSION_PROPERTY, environment, HeartbeatContract.SESSION_ENV)
                        .map(session -> Path.of(session).resolve(HeartbeatContract.DIRECTORY)))
                .orElse(null);

        Duration interval = lookup(properties, HeartbeatContract.INTERVAL_PROPERTY, environment, HeartbeatContract.INTERVAL_ENV)
                .flatMap(value -> parseMillis(value, log))
                .orElse(HeartbeatContract.DEFAULT_INTERVAL);

        return new HeartbeatConfig(directory, interval, enabled);
    }

    /** Whether this names somewhere to write and permits writing there. */
    public boolean writable() {
        return enabled && directory != null;
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

    private static Optional<Duration> parseMillis(String value, HeartbeatLog log) {
        try {
            long millis = Long.parseLong(value);
            if (millis <= 0) {
                log.warn("Heartbeat interval must be positive, using the default: value=" + value, null);
                return Optional.empty();
            }
            return Optional.of(Duration.ofMillis(millis));
        } catch (NumberFormatException e) {
            log.warn("Heartbeat interval is not a number of milliseconds, using the default: value=" + value, null);
            return Optional.empty();
        }
    }
}
