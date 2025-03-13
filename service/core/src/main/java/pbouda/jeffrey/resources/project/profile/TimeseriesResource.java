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

package pbouda.jeffrey.resources.project.profile;

import jakarta.ws.rs.POST;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.manager.TimeseriesManager;
import pbouda.jeffrey.resources.request.GenerateTimeseriesRequest;
import pbouda.jeffrey.timeseries.TimeseriesData;

public class TimeseriesResource {

    private final TimeseriesManager timeseriesManager;

    public TimeseriesResource(TimeseriesManager timeseriesManager) {
        this.timeseriesManager = timeseriesManager;
    }

    @POST
    public TimeseriesData generate(GenerateTimeseriesRequest request) {
        return timeseriesManager.timeseries(mapToGenerateRequest(request));
    }

    private static TimeseriesManager.Generate mapToGenerateRequest(GenerateTimeseriesRequest request) {
        GraphParameters graphParameters = GraphParameters.builder()
                .withSearchPattern(request.search())
                .withUseWeight(request.useWeight())
                .withExcludeNonJavaSamples(request.excludeNonJavaSamples())
                .withExcludeIdleSamples(request.excludeIdleSamples())
                .withOnlyUnsafeAllocationSamples(request.onlyUnsafeAllocationSamples())
                .withMarkers(request.markers())
                .build();

        return new TimeseriesManager.Generate(
                request.eventType(),
                graphParameters,
                request.threadInfo());
    }
}
