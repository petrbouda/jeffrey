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

package cafe.jeffrey.microscope.grpc.client;

import cafe.jeffrey.shared.common.activity.ActivityLimits;

/** One scan, addressed by its full scope. A scan ID alone would read across sessions. */
public record ActivityScanTarget(
        String workspaceId,
        String projectId,
        String sessionId,
        String scanId) {

    public ActivityScanTarget {
        for (String id : new String[]{workspaceId, projectId, sessionId, scanId}) {
            if (id == null || id.isBlank() || id.length() > ActivityLimits.MAX_ID_LENGTH) {
                throw new IllegalArgumentException("A nonempty ID of at most "
                        + ActivityLimits.MAX_ID_LENGTH + " characters is required");
            }
        }
    }
}
