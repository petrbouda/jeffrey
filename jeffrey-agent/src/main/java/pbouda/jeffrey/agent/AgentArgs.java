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

package pbouda.jeffrey.agent;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public record AgentArgs(Path heartbeatDir, Duration heartbeatInterval, boolean heartbeatEnabled) {

    private record KeyValue(String key, String value) {
    }

    // Duplicated from HeartbeatConstants — agent must stay zero-dependency for minimal JAR size
    private static final String PARAM_DIR = "heartbeat.dir";
    private static final String PARAM_INTERVAL = "heartbeat.interval";
    private static final String PARAM_ENABLED = "heartbeat.enabled";
    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);

    public static AgentArgs parse(String args) {
        if (args == null || args.isBlank()) {
            return new AgentArgs(null, DEFAULT_INTERVAL, true);
        }

        Path heartbeatDir = null;
        Duration interval = DEFAULT_INTERVAL;
        boolean enabled = true;

        for (String part : args.split(",")) {
            Optional<KeyValue> kvOpt = parseKeyValue(part);
            if (kvOpt.isEmpty()) {
                continue;
            }
            KeyValue kv = kvOpt.get();
            switch (kv.key) {
                case PARAM_DIR -> heartbeatDir = Path.of(kv.value);
                case PARAM_INTERVAL -> interval = Duration.ofMillis(Long.parseLong(kv.value));
                case PARAM_ENABLED -> enabled = Boolean.parseBoolean(kv.value);
            }
        }

        return new AgentArgs(heartbeatDir, interval, enabled);
    }

    private static Optional<KeyValue> parseKeyValue(String part) {
        String trimmed = part.trim();
        int eq = trimmed.indexOf('=');
        if (eq < 0) {
            return Optional.empty();
        }
        String key = trimmed.substring(0, eq).trim();
        String value = trimmed.substring(eq + 1).trim();
        return Optional.of(new KeyValue(key, value));
    }
}
