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

package cafe.jeffrey.provisioner.feature;

import cafe.jeffrey.provisioner.AppIdentity;
import cafe.jeffrey.shared.common.AppInfoConstants;
import cafe.jeffrey.shared.common.HeartbeatConstants;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Builds the argument string passed to the Jeffrey agent, carrying the identity it emits in every
 * {@code jeffrey.AppInformation} event.
 *
 * <p>The agent splits its arguments on {@code ','}, so the two free-form values — the project
 * label and the serialized attributes — are Base64-encoded. Every other value is delimiter-safe.
 */
public abstract class AgentArguments {

    private static final String SEPARATOR = ",";
    private static final String ASSIGN = "=";

    /** Turns on {@code @Traced} method tracing; off unless the session asks for it. */
    private static final String METHOD_TRACING = "tracing.enabled=true";

    public static String of(String heartbeatDir, boolean methodTracingEnabled, AppIdentity identity) {
        List<String> arguments = new ArrayList<>();
        arguments.add(HeartbeatConstants.PARAM_DIR + ASSIGN + heartbeatDir);

        if (methodTracingEnabled) {
            arguments.add(METHOD_TRACING);
        }
        if (identity != null) {
            addIdentity(arguments, identity);
        }
        return String.join(SEPARATOR, arguments);
    }

    private static void addIdentity(List<String> arguments, AppIdentity identity) {
        add(arguments, AppInfoConstants.PARAM_WORKSPACE_ID, identity.workspaceId());
        add(arguments, AppInfoConstants.PARAM_PROJECT_ID, identity.projectId());
        add(arguments, AppInfoConstants.PARAM_PROJECT_NAME, identity.projectName());
        add(arguments, AppInfoConstants.PARAM_PROJECT_LABEL, encodeBase64(identity.projectLabel()));
        add(arguments, AppInfoConstants.PARAM_INSTANCE_ID, identity.instanceId());
        add(arguments, AppInfoConstants.PARAM_SESSION_ID, identity.sessionId());
        add(arguments, AppInfoConstants.PARAM_SESSION_ORDER, Integer.toString(identity.sessionOrder()));
        add(arguments, AppInfoConstants.PARAM_ATTRIBUTES, encodeBase64(serializeAttributes(identity.attributes())));
        add(arguments, AppInfoConstants.PARAM_PROVISIONED_AT, Long.toString(identity.provisionedAt()));
    }

    private static void add(List<String> arguments, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        arguments.add(key + ASSIGN + value);
    }

    private static String serializeAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return "";
        }
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            pairs.add(attribute.getKey() + AppInfoConstants.ATTRIBUTE_KV_SEPARATOR + attribute.getValue());
        }
        return String.join(AppInfoConstants.ATTRIBUTE_PAIR_SEPARATOR, pairs);
    }

    private static String encodeBase64(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
