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

package cafe.jeffrey.performance.analyst.regression;

import java.util.List;

/**
 * What changed between two recordings of the same profile.
 *
 * <p>Improvements are reported alongside regressions on purpose. A view that only showed regressions
 * would read as though the tool were hiding good news, and confirming that a fix actually moved the
 * profile is the single most useful thing a comparison can tell a team that just shipped one.</p>
 *
 * @param eventType   the profile being compared
 * @param regressed   frames that grew by at least the threshold, heaviest increase first
 * @param improved    frames that shrank by at least the threshold, biggest improvement first
 * @param thresholdPp the percentage-point move a frame had to make to appear at all
 */
public record ProfileComparison(
        String eventType,
        List<FrameDelta> regressed,
        List<FrameDelta> improved,
        double thresholdPp) {

    public ProfileComparison {
        regressed = regressed == null ? List.of() : List.copyOf(regressed);
        improved = improved == null ? List.of() : List.copyOf(improved);
    }

    public boolean hasRegressions() {
        return !regressed.isEmpty();
    }
}
