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

package cafe.jeffrey.profile.manager.builder;

import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.resources.request.SpanFlamegraphOptions;
import cafe.jeffrey.shared.common.GraphType;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ProfilingStartEnd;
import cafe.jeffrey.shared.common.model.SpanScope;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.shared.common.model.time.UndefinedTimeRange;

/**
 * Builds the {@link GraphParameters} for a flamegraph narrowed to a {@link SpanScope}.
 * <p>
 * Lives on its own rather than on either controller because two unrelated features ask for the same
 * graph: async-profiler spans, which are flat and selected by tag, and trace spans, which are
 * nested and selected from a tree. Only the rendering options and the scope are common to both,
 * and neither feature should have to import the other to reach them.
 */
public abstract class SpanScopedGraphParameters {

    private SpanScopedGraphParameters() {
    }

    public static GraphParameters of(
            ProfileInfo profileInfo, SpanFlamegraphOptions request, SpanScope scope) {

        // Full-profile range so the timeseries can bucket over the whole timeline; the scope (not
        // the time range) is what narrows the samples, so a null range would NPE the timeseries
        // init.
        ProfilingStartEnd primaryStartEnd = new ProfilingStartEnd(
                profileInfo.profilingStartedAt(), profileInfo.profilingFinishedAt());
        RelativeTimeRange fullRange = UndefinedTimeRange.INSTANCE.toRelativeTimeRange(primaryStartEnd);

        return GraphParameters.builder()
                .withEventType(request.eventType())
                .withTimeRange(fullRange)
                .withThreadMode(request.useThreadMode())
                .withUseWeight(request.useWeight())
                .withExcludeNonJavaSamples(request.excludeNonJavaSamples())
                .withExcludeIdleSamples(request.excludeIdleSamples())
                .withOnlyUnsafeAllocationSamples(request.onlyUnsafeAllocationSamples())
                .withParseLocation(true)
                .withGraphType(GraphType.PRIMARY)
                .withGraphComponents(request.components())
                .withSpanScope(scope)
                .build();
    }
}
