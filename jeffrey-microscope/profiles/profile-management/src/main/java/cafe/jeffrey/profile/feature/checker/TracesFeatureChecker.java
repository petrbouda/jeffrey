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

package cafe.jeffrey.profile.feature.checker;

import cafe.jeffrey.profile.feature.FeatureCheckResult;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.provider.profile.api.TraceRepository;
import cafe.jeffrey.microscope.model.EventSummary;
import cafe.jeffrey.microscope.model.Type;

import java.util.Map;

/**
 * Enables the Traces section when the profile actually contains a trace.
 * <p>
 * Deliberately asks the repository rather than counting event samples. Counting
 * {@code jeffrey.TraceSpan} alone would miss a trace made entirely of instrumented HTTP and JDBC
 * events, while counting those event types would light the section up for every older recording
 * whose events carry no trace ids at all. The derived table already answers the question exactly.
 */
public class TracesFeatureChecker implements FeatureChecker {

    private final TraceRepository traceRepository;

    public TracesFeatureChecker(TraceRepository traceRepository) {
        this.traceRepository = traceRepository;
    }

    @Override
    public FeatureCheckResult check(Map<Type, EventSummary> eventSummaries) {
        return traceRepository.hasTraces()
                ? FeatureCheckResult.enabled(FeatureType.TRACES)
                : FeatureCheckResult.disabled(FeatureType.TRACES);
    }
}
