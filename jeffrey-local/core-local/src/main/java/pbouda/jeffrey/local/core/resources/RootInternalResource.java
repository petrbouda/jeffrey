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

package pbouda.jeffrey.local.core.resources;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import pbouda.jeffrey.shared.common.JeffreyVersion;
import pbouda.jeffrey.local.core.configuration.SettingsMetadata;
import pbouda.jeffrey.local.core.manager.SettingsManager;
import pbouda.jeffrey.local.core.manager.qanalysis.QuickAnalysisManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.local.core.client.RemoteClients;
import pbouda.jeffrey.profile.resources.ProfileResourceFactory;
import pbouda.jeffrey.local.persistence.LocalCorePersistenceProvider;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

@Path("/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RootInternalResource {

    private final Optional<RemoteClients.Factory> remoteClientsFactory;
    private final WorkspacesManager workspacesManager;
    private final LocalCorePersistenceProvider localCorePersistenceProvider;
    private final ProfileResourceFactory profileResourceFactory;
    private final Optional<QuickAnalysisManager> quickAnalysisManager;
    private final SettingsManager settingsManager;
    private final SettingsMetadata settingsMetadata;
    private final Clock clock;

    @Inject
    public RootInternalResource(
            Optional<RemoteClients.Factory> remoteClientsFactory,
            WorkspacesManager workspacesManager,
            LocalCorePersistenceProvider localCorePersistenceProvider,
            ProfileResourceFactory profileResourceFactory,
            Optional<QuickAnalysisManager> quickAnalysisManager,
            SettingsManager settingsManager,
            SettingsMetadata settingsMetadata,
            Clock clock) {

        this.remoteClientsFactory = remoteClientsFactory;
        this.workspacesManager = workspacesManager;
        this.localCorePersistenceProvider = localCorePersistenceProvider;
        this.profileResourceFactory = profileResourceFactory;
        this.quickAnalysisManager = quickAnalysisManager;
        this.settingsManager = settingsManager;
        this.settingsMetadata = settingsMetadata;
        this.clock = clock;
    }

    @Path("/workspaces")
    public WorkspacesResource workspaceResource() {
        return new WorkspacesResource(
                workspacesManager,
                profileResourceFactory,
                clock);
    }

    @Path("/remote-workspaces")
    public RemoteWorkspacesResource remoteWorkspaceResource() {
        return new RemoteWorkspacesResource(
                remoteClientsFactory.orElse(null),
                workspacesManager);
    }

    @Path("/profiler")
    public ProfilerResource profilerResource() {
        return new ProfilerResource(workspacesManager);
    }


    @Path("/profiles")
    public ProfilesResource profilesResource() {
        return new ProfilesResource(
                workspacesManager,
                quickAnalysisManager.orElse(null),
                localCorePersistenceProvider.localCoreRepositories(),
                profileResourceFactory);
    }

    @Path("/quick-analysis")
    public QuickAnalysisResource quickAnalysisResource() {
        return new QuickAnalysisResource(
                quickAnalysisManager.orElse(null),
                profileResourceFactory);
    }

    @Path("/settings")
    public SettingsResource settingsResource() {
        return new SettingsResource(settingsManager, settingsMetadata);
    }

    @GET
    @Path("/version")
    public Map<String, String> version() {
        return Map.of("version", JeffreyVersion.resolveJeffreyVersion());
    }
}
