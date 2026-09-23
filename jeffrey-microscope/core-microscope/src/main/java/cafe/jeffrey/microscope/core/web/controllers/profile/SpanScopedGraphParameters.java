/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.web.controllers.profile;

import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.resources.request.SpanFlamegraphOptions;
import cafe.jeffrey.microscope.model.GraphType;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.ProfilingStartEnd;
import cafe.jeffrey.microscope.model.SpanScope;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.microscope.model.time.UndefinedTimeRange;

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
