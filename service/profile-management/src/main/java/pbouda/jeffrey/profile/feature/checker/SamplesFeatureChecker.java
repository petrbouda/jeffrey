/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.feature.checker;

import pbouda.jeffrey.common.model.EventSummary;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.profile.feature.FeatureCheckResult;
import pbouda.jeffrey.profile.feature.FeatureType;

import java.util.List;
import java.util.Map;

public record SamplesFeatureChecker(FeatureType featureType, List<Type> type) implements FeatureChecker {

    public SamplesFeatureChecker(FeatureType featureType, Type type) {
        this(featureType, List.of(type));
    }

    @Override
    public FeatureCheckResult check(Map<Type, EventSummary> eventSummaries) {
        // At least one of the event summaries for the specified types must have samples > 0
        boolean activated = type.stream()
                .map(eventSummaries::get)
                .anyMatch(summary -> summary != null && summary.samples() > 0);

        return new FeatureCheckResult(featureType, activated);
    }
}
