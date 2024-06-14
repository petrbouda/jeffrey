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

package pbouda.jeffrey.generator.heatmap.api;

import pbouda.jeffrey.generator.heatmap.HeatmapConfig;

/**
 * Generate a data-file for a heatmap from a selected event from JFR file.
 */
public interface HeatmapGenerator {

    /**
     * Generate a data-file for the heatmap base on <i>JFR file</i> and selected <i>eventName</>. The result is returned
     * in a byte-array representation.
     *
     * @param config all information to generate a heatmap representation of the profiling
     * @return heatmap data represented in byte-array format.
     */
    byte[] generate(HeatmapConfig config);
}
