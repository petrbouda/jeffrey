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

package pbouda.jeffrey.platform.configuration.workspace;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.manager.workspace.LiveWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.manager.workspace.live.LiveWorkspaceManager;
import pbouda.jeffrey.platform.queue.DuckDBPersistentQueue;
import pbouda.jeffrey.platform.queue.PersistentQueue;
import pbouda.jeffrey.platform.workspace.WorkspaceEventSerializer;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.WorkspaceRepository;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Clock;
import java.util.function.Function;

@Configuration
public class LiveWorkspaceConfiguration {

    private static final String WORKSPACE_EVENTS_QUEUE = "workspace_events";

    public static final String LIVE_WORKSPACE_TYPE = "LIVE_WORKSPACE_FACTORY_TYPE";

    @Bean(LIVE_WORKSPACE_TYPE)
    public WorkspaceManager.Factory workspaceManagerFactory(
            Clock applicationClock,
            JeffreyDirs jeffreyDirs,
            PlatformRepositories platformRepositories,
            DatabaseClientProvider databaseClientProvider,
            @Qualifier(WorkspaceConfiguration.COMMON_PROJECTS_TYPE) ProjectsManager.Factory projectsManagerFactory) {

        WorkspaceEventSerializer serializer = new WorkspaceEventSerializer();
        Function<String, PersistentQueue<WorkspaceEvent>> queueFactory = scopeId ->
                new DuckDBPersistentQueue<>(databaseClientProvider, WORKSPACE_EVENTS_QUEUE, scopeId, serializer, applicationClock);

        return workspaceInfo -> {
            WorkspaceRepository workspaceRepository = platformRepositories.newWorkspaceRepository(workspaceInfo.id());
            return new LiveWorkspaceManager(
                    applicationClock, jeffreyDirs, workspaceInfo, workspaceRepository, queueFactory, projectsManagerFactory);
        };
    }

    @Bean
    public LiveWorkspacesManager liveWorkspaceManager(
            PlatformRepositories platformRepositories,
            @Qualifier(LIVE_WORKSPACE_TYPE) WorkspaceManager.Factory workspaceManagerFactory) {

        return new LiveWorkspacesManager(platformRepositories.newWorkspacesRepository(), workspaceManagerFactory);
    }
}
