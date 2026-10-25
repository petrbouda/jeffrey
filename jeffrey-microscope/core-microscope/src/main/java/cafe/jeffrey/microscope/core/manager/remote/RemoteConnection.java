/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.manager.remote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.remote.client.RemoteClients;
import cafe.jeffrey.microscope.remote.client.DiscoveryClient;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.RemoteProjectManagerFactory;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManagerFactory;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The configured remote: lists its workspaces live, creates workspaces on it, and produces
 * per-workspace and per-project managers. What it talks to — a Jeffrey Hub over gRPC, or a file
 * share directly — is its clients' business.
 */
public class RemoteConnection {

    @FunctionalInterface
    public interface Factory extends Function<RemoteInfo, RemoteConnection> {
    }

    private static final Logger LOG = LoggerFactory.getLogger(RemoteConnection.class);

    private final RemoteInfo remote;
    private final RemoteClients clients;
    private final WorkspaceManagerFactory workspaceManagerFactory;
    private final RemoteProjectManagerFactory projectManagerFactory;

    public RemoteConnection(
            RemoteInfo remote,
            RemoteClients clients,
            WorkspaceManagerFactory workspaceManagerFactory,
            RemoteProjectManagerFactory projectManagerFactory) {

        this.remote = remote;
        this.clients = clients;
        this.workspaceManagerFactory = workspaceManagerFactory;
        this.projectManagerFactory = projectManagerFactory;
    }

    public RemoteInfo info() {
        return remote;
    }

    /** Whether the remote answers at all, and what it is running; throws when it cannot be reached. */
    public DiscoveryClient.PublicApiInfo infoOrThrow() {
        return clients.discovery().info();
    }

    /** Every workspace on the remote, read live; none when it cannot be reached. */
    public List<WorkspaceInfo> workspaces() {
        try {
            return clients.discovery().allWorkspaces();
        } catch (Exception e) {
            LOG.warn("Failed to list workspaces from the remote: address={}", remote.address(), e);
            return List.of();
        }
    }

    public List<WorkspaceInfo> workspacesOrThrow() {
        return clients.discovery().allWorkspaces();
    }

    /** One workspace on the remote as a {@link WorkspaceManager}. */
    public Optional<WorkspaceManager> workspace(String workspaceId) {
        DiscoveryClient.WorkspaceResult result = clients.discovery().workspace(workspaceId);
        return switch (result.status()) {
            case AVAILABLE -> Optional.of(workspaceManagerFactory.create(remote, result.info(), clients));
            case UNAVAILABLE, OFFLINE, UNKNOWN -> Optional.empty();
        };
    }

    public Optional<WorkspaceManager> workspaceOrThrow(String workspaceId) {
        return clients.discovery().workspaceOrThrow(workspaceId)
                .map(this::workspace);
    }

    /**
     * One project of one of the remote's workspaces, in a single call: the remote checks the
     * workspace and the project together, and the answer carries everything the manager needs.
     * Empty when it holds no such workspace or project; a remote that cannot be reached surfaces as
     * its failure.
     */
    public Optional<ProjectManager> projectOrThrow(String workspaceId, String projectId) {
        return clients.discovery().project(workspaceId, projectId)
                .map(project -> projectManagerFactory.create(remote, clients, project));
    }

    /** The same lookup, answering empty when the remote cannot be reached. */
    public Optional<ProjectManager> project(String workspaceId, String projectId) {
        try {
            return projectOrThrow(workspaceId, projectId);
        } catch (RuntimeException e) {
            LOG.warn("Failed to look up a project on the remote: workspaceId={} projectId={}",
                    workspaceId, projectId, e);
            return Optional.empty();
        }
    }

    /**
     * Builds a manager for workspace information returned by the same scan. This avoids a redundant
     * workspace lookup after ListWorkspaces has already established that the workspace exists.
     */
    public WorkspaceManager workspace(WorkspaceInfo workspaceInfo) {
        return workspaceManagerFactory.create(remote, workspaceInfo, clients);
    }

    /**
     * Creates a workspace on the remote.
     *
     * @param referenceId stable user-supplied id; jeffrey-provisioner's project.workspace-ref-id
     *                    must match this value to route recordings to the workspace
     * @param name        display name
     */
    public WorkspaceInfo createWorkspace(String referenceId, String name) {
        WorkspaceInfo created = clients.discovery().createWorkspace(referenceId, name);
        LOG.info("Created workspace on the remote: workspace_id={} reference_id={} name={}",
                created.id(), referenceId, name);
        return created;
    }
}
