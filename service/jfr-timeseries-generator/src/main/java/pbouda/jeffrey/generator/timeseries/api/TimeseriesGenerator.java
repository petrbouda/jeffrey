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

package pbouda.jeffrey.generator.timeseries.api;

import com.fasterxml.jackson.databind.node.ArrayNode;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.analysis.FramePath;
import pbouda.jeffrey.common.analysis.marker.Marker;

import java.util.List;

/**
 * Generate a data-file for a timeseries graph from a selected event from JFR file.
 */
public interface TimeseriesGenerator {

    /**
     * Generate a data-file for the timeseries graph based on <i>JFR file</i> and selected <i>eventName</>. The result is returned
     * in a Json representation.
     *
     * @param config all information to generate a timeseries representation of the profiling
     * @return timeseries data represented in byte-array format.
     */
    ArrayNode generate(Config config);

    /**
     * Generate a data-file for the timeseries graph based on <i>JFR file</i> and selected <i>eventName</>.
     * The result is returned in a Json representation and timeseries are split into two series
     * based on the provided markers.
     *
     * @param config all information to generate a timeseries representation of the profiling
     * @param markers list of markers that split the timeseries into two series
     * @return timeseries data represented in byte-array format.
     */
    ArrayNode generate(Config config, List<Marker> markers);
}
