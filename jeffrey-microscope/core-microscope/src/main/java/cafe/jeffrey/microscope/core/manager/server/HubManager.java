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

package cafe.jeffrey.microscope.core.manager.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.client.CachedHubClientsFactory;
import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.hub.client.DiscoveryClient;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManagerFactory;
import cafe.jeffrey.microscope.persistence.api.HubInfo;
import cafe.jeffrey.microscope.persistence.api.HubsRepository;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Facade for one connected jeffrey-hub. Lists workspaces live via gRPC,
 * creates new workspaces on the server, and produces per-workspace managers.
 */
public class HubManager {

    @FunctionalInterface
    public interface Factory extends Function<HubInfo, HubManager> {
    }

    private static final Logger LOG = LoggerFactory.getLogger(HubManager.class);

    private final HubInfo serverInfo;
    private final HubClients remoteClients;
    private final WorkspaceManagerFactory workspaceManagerFactory;
    private final HubsRepository remoteServersRepository;
    private final HubClients.Factory remoteClientsFactory;

    public HubManager(
            HubInfo serverInfo,
            HubClients remoteClients,
            WorkspaceManagerFactory workspaceManagerFactory,
            HubsRepository remoteServersRepository,
            HubClients.Factory remoteClientsFactory) {

        this.serverInfo = serverInfo;
        this.remoteClients = remoteClients;
        this.workspaceManagerFactory = workspaceManagerFactory;
        this.remoteServersRepository = remoteServersRepository;
        this.remoteClientsFactory = remoteClientsFactory;
    }

    public HubInfo info() {
        return serverInfo;
    }

    /**
     * Lists all workspaces on this server (live gRPC ListWorkspaces call).
     */
    public List<WorkspaceInfo> workspaces() {
        try {
            return remoteClients.discovery().allWorkspaces();
        } catch (Exception e) {
            LOG.warn("Failed to list workspaces from hub: hubId={} address={}",
                    serverInfo.hubId(), serverInfo.address(), e);
            return List.of();
        }
    }

    /**
     * Resolves a single workspace on this server to a {@link WorkspaceManager}.
     */
    public Optional<WorkspaceManager> workspace(String workspaceId) {
        DiscoveryClient.WorkspaceResult result = remoteClients.discovery().workspace(workspaceId);
        return switch (result.status()) {
            case AVAILABLE -> Optional.of(workspaceManagerFactory.create(serverInfo, result.info(), remoteClients));
            case UNAVAILABLE, OFFLINE, UNKNOWN -> Optional.empty();
        };
    }

    /**
     * Creates a new workspace on this server via the gRPC CreateWorkspace RPC.
     *
     * @param referenceId stable user-supplied id; jeffrey-provisioner's project.workspace-ref-id
     *                    must match this value to route recordings to the workspace
     * @param name        display name
     */
    public WorkspaceInfo createWorkspace(String referenceId, String name) {
        WorkspaceInfo created = remoteClients.discovery().createWorkspace(referenceId, name);
        LOG.info("Created workspace on hub: hub_id={} workspace_id={} reference_id={} name={}",
                serverInfo.hubId(), created.id(), referenceId, name);
        return created;
    }

    /**
     * Removes the local pointer to this server. Does NOT touch server-side data.
     */
    public void delete() {
        remoteServersRepository.delete(serverInfo.hubId());
        if (remoteClientsFactory instanceof CachedHubClientsFactory cached) {
            cached.evict(serverInfo.address());
        }
        LOG.info("Deleted local server pointer: hub_id={} address={}",
                serverInfo.hubId(), serverInfo.address());
    }
}
