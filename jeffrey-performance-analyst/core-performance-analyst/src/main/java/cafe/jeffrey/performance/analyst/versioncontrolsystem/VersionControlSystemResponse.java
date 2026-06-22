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

package cafe.jeffrey.performance.analyst.versioncontrolsystem;

import cafe.jeffrey.performance.analyst.persistence.VersionControlSystem;

/**
 * Response describing a project's version-control-system integration. The raw access token is never returned —
 * only a {@code hasCredentials} flag indicating whether one is stored.
 */
public record VersionControlSystemResponse(boolean configured, String platform, String url, boolean hasCredentials) {

    public static VersionControlSystemResponse empty() {
        return new VersionControlSystemResponse(false, null, null, false);
    }

    public static VersionControlSystemResponse of(VersionControlSystem versionControlSystem) {
        return new VersionControlSystemResponse(
                true,
                versionControlSystem.platform().code(),
                versionControlSystem.url(),
                versionControlSystem.hasCredentials());
    }
}
