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

package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.node.ArrayNode;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.time.Instant;
import java.util.function.Function;

public interface TimeseriesManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, TimeseriesManager> {
    }

    /**
     * Generates a whole timeseries for the given event-type.
     *
     * @param eventType type of the samples in the timeseries
     * @return time + samples in the array format compatible with timeseries graphs
     */
    ArrayNode contentByEventType(Type eventType);

    /**
     * Generates a timeseries for the given event-type bounded by the interval.
     *
     * @param eventType type of the samples in the timeseries
     * @param start     start of the interval for generated output.
     * @param end       enf of the interval for generated output.
     * @return time + samples in the array format compatible with timeseries graphs
     */
    ArrayNode contentByEventType(Type eventType, Instant start, Instant end);
}
