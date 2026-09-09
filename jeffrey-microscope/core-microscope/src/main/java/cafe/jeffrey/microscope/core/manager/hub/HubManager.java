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

package cafe.jeffrey.microscope.core.manager.hub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.client.CachedHubClientsFactory;
import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.hub.client.DiscoveryClient;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManagerFactory;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.microscope.persistence.api.HubsRepository;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Facade for one connected jeffrey-hub. Lists workspaces live via gRPC,
 * creates new workspaces on the hub, and produces per-workspace managers.
 */
public class HubManager {

    @FunctionalInterface
    public interface Factory extends Function<HubInfo, HubManager> {
    }

    private static final Logger LOG = LoggerFactory.getLogger(HubManager.class);

    private final HubInfo hubInfo;
    private final HubClients hubClients;
    private final WorkspaceManagerFactory workspaceManagerFactory;
    private final HubsRepository hubsRepository;
    private final HubClients.Factory hubClientsFactory;

    public HubManager(
            HubInfo hubInfo,
            HubClients hubClients,
            WorkspaceManagerFactory workspaceManagerFactory,
            HubsRepository hubsRepository,
            HubClients.Factory hubClientsFactory) {

        this.hubInfo = hubInfo;
        this.hubClients = hubClients;
        this.workspaceManagerFactory = workspaceManagerFactory;
        this.hubsRepository = hubsRepository;
        this.hubClientsFactory = hubClientsFactory;
    }

    public HubInfo info() {
        return hubInfo;
    }

    /**
     * Whether this hub answers at all, and what it is running. Empty when it cannot be reached.
     * <p>
     * Needed because {@link #workspaces()} reports a hub that is down and a hub that is empty the
     * same way — as no workspaces — which is the right thing for a UI that would otherwise show an
     * error banner, and the wrong thing for a caller that has to tell a reader why a listing came
     * back empty. One cheap round trip separates the two.
     */
    public Optional<DiscoveryClient.PublicApiInfo> tryInfo() {
        return hubClients.discovery().tryInfo();
    }

    /**
     * Lists all workspaces on this hub (live gRPC ListWorkspaces call).
     */
    public List<WorkspaceInfo> workspaces() {
        try {
            return hubClients.discovery().allWorkspaces();
        } catch (Exception e) {
            LOG.warn("Failed to list workspaces from hub: hubId={} address={}",
                    hubInfo.hubId(), hubInfo.address(), e);
            return List.of();
        }
    }

    /**
     * Resolves a single workspace on this hub to a {@link WorkspaceManager}.
     */
    public Optional<WorkspaceManager> workspace(String workspaceId) {
        DiscoveryClient.WorkspaceResult result = hubClients.discovery().workspace(workspaceId);
        return switch (result.status()) {
            case AVAILABLE -> Optional.of(workspaceManagerFactory.create(hubInfo, result.info(), hubClients));
            case UNAVAILABLE, OFFLINE, UNKNOWN -> Optional.empty();
        };
    }

    /**
     * Creates a new workspace on this hub via the gRPC CreateWorkspace RPC.
     *
     * @param referenceId stable user-supplied id; jeffrey-provisioner's project.workspace-ref-id
     *                    must match this value to route recordings to the workspace
     * @param name        display name
     */
    public WorkspaceInfo createWorkspace(String referenceId, String name) {
        WorkspaceInfo created = hubClients.discovery().createWorkspace(referenceId, name);
        LOG.info("Created workspace on hub: hub_id={} workspace_id={} reference_id={} name={}",
                hubInfo.hubId(), created.id(), referenceId, name);
        return created;
    }

    /**
     * Removes the local pointer to this hub. Does NOT touch data on the hub.
     */
    public void delete() {
        hubsRepository.delete(hubInfo.hubId());
        if (hubClientsFactory instanceof CachedHubClientsFactory cached) {
            cached.evict(hubInfo.address());
        }
        LOG.info("Deleted local hub pointer: hub_id={} address={}",
                hubInfo.hubId(), hubInfo.address());
    }
}
