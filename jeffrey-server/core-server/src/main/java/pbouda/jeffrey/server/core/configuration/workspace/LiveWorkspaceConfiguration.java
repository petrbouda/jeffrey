/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.server.core.configuration.workspace;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.server.core.manager.project.ProjectsManager;
import pbouda.jeffrey.server.core.manager.workspace.LiveWorkspacesManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.server.core.manager.workspace.LiveWorkspaceManager;
import pbouda.jeffrey.shared.persistentqueue.DuckDBPersistentQueue;
import pbouda.jeffrey.shared.persistentqueue.PersistentQueue;
import pbouda.jeffrey.server.core.workspace.QueueWorkspaceEventPublisher;
import pbouda.jeffrey.server.core.workspace.QueueWorkspaceEventReader;
import pbouda.jeffrey.server.core.workspace.WorkspaceEventPublisher;
import pbouda.jeffrey.server.core.workspace.WorkspaceEventReader;
import pbouda.jeffrey.server.core.workspace.WorkspaceEventSerializer;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.server.persistence.repository.WorkspaceRepository;
import pbouda.jeffrey.server.core.ServerJeffreyDirs;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Clock;

@Configuration
public class LiveWorkspaceConfiguration {

    private static final String WORKSPACE_EVENTS_QUEUE = "workspace_events";

    public static final String LIVE_WORKSPACE_TYPE = "LIVE_WORKSPACE_FACTORY_TYPE";

    @Bean
    public PersistentQueue<WorkspaceEvent> workspaceEventQueue(
            DatabaseClientProvider databaseClientProvider,
            Clock applicationClock) {

        return new DuckDBPersistentQueue<>(
                databaseClientProvider, WORKSPACE_EVENTS_QUEUE, new WorkspaceEventSerializer(), applicationClock);
    }

    @Bean
    public WorkspaceEventPublisher workspaceEventPublisher(
            PersistentQueue<WorkspaceEvent> workspaceEventQueue) {
        return new QueueWorkspaceEventPublisher(workspaceEventQueue);
    }

    @Bean
    public WorkspaceEventReader workspaceEventReader(
            PersistentQueue<WorkspaceEvent> workspaceEventQueue) {
        return new QueueWorkspaceEventReader(workspaceEventQueue);
    }

    @Bean(LIVE_WORKSPACE_TYPE)
    public WorkspaceManager.Factory workspaceManagerFactory(
            Clock applicationClock,
            ServerJeffreyDirs jeffreyDirs,
            ServerPlatformRepositories platformRepositories,
            @Qualifier(ServerWorkspaceConfiguration.COMMON_PROJECTS_TYPE) ProjectsManager.Factory projectsManagerFactory) {

        return workspaceInfo -> {
            WorkspaceRepository workspaceRepository = platformRepositories.newWorkspaceRepository(workspaceInfo.id());
            return new LiveWorkspaceManager(
                    applicationClock, jeffreyDirs, workspaceInfo, workspaceRepository, projectsManagerFactory);
        };
    }

    @Bean
    public LiveWorkspacesManager liveWorkspaceManager(
            Clock applicationClock,
            ServerPlatformRepositories platformRepositories,
            @Qualifier(LIVE_WORKSPACE_TYPE) WorkspaceManager.Factory workspaceManagerFactory) {

        return new LiveWorkspacesManager(applicationClock, platformRepositories.newWorkspacesRepository(), workspaceManagerFactory);
    }
}
