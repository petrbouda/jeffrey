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

package pbouda.jeffrey.platform.resources.pub;

/**
 * Single source of truth for all public API path templates used by remote clients.
 * These paths correspond to the Jersey resource tree under {@code /api/public/}.
 */
public abstract class PublicApiPaths {

    private static final String BASE = "/api/public";

    // Info
    public static final String INFO = BASE + "/info";

    // Workspaces
    public static final String WORKSPACES = BASE + "/workspaces";
    public static final String WORKSPACE = WORKSPACES + "/{workspaceId}";

    // Projects
    public static final String PROJECTS = WORKSPACE + "/projects";

    // Repository
    public static final String REPOSITORY_STATISTICS = PROJECTS + "/{projectId}/repository/statistics";
    public static final String SESSIONS = PROJECTS + "/{projectId}/repository/sessions";
    public static final String SESSION = SESSIONS + "/{sessionId}";

    // Downloads
    public static final String SESSION_RECORDINGS = SESSION + "/recordings";
    public static final String SESSION_ARTIFACT = SESSION + "/artifact";

    // Profiler settings
    public static final String PROFILER_SETTINGS = PROJECTS + "/{projectId}/profiler/settings";

    // Messages
    public static final String MESSAGES = PROJECTS + "/{projectId}/messages";
    public static final String ALERTS = MESSAGES + "/alerts";

    // Instances
    public static final String INSTANCES = PROJECTS + "/{projectId}/instances";
    public static final String INSTANCE = INSTANCES + "/{instanceId}";
    public static final String INSTANCE_SESSIONS = INSTANCE + "/sessions";
}
