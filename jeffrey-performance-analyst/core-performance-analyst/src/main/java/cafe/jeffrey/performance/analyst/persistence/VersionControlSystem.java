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

package cafe.jeffrey.performance.analyst.persistence;

import java.time.Instant;

/**
 * A project's version-control integration: the {@link Platform}, the repository URL, and an optional
 * platform-specific credentials JSON blob (e.g. an access token). One per project.
 *
 * <p>{@code credentials} is the <em>decrypted</em> JSON in this domain object; encryption at rest is
 * handled at the persistence boundary (see {@code JdbcVersionControlSystemStore}).</p>
 */
public record VersionControlSystem(
        String id,
        String projectId,
        Platform platform,
        String url,
        String credentials,
        Instant createdAt,
        Instant modifiedAt) {

    public boolean hasCredentials() {
        return credentials != null && !credentials.isBlank();
    }
}
