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

import cafe.jeffrey.shared.common.model.Severity;

/**
 * The artifacts an AI recommendation run produces: the overall {@code severity} the model graded for the
 * profile's findings (used to prioritize across recordings), the human-readable {@code recommendations}
 * markdown (analysis + rationale, no diffs) and an applicable {@code patch} (a single unified diff that
 * {@code git apply} can consume). {@code patch} is {@code null} when the model proposed no concrete code
 * edits.
 */
public record RecommendationResult(Severity severity, String recommendations, String patch) {

    public boolean hasPatch() {
        return patch != null && !patch.isBlank();
    }
}
