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

/**
 * Identifies what a recommendation run targets: the remote hub/workspace/project it belongs to (with the
 * denormalized {@code projectName} the frontend supplies, so the global Overview can label/deep-link
 * without resolving the hub), the recording, the sample event type to analyze, and the commit the
 * recording came from.
 *
 * <p>{@code commitRef} is null when the recording carries no build metadata. That is a meaningful
 * absence, not a default: without it the analysis reads whatever is on the default branch today, which
 * may not be the code that produced the profile — so it is surfaced rather than hidden.</p>
 */
public record RecommendationTarget(
        String hubId,
        String workspaceId,
        String projectId,
        String projectName,
        String recordingId,
        String eventType,
        String commitRef) {

    public boolean hasCommitRef() {
        return commitRef != null && !commitRef.isBlank();
    }
}
