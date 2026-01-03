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

package pbouda.jeffrey.profile.guardian.guard;

import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.profile.common.analysis.marker.Marker;

import java.util.List;

public record GuardVisualization(
        String primaryProfileId,
        Type eventType,
        boolean useWeight,
        boolean withTimeseries,
        Matched matched,
        List<Marker> markers) {

    public static GuardVisualization withTimeseries(
            String profileId, Type eventType, boolean useWeight, Matched matched, List<Marker> markers) {
        return new GuardVisualization(profileId, eventType, useWeight, true, matched, markers);
    }

    public static GuardVisualization withTimeseries(
            String profileId, Type eventType, boolean useWeight, Matched matched, Marker marker) {
        return new GuardVisualization(profileId, eventType, useWeight, true, matched, List.of(marker));
    }
}
