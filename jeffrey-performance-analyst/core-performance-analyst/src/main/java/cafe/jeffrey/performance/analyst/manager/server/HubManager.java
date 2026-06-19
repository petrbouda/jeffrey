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

package cafe.jeffrey.performance.analyst.manager.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.client.CachedHubClientsFactory;
import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.hub.client.DiscoveryClient;
import cafe.jeffrey.hub.client.dto.RemoteProjectResponse;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.microscope.persistence.api.HubInfo;
import cafe.jeffrey.microscope.persistence.api.HubsRepository;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.function.Function;

/**
 * Facade for one connected jeffrey-hub. Lists workspaces and projects live via gRPC and
 * creates/deletes workspaces on the server. Backed by the shared {@link CachedHubClientsFactory},
 * which also supplies the instance/repository/recording-stream clients used by the project-detail
 * controllers (resolved through {@code RemoteProjectResolver}).
 */
public class HubManager {

    @FunctionalInterface
    public interface Factory extends Function<HubInfo, HubManager> {
    }

    private static final Logger LOG = LoggerFactory.getLogger(HubManager.class);

    private final HubInfo serverInfo;
    private final CachedHubClientsFactory clientsFactory;
    private final HubsRepository remoteServersRepository;

    public HubManager(
            HubInfo serverInfo,
            CachedHubClientsFactory clientsFactory,
            HubsRepository remoteServersRepository) {

        this.serverInfo = serverInfo;
        this.clientsFactory = clientsFactory;
        this.remoteServersRepository = remoteServersRepository;
    }

    public HubInfo info() {
        return serverInfo;
    }

    /**
     * The full set of gRPC clients for this server (cached per address). Used by the project-detail
     * resolver to build instance/repository/download managers.
     */
    public HubClients clients() {
        return clientsFactory.apply(serverInfo.address());
    }

    public DiscoveryClient discovery() {
        return clients().discovery();
    }

    /**
     * Lists all workspaces on this server (live gRPC ListWorkspaces call).
     */
    public List<WorkspaceInfo> workspaces() {
        try {
            return discovery().allWorkspaces();
        } catch (Exception e) {
            LOG.warn("Failed to list workspaces from hub: hubId={} address={}",
                    serverInfo.hubId(), serverInfo.address(), e);
            return List.of();
        }
    }

    /**
     * Resolves a single workspace on this server (live gRPC GetWorkspace call).
     */
    public DiscoveryClient.WorkspaceResult workspace(String workspaceId) {
        return discovery().workspace(workspaceId);
    }

    /**
     * Lists the projects of a workspace on this server (live gRPC ListProjects call).
     */
    public List<RemoteProjectResponse> projects(String workspaceId, boolean includeDeleted) {
        return discovery().allProjects(workspaceId, includeDeleted);
    }

    /**
     * Fetches a single project of a workspace on this server (live gRPC GetProject call).
     */
    public RemoteProjectResponse project(String workspaceId, String projectId) {
        return discovery().project(workspaceId, projectId)
                .orElseThrow(() -> Exceptions.projectNotFound(projectId));
    }

    /**
     * Creates a new workspace on this server via the gRPC CreateWorkspace RPC.
     */
    public WorkspaceInfo createWorkspace(String referenceId, String name) {
        WorkspaceInfo created = discovery().createWorkspace(referenceId, name);
        LOG.info("Created workspace on hub: hub_id={} workspace_id={} reference_id={} name={}",
                serverInfo.hubId(), created.id(), referenceId, name);
        return created;
    }

    /**
     * Deletes a workspace on this server via the gRPC DeleteWorkspace RPC.
     */
    public void deleteWorkspace(String workspaceId) {
        discovery().deleteWorkspace(workspaceId);
        LOG.info("Deleted workspace on hub: hub_id={} workspace_id={}",
                serverInfo.hubId(), workspaceId);
    }

    /**
     * Removes the local pointer to this server. Does NOT touch server-side data.
     */
    public void delete() {
        remoteServersRepository.delete(serverInfo.hubId());
        clientsFactory.evict(serverInfo.address());
        LOG.info("Deleted local server pointer: hub_id={} address={}",
                serverInfo.hubId(), serverInfo.address());
    }
}
