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

package cafe.jeffrey.performance.analyst.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.performance.analyst.persistence.VersionSystemStore;
import cafe.jeffrey.performance.analyst.versionsystem.VersionSystemManager;

import java.time.Clock;

/**
 * Wiring for the per-project version-control integration (GitHub/GitLab): registering the platform,
 * repository URL, and (encrypted) credentials. Cloning the repository is a separate, later concern.
 */
@Configuration
public class VersionSystemConfiguration {

    @Bean
    public VersionSystemManager versionSystemManager(VersionSystemStore versionSystemStore, Clock clock) {
        return new VersionSystemManager(versionSystemStore, clock);
    }
}
