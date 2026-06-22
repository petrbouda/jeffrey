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

package cafe.jeffrey.performance.analyst.recommendations;

import cafe.jeffrey.performance.analyst.persistence.GeneratedRecommendation;
import cafe.jeffrey.shared.common.model.Severity;

/**
 * A previously generated recommendation result for one sample event type, returned by the peek endpoint
 * so the UI can restore the artifacts (severity + recommendations + patch) on page load.
 */
public record RecommendationArtifactsResponse(
        String eventType,
        Severity severity,
        String recommendations,
        String patch) {

    public static RecommendationArtifactsResponse from(GeneratedRecommendation stored) {
        return new RecommendationArtifactsResponse(
                stored.eventType(), stored.severity(), stored.recommendations(), stored.patch());
    }
}
