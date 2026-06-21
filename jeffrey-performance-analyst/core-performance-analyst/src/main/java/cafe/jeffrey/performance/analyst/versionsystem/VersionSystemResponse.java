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

package cafe.jeffrey.performance.analyst.versionsystem;

import cafe.jeffrey.performance.analyst.persistence.VersionSystem;

/**
 * Response describing a project's version-system integration. The raw access token is never returned —
 * only a {@code hasCredentials} flag indicating whether one is stored.
 */
public record VersionSystemResponse(boolean configured, String platform, String url, boolean hasCredentials) {

    public static VersionSystemResponse empty() {
        return new VersionSystemResponse(false, null, null, false);
    }

    public static VersionSystemResponse of(VersionSystem versionSystem) {
        return new VersionSystemResponse(
                true,
                versionSystem.platform().code(),
                versionSystem.url(),
                versionSystem.hasCredentials());
    }
}
