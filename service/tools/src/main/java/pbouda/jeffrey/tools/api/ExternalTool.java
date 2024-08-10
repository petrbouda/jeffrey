/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.tools.api;

import java.nio.file.Path;

/**
 * An interface holding information about external tools used in Jeffrey.
 * e.g. `jfr` command from OpenJDK
 */
public interface ExternalTool {

    /**
     * Should be called after creating an external tool to initialize an internal state
     * and resolve whether the tool is `enabled` and resolve its `path`.
     *
     */
    void initialize();

    /**
     * A tool is enabled. It means that it's enabled by configuration and the path
     * was checked by "smoked test" that the tool is available.
     *
     * @return true if the tool is enabled and available.
     */
    boolean enabled();

    /**
     * Resolved path to the tool. The path of the tool can be configured in the configuration,
     * or resolved using best-effort hard-coded `hacks`.
     *
     * @return resolved path to execute the external tool.
     */
    Path path();

}
