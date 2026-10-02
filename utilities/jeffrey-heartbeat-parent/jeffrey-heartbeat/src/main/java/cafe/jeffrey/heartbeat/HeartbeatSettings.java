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
package cafe.jeffrey.heartbeat;

import cafe.jeffrey.heartbeat.core.HeartbeatConfig;
import cafe.jeffrey.heartbeat.core.HeartbeatContract;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Function;

/**
 * Where to write liveness files, how often, and whether to write them at all.
 *
 * <p>{@link #fromEnvironment()} resolves all three the way a provisioned application expects,
 * layering <b>explicit system property over environment variable</b>. That order is what makes the
 * library reachable at all in a container: the Provisioner writes the system properties into the
 * argfile the entrypoint execs the JVM with, while the environment variables live in a
 * {@code .env} file that is generated only on request and that nothing sources on that path.
 * The resolution itself is {@link HeartbeatConfig#resolve}, shared with the Jeffrey agent.</p>
 *
 * @param directory where the liveness files go, or {@code null} when nothing said
 * @param interval  how often the heartbeat is rewritten
 * @param enabled   whether to write at all; on unless the application says otherwise
 */
public record HeartbeatSettings(Path directory, Duration interval, boolean enabled) {

    /** Names the heartbeat directory outright. Written by the Jeffrey Provisioner. */
    public static final String DIRECTORY_ENV = HeartbeatContract.DIRECTORY_ENV;

    /** Names the session directory, whose {@code .heartbeat} folder is the same place; the fallback. */
    public static final String SESSION_ENV = HeartbeatContract.SESSION_ENV;

    /** Milliseconds between heartbeats. */
    public static final String INTERVAL_ENV = HeartbeatContract.INTERVAL_ENV;

    /** Set to {@code false} to stand this library down. The application's own switch. */
    public static final String ENABLED_ENV = HeartbeatContract.ENABLED_ENV;

    /**
     * System property counterpart of {@link #DIRECTORY_ENV}, and the one the Provisioner actually
     * uses: it writes this into the argfile. Mirrored by {@code HeartbeatConstants.DIRECTORY_PROPERTY}.
     */
    public static final String DIRECTORY_PROPERTY = HeartbeatContract.DIRECTORY_PROPERTY;

    /** System property counterpart of {@link #INTERVAL_ENV}, in milliseconds. */
    public static final String INTERVAL_PROPERTY = HeartbeatContract.INTERVAL_PROPERTY;

    /** System property counterpart of {@link #ENABLED_ENV}, likewise the application's own switch. */
    public static final String ENABLED_PROPERTY = HeartbeatContract.ENABLED_PROPERTY;

    /**
     * Set to {@code true} by the Jeffrey agent once it is beating; while it is, this library stays
     * inert, because both would write the same files through the same scratch names.
     */
    public static final String AGENT_ACTIVE_PROPERTY = HeartbeatContract.AGENT_ACTIVE_PROPERTY;

    public HeartbeatSettings {
        if (interval == null) {
            interval = HeartbeatFiles.DEFAULT_INTERVAL;
        }
        if (!interval.isPositive()) {
            throw new IllegalArgumentException("Heartbeat interval must be positive: interval=" + interval);
        }
    }

    /** Settings for an explicitly chosen directory, at the default interval. */
    public static HeartbeatSettings of(Path directory) {
        return new HeartbeatSettings(directory, HeartbeatFiles.DEFAULT_INTERVAL, true);
    }

    /**
     * Reads the settings the Provisioner exported. Never throws and never returns {@code null}: a
     * value that cannot be parsed falls back to the default with a warning.
     */
    public static HeartbeatSettings fromEnvironment() {
        return resolve(System::getProperty, System::getenv);
    }

    /** Over two arbitrary lookups, so a test can drive it without the real process environment. */
    static HeartbeatSettings resolve(Function<String, String> properties, Function<String, String> environment) {
        HeartbeatConfig config = HeartbeatConfig.resolve(
                properties, environment, new Slf4jHeartbeatLog(HeartbeatSettings.class));
        return new HeartbeatSettings(config.directory(), config.interval(), config.enabled());
    }

    /** Whether these settings name somewhere to write and permit writing there. */
    public boolean writable() {
        return enabled && directory != null;
    }
}
