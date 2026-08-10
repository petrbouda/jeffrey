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

package cafe.jeffrey.hub.core.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.manager.storage.StorageManager;
import cafe.jeffrey.hub.core.manager.storage.StorageManagerImpl;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;

import java.time.Clock;

@Configuration
public class StorageConfiguration {

    @Bean
    public StorageManager storageManager(WorkspacesManager workspacesManager, HubJeffreyDirs jeffreyDirs) {
        return new StorageManagerImpl(workspacesManager, jeffreyDirs);
    }

    @Bean
    public StorageOverviewCache storageOverviewCache(StorageManager storageManager, Clock clock) {
        return new StorageOverviewCache(storageManager, clock);
    }
}
