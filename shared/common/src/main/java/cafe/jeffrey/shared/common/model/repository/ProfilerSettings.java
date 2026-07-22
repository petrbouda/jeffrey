/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import java.util.Map;

/**
 * Profiler settings bundle uploaded by the hub into a workspace's
 * {@code .settings/} directory and consumed by the provisioner on the next
 * JVM launch.
 *
 * <p>Per-project settings are published under two keys: {@code projectSettingsById}
 * keyed by the origin project id (the id the provisioner persisted in
 * {@code .project-info.json}) and {@code projectSettings} keyed by project name.
 * The id-keyed map is authoritative — names are mutable and can collide — while
 * the name-keyed map keeps old provisioners working. Either map may be missing
 * when reading files written by an older counterpart.</p>
 */
public record ProfilerSettings(
        String defaultSettings,
        String defaultSettingsLevel,
        Map<String, String> projectSettings,
        Map<String, String> projectSettingsById) {
}
