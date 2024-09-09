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

package pbouda.jeffrey.guardian.guard;

import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.analysis.marker.Marker;

import java.math.BigDecimal;
import java.util.List;

public record GuardVisualization(
        String primaryProfileId,
        Type eventType,
        boolean withTimeseries,
        BigDecimal matchedInPercent,
        List<Marker> markers) {

    public static GuardVisualization withTimeseries(
            String profileId, Type eventType, BigDecimal matchedInPercent, List<Marker> markers) {
        return new GuardVisualization(profileId, eventType, true, matchedInPercent, markers);
    }

    public static GuardVisualization withTimeseries(
            String profileId, Type eventType, BigDecimal matchedInPercent, Marker marker) {
        return new GuardVisualization(profileId, eventType, true, matchedInPercent, List.of(marker));
    }
}
