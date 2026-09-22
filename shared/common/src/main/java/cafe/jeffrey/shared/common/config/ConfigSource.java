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


package cafe.jeffrey.shared.common.config;

/**
 * Which configuration layer supplied a session's profiler command. Recorded in
 * {@code .session-info.json} so every session documents where its active command came from.
 */
public enum ConfigSource {

    /** A layer inside the container: the image's base config, a mounted override, or the environment */
    CONTAINER,

    /** A hub-published file in the workspaces root */
    HUB_GLOBAL,

    /** A hub-published file in the workspace's folder */
    HUB_WORKSPACE,

    /** A hub-published file in the project's folder */
    HUB_PROJECT,

    /** Nothing set it, so the provisioner's built-in default applied */
    BUILT_IN;

    /** The source naming a hub-published file at the given scope */
    public static ConfigSource ofScope(ConfigScope scope) {
        return switch (scope) {
            case GLOBAL -> HUB_GLOBAL;
            case WORKSPACE -> HUB_WORKSPACE;
            case PROJECT -> HUB_PROJECT;
        };
    }
}
