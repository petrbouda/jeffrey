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

package cafe.jeffrey.microscope.core.manager.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.connector.api.ConnectorClients;
import cafe.jeffrey.microscope.connector.api.DiscoveryClient;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.ConnectorProjectManagerFactory;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManagerFactory;
import cafe.jeffrey.microscope.model.connector.ConnectorInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The configured connector: lists its workspaces live and produces per-workspace and per-project
 * managers. How the share is read is its clients' business.
 */
public class ConnectorConnection {

    @FunctionalInterface
    public interface Factory extends Function<ConnectorInfo, ConnectorConnection> {
    }

    private static final Logger LOG = LoggerFactory.getLogger(ConnectorConnection.class);

    private final ConnectorInfo connector;
    private final ConnectorClients clients;
    private final WorkspaceManagerFactory workspaceManagerFactory;
    private final ConnectorProjectManagerFactory projectManagerFactory;

    public ConnectorConnection(
            ConnectorInfo connector,
            ConnectorClients clients,
            WorkspaceManagerFactory workspaceManagerFactory,
            ConnectorProjectManagerFactory projectManagerFactory) {

        this.connector = connector;
        this.clients = clients;
        this.workspaceManagerFactory = workspaceManagerFactory;
        this.projectManagerFactory = projectManagerFactory;
    }

    public ConnectorInfo info() {
        return connector;
    }

    /** Whether the connector answers at all, and what it is running; throws when it cannot be reached. */
    public DiscoveryClient.PublicApiInfo infoOrThrow() {
        return clients.discovery().info();
    }

    /** Every workspace on the connector, read live; none when it cannot be reached. */
    public List<WorkspaceInfo> workspaces() {
        try {
            return clients.discovery().allWorkspaces();
        } catch (Exception e) {
            LOG.warn("Failed to list workspaces from the connector: address={}", connector.address(), e);
            return List.of();
        }
    }

    public List<WorkspaceInfo> workspacesOrThrow() {
        return clients.discovery().allWorkspaces();
    }

    /** One workspace on the connector as a {@link WorkspaceManager}. */
    public Optional<WorkspaceManager> workspace(String workspaceId) {
        DiscoveryClient.WorkspaceResult result = clients.discovery().workspace(workspaceId);
        return switch (result.status()) {
            case AVAILABLE -> Optional.of(workspaceManagerFactory.create(connector, result.info(), clients));
            case UNAVAILABLE, OFFLINE, UNKNOWN -> Optional.empty();
        };
    }

    public Optional<WorkspaceManager> workspaceOrThrow(String workspaceId) {
        return clients.discovery().workspaceOrThrow(workspaceId)
                .map(this::workspace);
    }

    /**
     * One project of one of the connector's workspaces, in a single call: the connector checks the
     * workspace and the project together, and the answer carries everything the manager needs.
     * Empty when it holds no such workspace or project; a connector that cannot be reached surfaces as
     * its failure.
     */
    public Optional<ProjectManager> projectOrThrow(String workspaceId, String projectId) {
        return clients.discovery().project(workspaceId, projectId)
                .map(project -> projectManagerFactory.create(connector, clients, project));
    }

    /** The same lookup, answering empty when the connector cannot be reached. */
    public Optional<ProjectManager> project(String workspaceId, String projectId) {
        try {
            return projectOrThrow(workspaceId, projectId);
        } catch (RuntimeException e) {
            LOG.warn("Failed to look up a project on the connector: workspaceId={} projectId={}",
                    workspaceId, projectId, e);
            return Optional.empty();
        }
    }

    /**
     * Builds a manager for workspace information returned by the same scan. This avoids a redundant
     * workspace lookup after ListWorkspaces has already established that the workspace exists.
     */
    public WorkspaceManager workspace(WorkspaceInfo workspaceInfo) {
        return workspaceManagerFactory.create(connector, workspaceInfo, clients);
    }
}
