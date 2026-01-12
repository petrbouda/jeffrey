/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
import pbouda.jeffrey.platform.manager.SchedulerManager;
import pbouda.jeffrey.platform.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteWorkspaceClient;
import pbouda.jeffrey.platform.project.template.ProjectTemplatesResolver;
import pbouda.jeffrey.profile.ai.service.HeapDumpContextExtractor;
import pbouda.jeffrey.profile.ai.service.OqlAssistantService;
import pbouda.jeffrey.provider.platform.repository.ProfilerRepository;

import static pbouda.jeffrey.platform.configuration.AppConfiguration.GLOBAL_SCHEDULER_MANAGER_BEAN;

@Path("/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RootInternalResource {

    private final SchedulerManager globalSchedulerManager;
    private final RemoteWorkspaceClient.Factory remoteWorkspacesManagerFactory;
    private final ProjectTemplatesResolver projectTemplatesResolver;
    private final CompositeWorkspacesManager workspacesManager;
    private final ProfilerRepository profilerRepository;
    private final OqlAssistantService oqlAssistantService;
    private final HeapDumpContextExtractor heapDumpContextExtractor;

    @Inject
    public RootInternalResource(
            @Qualifier(GLOBAL_SCHEDULER_MANAGER_BEAN) SchedulerManager globalSchedulerManager,
            RemoteWorkspaceClient.Factory remoteWorkspacesManagerFactory,
            ProjectTemplatesResolver projectTemplatesResolver,
            CompositeWorkspacesManager workspacesManager,
            ProfilerRepository profilerRepository,
            OqlAssistantService oqlAssistantService,
            HeapDumpContextExtractor heapDumpContextExtractor) {

        this.globalSchedulerManager = globalSchedulerManager;
        this.remoteWorkspacesManagerFactory = remoteWorkspacesManagerFactory;
        this.projectTemplatesResolver = projectTemplatesResolver;
        this.workspacesManager = workspacesManager;
        this.profilerRepository = profilerRepository;
        this.oqlAssistantService = oqlAssistantService;
        this.heapDumpContextExtractor = heapDumpContextExtractor;
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
                oqlAssistantService,
                heapDumpContextExtractor);
    }

    @Path("/remote-workspaces")
    public RemoteWorkspacesResource remoteWorkspaceResource() {
        return new RemoteWorkspacesResource(remoteWorkspacesManagerFactory, workspacesManager);
    }

    @Path("/profiler")
    public ProfilerResource profilerResource() {
        return new ProfilerResource(profilerRepository);
    }

    @Path("/simulate")
    public SimulateResource simulateResource() {
        return new SimulateResource();
    }
}
