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
import cafe.jeffrey.performance.analyst.client.RemoteConnections;
import cafe.jeffrey.performance.analyst.client.RemoteDiscoveryClient;
import cafe.jeffrey.performance.analyst.resources.response.RemoteProjectResponse;
import cafe.jeffrey.microscope.persistence.api.RemoteServerInfo;
import cafe.jeffrey.microscope.persistence.api.RemoteServersRepository;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.function.Function;

/**
 * Facade for one connected jeffrey-server. Lists workspaces and projects live via gRPC
 * (through {@link RemoteDiscoveryClient}) and creates/deletes workspaces on the server.
 * Trimmed from microscope: no profile/recording managers — discovery only.
 */
public class RemoteServerManager {

    @FunctionalInterface
    public interface Factory extends Function<RemoteServerInfo, RemoteServerManager> {
    }

    private static final Logger LOG = LoggerFactory.getLogger(RemoteServerManager.class);

    private final RemoteServerInfo serverInfo;
    private final RemoteDiscoveryClient discovery;
    private final RemoteServersRepository remoteServersRepository;
    private final RemoteConnections connections;

    public RemoteServerManager(
            RemoteServerInfo serverInfo,
            RemoteDiscoveryClient discovery,
            RemoteServersRepository remoteServersRepository,
            RemoteConnections connections) {

        this.serverInfo = serverInfo;
        this.discovery = discovery;
        this.remoteServersRepository = remoteServersRepository;
        this.connections = connections;
    }

    public RemoteServerInfo info() {
        return serverInfo;
    }

    public RemoteDiscoveryClient discovery() {
        return discovery;
    }

    /**
     * Lists all workspaces on this server (live gRPC ListWorkspaces call).
     */
    public List<WorkspaceInfo> workspaces() {
        try {
            return discovery.allWorkspaces();
        } catch (Exception e) {
            LOG.warn("Failed to list workspaces from remote server: serverId={} address={}",
                    serverInfo.serverId(), serverInfo.address(), e);
            return List.of();
        }
    }

    /**
     * Resolves a single workspace on this server (live gRPC GetWorkspace call).
     */
    public RemoteDiscoveryClient.WorkspaceResult workspace(String workspaceId) {
        return discovery.workspace(workspaceId);
    }

    /**
     * Lists the projects of a workspace on this server (live gRPC ListProjects call).
     */
    public List<RemoteProjectResponse> projects(String workspaceId, boolean includeDeleted) {
        return discovery.allProjects(workspaceId, includeDeleted);
    }

    /**
     * Creates a new workspace on this server via the gRPC CreateWorkspace RPC.
     *
     * @param referenceId stable user-supplied id; jeffrey-cli's project.workspace-ref-id
     *                    must match this value to route recordings to the workspace
     * @param name        display name
     */
    public WorkspaceInfo createWorkspace(String referenceId, String name) {
        WorkspaceInfo created = discovery.createWorkspace(referenceId, name);
        LOG.info("Created workspace on remote server: server_id={} workspace_id={} reference_id={} name={}",
                serverInfo.serverId(), created.id(), referenceId, name);
        return created;
    }

    /**
     * Deletes a workspace on this server via the gRPC DeleteWorkspace RPC.
     */
    public void deleteWorkspace(String workspaceId) {
        discovery.deleteWorkspace(workspaceId);
        LOG.info("Deleted workspace on remote server: server_id={} workspace_id={}",
                serverInfo.serverId(), workspaceId);
    }

    /**
     * Removes the local pointer to this server. Does NOT touch server-side data.
     */
    public void delete() {
        remoteServersRepository.delete(serverInfo.serverId());
        connections.evict(serverInfo.address());
        LOG.info("Deleted local server pointer: server_id={} address={}",
                serverInfo.serverId(), serverInfo.address());
    }
}
