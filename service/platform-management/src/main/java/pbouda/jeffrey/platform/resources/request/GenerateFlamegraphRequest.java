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

package pbouda.jeffrey.platform.resources.request;

import pbouda.jeffrey.platform.TimeRangeRequest;
import pbouda.jeffrey.shared.model.ThreadInfo;
import pbouda.jeffrey.shared.model.Type;
import pbouda.jeffrey.profile.common.analysis.marker.Marker;
import pbouda.jeffrey.profile.common.config.GraphComponents;

import java.util.List;

public record GenerateFlamegraphRequest(
        String flamegraphName,
        Type eventType,
        TimeRangeRequest timeRange,
        String search,
        boolean useThreadMode,
        Boolean useWeight,
        boolean excludeNonJavaSamples,
        boolean excludeIdleSamples,
        boolean onlyUnsafeAllocationSamples,
        ThreadInfo threadInfo,
        GraphComponents components,
        List<Marker> markers) {
}
