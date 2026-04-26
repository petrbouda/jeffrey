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

package cafe.jeffrey.server.core.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.server.core.project.repository.RepositoryStorage;
import cafe.jeffrey.server.core.streaming.SessionFinisher;
import cafe.jeffrey.server.core.workspace.consumer.*;
import cafe.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import cafe.jeffrey.server.core.ServerJeffreyDirs;

import java.time.Clock;

@Configuration
public class WorkspaceEventConsumerConfiguration {

    @Bean
    public CreateProjectWorkspaceEventConsumer createProjectWorkspaceEventConsumer() {
        return new CreateProjectWorkspaceEventConsumer();
    }

    @Bean
    public InstanceCreatedWorkspaceEventConsumer instanceCreatedWorkspaceEventConsumer() {
        return new InstanceCreatedWorkspaceEventConsumer();
    }

    @Bean
    public CreateSessionWorkspaceEventConsumer createSessionWorkspaceEventConsumer(
            ServerPlatformRepositories platformRepositories,
            ServerJeffreyDirs jeffreyDirs,
            SessionFinisher sessionFinisher) {

        return new CreateSessionWorkspaceEventConsumer(platformRepositories, jeffreyDirs, sessionFinisher);
    }

    @Bean
    public DeleteSessionWorkspaceEventConsumer deleteSessionWorkspaceEventConsumer(
            ServerPlatformRepositories platformRepositories,
            RepositoryStorage.Factory remoteRepositoryStorageFactory,
            Clock clock) {

        return new DeleteSessionWorkspaceEventConsumer(platformRepositories, remoteRepositoryStorageFactory, clock);
    }

    @Bean
    public DeleteProjectWorkspaceEventConsumer deleteProjectWorkspaceEventConsumer(
            ServerPlatformRepositories platformRepositories,
            RepositoryStorage.Factory remoteRepositoryStorageFactory) {

        return new DeleteProjectWorkspaceEventConsumer(platformRepositories, remoteRepositoryStorageFactory);
    }
}
