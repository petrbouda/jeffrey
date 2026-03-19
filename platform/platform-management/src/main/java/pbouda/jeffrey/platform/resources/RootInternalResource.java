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

package pbouda.jeffrey.platform.resources;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import pbouda.jeffrey.platform.configuration.ProjectParamsResolver;
import pbouda.jeffrey.platform.manager.qanalysis.QuickAnalysisManager;
import pbouda.jeffrey.platform.manager.SchedulerManager;
import pbouda.jeffrey.platform.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteClients;
import pbouda.jeffrey.platform.project.template.ProjectTemplatesResolver;
import pbouda.jeffrey.platform.queue.PersistentQueue;
import pbouda.jeffrey.profile.resources.ProfileResourceFactory;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.ProfilerRepository;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;

import java.time.Clock;

import static pbouda.jeffrey.platform.configuration.AppConfiguration.GLOBAL_SCHEDULER_MANAGER_BEAN;

@Path("/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RootInternalResource {

    private final SchedulerManager globalSchedulerManager;
    private final RemoteClients.Factory remoteClientsFactory;
    private final ProjectTemplatesResolver projectTemplatesResolver;
    private final CompositeWorkspacesManager workspacesManager;
    private final PersistentQueue<WorkspaceEvent> workspaceEventQueue;
    private final ProfilerRepository profilerRepository;
    private final PlatformRepositories platformRepositories;
    private final ProfileResourceFactory profileResourceFactory;
    private final QuickAnalysisManager quickAnalysisManager;
    private final ProjectParamsResolver projectParamsResolver;
    private final Clock clock;

    @Inject
    public RootInternalResource(
            @Qualifier(GLOBAL_SCHEDULER_MANAGER_BEAN) SchedulerManager globalSchedulerManager,
            RemoteClients.Factory remoteClientsFactory,
            ProjectTemplatesResolver projectTemplatesResolver,
            CompositeWorkspacesManager workspacesManager,
            PersistentQueue<WorkspaceEvent> workspaceEventQueue,
            ProfilerRepository profilerRepository,
            PlatformRepositories platformRepositories,
            ProfileResourceFactory profileResourceFactory,
            QuickAnalysisManager quickAnalysisManager,
            ProjectParamsResolver projectParamsResolver,
            Clock clock) {

        this.globalSchedulerManager = globalSchedulerManager;
        this.remoteClientsFactory = remoteClientsFactory;
        this.projectTemplatesResolver = projectTemplatesResolver;
        this.workspacesManager = workspacesManager;
        this.workspaceEventQueue = workspaceEventQueue;
        this.profilerRepository = profilerRepository;
        this.platformRepositories = platformRepositories;
        this.profileResourceFactory = profileResourceFactory;
        this.quickAnalysisManager = quickAnalysisManager;
        this.projectParamsResolver = projectParamsResolver;
        this.clock = clock;
    }

    @Path("/projects")
    public ProjectsResource projectsResource() {
        return new ProjectsResource(projectTemplatesResolver);
    }

    @Path("/scheduler")
    public SchedulerResource schedulerResource() {
        return new SchedulerResource(globalSchedulerManager);
    }

    @Path("/workspaces")
    public WorkspacesResource workspaceResource() {
        return new WorkspacesResource(
                workspacesManager,
                workspaceEventQueue,
                profileResourceFactory,
                projectParamsResolver,
                clock);
    }

    @Path("/remote-workspaces")
    public RemoteWorkspacesResource remoteWorkspaceResource() {
        return new RemoteWorkspacesResource(remoteClientsFactory, workspacesManager);
    }

    @Path("/profiler")
    public ProfilerResource profilerResource() {
        return new ProfilerResource(profilerRepository);
    }

    @Path("/simulate")
    public SimulateResource simulateResource() {
        return new SimulateResource();
    }

    @Path("/profiles")
    public ProfilesResource profilesResource() {
        return new ProfilesResource(
                workspacesManager,
                quickAnalysisManager,
                platformRepositories,
                profileResourceFactory);
    }

    @Path("/quick-analysis")
    public QuickAnalysisResource quickAnalysisResource() {
        return new QuickAnalysisResource(
                quickAnalysisManager,
                profileResourceFactory);
    }
}
