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

package cafe.jeffrey.heartbeat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Where to write liveness files, how often, and whether to write them at all.
 *
 * <p>{@link #fromEnvironment()} resolves all three the way a provisioned application expects,
 * layering <b>explicit system property over environment variable</b> so a deployment can override
 * one value without rewriting the file the Provisioner generated.</p>
 *
 * <p>The directory is resolved from whichever of two variables is set. {@code JEFFREY_HEARTBEAT_DIR}
 * names it outright and is what the Provisioner writes; {@code JEFFREY_CURRENT_SESSION} names the
 * session directory, which has held the heartbeat folder since long before this library existed, so
 * an application running against a session provisioned by an older CLI still finds it.</p>
 *
 * @param directory where the liveness files go, or {@code null} when nothing said
 * @param interval  how often the heartbeat is rewritten
 * @param enabled   whether to write at all
 */
public record HeartbeatSettings(Path directory, Duration interval, boolean enabled) {

    /** Names the heartbeat directory outright. Written by the Jeffrey Provisioner. */
    public static final String DIRECTORY_ENV = "JEFFREY_HEARTBEAT_DIR";

    /**
     * Names the session directory, whose {@code .heartbeat} folder is the same place. Predates
     * {@link #DIRECTORY_ENV} and is the fallback for a session provisioned before it existed.
     */
    public static final String SESSION_ENV = "JEFFREY_CURRENT_SESSION";

    /** Milliseconds between heartbeats. */
    public static final String INTERVAL_ENV = "JEFFREY_HEARTBEAT_INTERVAL";

    /**
     * Set to {@code false} to stand this library down. The Provisioner writes {@code false}
     * whenever it attached the Jeffrey agent, because the agent already beats for that session and
     * two writers of one file is redundant work, not redundancy.
     */
    public static final String ENABLED_ENV = "JEFFREY_HEARTBEAT_ENABLED";

    private static final String PROPERTY_PREFIX = "jeffrey.heartbeat.";
    private static final String DIRECTORY_PROPERTY = PROPERTY_PREFIX + "dir";
    private static final String INTERVAL_PROPERTY = PROPERTY_PREFIX + "interval";
    private static final String ENABLED_PROPERTY = PROPERTY_PREFIX + "enabled";
    private static final String SESSION_PROPERTY = "jeffrey.current.session";

    private static final System.Logger LOG = System.getLogger(HeartbeatSettings.class.getName());

    public HeartbeatSettings {
        if (interval == null) {
            interval = HeartbeatFiles.DEFAULT_INTERVAL;
        }
        if (!interval.isPositive()) {
            throw new IllegalArgumentException("Heartbeat interval must be positive: interval=" + interval);
        }
    }

    /**
     * Settings for an explicitly chosen directory, at the default interval.
     */
    public static HeartbeatSettings of(Path directory) {
        return new HeartbeatSettings(directory, HeartbeatFiles.DEFAULT_INTERVAL, true);
    }

    /**
     * Reads the settings the Provisioner exported. Never throws and never returns {@code null}: a
     * value that cannot be parsed falls back to the default with a warning, because failing an
     * application's startup over a malformed liveness setting would be a worse outcome than not
     * reporting liveness.
     */
    public static HeartbeatSettings fromEnvironment() {
        return resolve(System::getProperty, System::getenv);
    }

    /**
     * The resolution itself, over two arbitrary lookups. Package-private so a test can drive it
     * without touching the real process environment, which cannot be mutated from Java anyway.
     */
    static HeartbeatSettings resolve(
            Function<String, String> properties, Function<String, String> environment) {

        boolean enabled = lookup(properties, ENABLED_PROPERTY, environment, ENABLED_ENV)
                .map(Boolean::parseBoolean)
                .orElse(true);

        Path directory = lookup(properties, DIRECTORY_PROPERTY, environment, DIRECTORY_ENV)
                .map(Path::of)
                .or(() -> lookup(properties, SESSION_PROPERTY, environment, SESSION_ENV)
                        .map(session -> Path.of(session).resolve(HeartbeatFiles.DIRECTORY)))
                .orElse(null);

        Duration interval = lookup(properties, INTERVAL_PROPERTY, environment, INTERVAL_ENV)
                .flatMap(HeartbeatSettings::parseMillis)
                .orElse(HeartbeatFiles.DEFAULT_INTERVAL);

        return new HeartbeatSettings(directory, interval, enabled);
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

    private static Optional<Duration> parseMillis(String value) {
        try {
            long millis = Long.parseLong(value);
            if (millis <= 0) {
                LOG.log(System.Logger.Level.WARNING,
                        "Heartbeat interval must be positive, using the default: value=" + value);
                return Optional.empty();
            }
            return Optional.of(Duration.ofMillis(millis));
        } catch (NumberFormatException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Heartbeat interval is not a number of milliseconds, using the default: value=" + value);
            return Optional.empty();
        }
    }

    /** Whether these settings name somewhere to write and permit writing there. */
    public boolean writable() {
        return enabled && directory != null;
    }
}
