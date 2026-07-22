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

package cafe.jeffrey.agent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

public record AgentArgs(Path heartbeatDir, Duration heartbeatInterval, boolean heartbeatEnabled,
                        AppInformation appInfo) {

    private record KeyValue(String key, String value) {
    }

    // Duplicated from HeartbeatConstants — agent must stay zero-dependency for minimal JAR size
    private static final String PARAM_DIR = "heartbeat.dir";
    private static final String PARAM_INTERVAL = "heartbeat.interval";
    private static final String PARAM_ENABLED = "heartbeat.enabled";
    // Must match HeartbeatConstants.DEFAULT_INTERVAL — both values move together
    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);

    // Duplicated from AppInfoConstants — see note above
    private static final String PARAM_WORKSPACE_ID = "app.workspaceId";
    private static final String PARAM_PROJECT_ID = "app.projectId";
    private static final String PARAM_PROJECT_NAME = "app.projectName";
    private static final String PARAM_PROJECT_LABEL = "app.projectLabel";
    private static final String PARAM_INSTANCE_ID = "app.instanceId";
    private static final String PARAM_SESSION_ID = "app.sessionId";
    private static final String PARAM_SESSION_ORDER = "app.sessionOrder";
    private static final String PARAM_ATTRIBUTES = "app.attributes";
    private static final String PARAM_PROVISIONED_AT = "app.provisionedAt";

    public static AgentArgs parse(String args) {
        if (args == null || args.isBlank()) {
            return new AgentArgs(null, DEFAULT_INTERVAL, true, null);
        }

        Path heartbeatDir = null;
        Duration interval = DEFAULT_INTERVAL;
        boolean enabled = true;

        String workspaceId = null;
        String projectId = null;
        String projectName = null;
        String projectLabel = null;
        String instanceId = null;
        String sessionId = null;
        int sessionOrder = 0;
        String attributes = null;
        long provisionedAt = 0L;

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
                case PARAM_WORKSPACE_ID -> workspaceId = kv.value;
                case PARAM_PROJECT_ID -> projectId = kv.value;
                case PARAM_PROJECT_NAME -> projectName = kv.value;
                case PARAM_PROJECT_LABEL -> projectLabel = decodeBase64(kv.value);
                case PARAM_INSTANCE_ID -> instanceId = kv.value;
                case PARAM_SESSION_ID -> sessionId = kv.value;
                case PARAM_SESSION_ORDER -> sessionOrder = Integer.parseInt(kv.value);
                case PARAM_ATTRIBUTES -> attributes = decodeBase64(kv.value);
                case PARAM_PROVISIONED_AT -> provisionedAt = Long.parseLong(kv.value);
            }
        }

        // Identity is emitted only when the session is known — the agent is the
        // sole emitter and a session id anchors the whole identity graph.
        AppInformation appInfo = sessionId == null ? null : new AppInformation(
                workspaceId, projectId, projectName, projectLabel,
                instanceId, sessionId, sessionOrder, attributes, provisionedAt);

        return new AgentArgs(heartbeatDir, interval, enabled, appInfo);
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

    private static String decodeBase64(String value) {
        if (value.isEmpty()) {
            return "";
        }
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
