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

package cafe.jeffrey.shared.common.model.repository;

/**
 * Which source the provisioner's profiler configuration was resolved from.
 * Recorded in {@code .session-info.json} so every session documents where its
 * active async-profiler command came from.
 */
public enum ProfilerSettingsSource {

    /** Explicit {@code profiler-config} in the provisioner's HOCON config */
    CLI_CONFIG,

    /** Hub-pushed workspace settings file, project-level entry */
    HUB_PROJECT,

    /** Hub-pushed workspace settings file, workspace-level default */
    HUB_WORKSPACE,

    /** Hub-pushed workspace settings file, global-level default */
    HUB_GLOBAL,

    /** Built-in provisioner default ({@code CliConstants.DEFAULT_PROFILER_CONFIG}) */
    BUILT_IN
}
