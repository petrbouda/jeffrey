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


package cafe.jeffrey.microscope.core.manager;

import cafe.jeffrey.microscope.model.config.ConfigEntry;
import cafe.jeffrey.shared.common.config.ConfigType;

import java.util.List;

/**
 * The configuration the hub holds for one project.
 *
 * <p>Project scope only. What the project inherits is read through its workspace, which is the
 * side that knows about the scopes above it.</p>
 */
public interface ScopedConfigManager {

    /** What this project's own scope holds, empty when it holds nothing. */
    List<ConfigEntry> find();

    /** Stores one value at this project's scope and republishes its file. */
    List<ConfigEntry> upsert(ConfigType type, String value);

    /** Removes one value from this project's scope. */
    List<ConfigEntry> delete(ConfigType type);
}
