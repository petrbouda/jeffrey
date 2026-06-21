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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.performance.analyst.persistence.Platform;
import cafe.jeffrey.performance.analyst.persistence.VersionSystem;
import cafe.jeffrey.performance.analyst.persistence.VersionSystemStore;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Reads and writes a project's {@link VersionSystem} integration. Credentials arrive as a single access
 * token, are wrapped into a platform-specific JSON blob, and are encrypted at the persistence boundary.
 * When a save omits the token, the previously stored credentials are preserved.
 */
public class VersionSystemManager {

    private static final Logger LOG = LoggerFactory.getLogger(VersionSystemManager.class);

    private final VersionSystemStore versionSystemStore;
    private final Clock clock;

    public VersionSystemManager(VersionSystemStore versionSystemStore, Clock clock) {
        this.versionSystemStore = versionSystemStore;
        this.clock = clock;
    }

    public Optional<VersionSystem> find(String projectId) {
        return versionSystemStore.findByProject(projectId);
    }

    public VersionSystem save(String projectId, String platformCode, String url, String token) {
        if (url == null || url.isBlank()) {
            throw Exceptions.invalidRequest("Repository URL must not be blank");
        }

        Platform platform = parsePlatform(platformCode);
        Optional<VersionSystem> existing = versionSystemStore.findByProject(projectId);
        Instant now = clock.instant();

        VersionSystem versionSystem = new VersionSystem(
                existing.map(VersionSystem::id).orElseGet(IDGenerator::generate),
                projectId,
                platform,
                url,
                resolveCredentials(token, existing),
                existing.map(VersionSystem::createdAt).orElse(now),
                now);

        versionSystemStore.upsert(versionSystem);
        LOG.info("Saved version system: project_id={} platform={} has_credentials={}",
                projectId, platform.code(), versionSystem.hasCredentials());
        return versionSystem;
    }

    private String resolveCredentials(String token, Optional<VersionSystem> existing) {
        if (token != null && !token.isBlank()) {
            return Json.toString(new VersionSystemCredentials(token));
        }
        // No new token supplied — keep whatever was previously stored (URL-only edits keep the token).
        return existing.map(VersionSystem::credentials).orElse(null);
    }

    private Platform parsePlatform(String platformCode) {
        try {
            return Platform.fromCode(platformCode);
        } catch (IllegalArgumentException e) {
            throw Exceptions.invalidRequest("Unsupported version-system platform: " + platformCode);
        }
    }
}
