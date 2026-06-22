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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.performance.analyst.persistence.Platform;
import cafe.jeffrey.performance.analyst.persistence.VersionControlSystem;
import cafe.jeffrey.performance.analyst.persistence.VersionControlSystemStore;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Reads and writes a project's {@link VersionControlSystem} integration. Credentials arrive as a single access
 * token, are wrapped into a platform-specific JSON blob, and are encrypted at the persistence boundary.
 * When a save omits the token, the previously stored credentials are preserved.
 */
public class VersionControlSystemManager {

    private static final Logger LOG = LoggerFactory.getLogger(VersionControlSystemManager.class);

    private final VersionControlSystemStore versionControlSystemStore;
    private final Clock clock;

    public VersionControlSystemManager(VersionControlSystemStore versionControlSystemStore, Clock clock) {
        this.versionControlSystemStore = versionControlSystemStore;
        this.clock = clock;
    }

    public Optional<VersionControlSystem> find(String projectId) {
        return versionControlSystemStore.findByProject(projectId);
    }

    public VersionControlSystem save(String projectId, String platformCode, String url, String token) {
        if (url == null || url.isBlank()) {
            throw Exceptions.invalidRequest("Repository URL must not be blank");
        }

        Platform platform = parsePlatform(platformCode);
        Optional<VersionControlSystem> existing = versionControlSystemStore.findByProject(projectId);
        Instant now = clock.instant();

        VersionControlSystem versionControlSystem = new VersionControlSystem(
                existing.map(VersionControlSystem::id).orElseGet(IDGenerator::generate),
                projectId,
                platform,
                url,
                resolveCredentials(token, existing),
                existing.map(VersionControlSystem::createdAt).orElse(now),
                now);

        versionControlSystemStore.upsert(versionControlSystem);
        LOG.info("Saved version control system: project_id={} platform={} has_credentials={}",
                projectId, platform.code(), versionControlSystem.hasCredentials());
        return versionControlSystem;
    }

    private String resolveCredentials(String token, Optional<VersionControlSystem> existing) {
        if (token != null && !token.isBlank()) {
            return Json.toString(new VersionControlSystemCredentials(token));
        }
        // No new token supplied — keep whatever was previously stored (URL-only edits keep the token).
        return existing.map(VersionControlSystem::credentials).orElse(null);
    }

    private Platform parsePlatform(String platformCode) {
        try {
            return Platform.fromCode(platformCode);
        } catch (IllegalArgumentException e) {
            throw Exceptions.invalidRequest("Unsupported version-control-system platform: " + platformCode);
        }
    }
}
