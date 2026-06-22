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

import cafe.jeffrey.performance.analyst.persistence.TopSeverityRecommendation;
import cafe.jeffrey.shared.common.model.Severity;

/**
 * One row of the Overview "Highest Impact" list: a recording, its worst severity, a short dominant-hotspot
 * {@code headline} derived from the recommendation, and the hub/workspace/project identity needed to
 * deep-link to it.
 */
public record TopSeverityResponse(
        String recordingId,
        String recordingName,
        String hubId,
        String workspaceId,
        String projectId,
        String projectName,
        Severity severity,
        String headline,
        long generatedAt) {

    public static TopSeverityResponse from(TopSeverityRecommendation row, String headline) {
        return new TopSeverityResponse(
                row.recordingId(),
                row.recordingName(),
                row.hubId(),
                row.workspaceId(),
                row.projectId(),
                row.projectName(),
                row.severity(),
                headline,
                row.generatedAt().toEpochMilli());
    }
}
