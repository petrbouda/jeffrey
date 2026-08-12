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

package cafe.jeffrey.profile.feature.checker;

import cafe.jeffrey.profile.feature.FeatureCheckResult;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.provider.profile.api.TraceRepository;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.Type;

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
