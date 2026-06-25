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

package cafe.jeffrey.shared.common;

/**
 * Shared constants for the {@code jeffrey.AppInformation} agent arguments.
 * Used by the Provisioner (which builds the {@code -javaagent} option) and by
 * tests. The agent module duplicates the relevant subset of these constants
 * because it must remain zero-dependency for minimal JAR size.
 *
 * <p>Identity is passed to the agent as comma-separated {@code key=value} pairs.
 * Because the agent argument string is split on {@code ','}, the two free-form
 * values that may legitimately contain delimiters — the project label and the
 * serialized attributes — are Base64-encoded by the producer and decoded by the
 * agent. All other values (ids, names, numbers) are delimiter-safe and passed
 * verbatim.</p>
 */
public abstract class AppInfoConstants {

    /** Agent argument key for the workspace reference id */
    public static final String PARAM_WORKSPACE_ID = "app.workspaceId";

    /** Agent argument key for the project id (UUID) */
    public static final String PARAM_PROJECT_ID = "app.projectId";

    /** Agent argument key for the machine-friendly project name */
    public static final String PARAM_PROJECT_NAME = "app.projectName";

    /** Agent argument key for the human-readable project label (Base64-encoded) */
    public static final String PARAM_PROJECT_LABEL = "app.projectLabel";

    /** Agent argument key for the instance id (typically host/pod name) */
    public static final String PARAM_INSTANCE_ID = "app.instanceId";

    /** Agent argument key for the session id (UUID) */
    public static final String PARAM_SESSION_ID = "app.sessionId";

    /** Agent argument key for the sequential session order within the instance */
    public static final String PARAM_SESSION_ORDER = "app.sessionOrder";

    /** Agent argument key for the serialized custom attributes (Base64-encoded) */
    public static final String PARAM_ATTRIBUTES = "app.attributes";

    /** Agent argument key for the provisioning time (epoch millis) */
    public static final String PARAM_PROVISIONED_AT = "app.provisionedAt";

    /** Separator between serialized attribute pairs ({@code key=value;key=value}) */
    public static final String ATTRIBUTE_PAIR_SEPARATOR = ";";

    /** Separator between an attribute key and its value */
    public static final String ATTRIBUTE_KV_SEPARATOR = "=";

    private AppInfoConstants() {
    }
}
