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

package pbouda.jeffrey.generator.subsecond.db.api;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.generator.subsecond.db.SubSecondConfig;

/**
 * Generate a data-file for a sub-second Graph from a selected event from JFR file.
 */
public interface SubSecondGenerator {

    /**
     * Generate a data-file for the sub-second base on <i>JFR file</i> and selected <i>eventName</>. The result is returned
     * in a byte-array representation.
     *
     * @param config all information to generate a sub-second representation of the profiling
     * @return sub-second graph data represented in byte-array format.
     */
    JsonNode generate(SubSecondConfig config);
}
