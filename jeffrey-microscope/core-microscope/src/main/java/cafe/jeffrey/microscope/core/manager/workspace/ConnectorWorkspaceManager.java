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

package cafe.jeffrey.microscope.core.manager.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.connector.api.ConnectorClients;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.project.ConnectorProjectManagerFactory;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorInfo;
import cafe.jeffrey.microscope.common.exception.Exceptions;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;

import cafe.jeffrey.microscope.notification.NotificationType;
import cafe.jeffrey.microscope.notification.Notifications;

public class ConnectorWorkspaceManager implements WorkspaceManager {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectorWorkspaceManager.class);

    private final ConnectorInfo connector;
    private final WorkspaceInfo workspaceInfo;
    private final ConnectorClients clients;
    private final ConnectorProjectManagerFactory projectManagerFactory;

    public ConnectorWorkspaceManager(
            ConnectorInfo connector,
            WorkspaceInfo workspaceInfo,
            ConnectorClients clients,
            ConnectorProjectManagerFactory projectManagerFactory) {

        this.connector = connector;
        this.workspaceInfo = workspaceInfo;
        this.clients = clients;
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public ProjectsManager projectsManager() {
        return new ConnectorProjectsManager(connector, workspaceInfo, clients, projectManagerFactory);
    }

    /**
     * Drops the workspace on the connector. Nothing local goes with it: a profile belongs to the
     * recording it was built from, never to a workspace, so it stays until its recording is deleted.
     */
    @Override
    public void delete() {
        if (connector.address().readOnly()) {
            throw Exceptions.invalidRequest("Cannot delete workspace " + workspaceInfo.id()
                    + ": the connector is declared read-only");
        }

        try {
            clients.discovery().deleteWorkspace(workspaceInfo.id());
            LOG.info("Deleted workspace: workspaceId={}", workspaceInfo.id());
        } catch (Exception e) {
            LOG.warn("Connector DeleteWorkspace failed: workspaceId={}", workspaceInfo.id(), e);

            // The caller gets 204 either way, so from the outside this delete looks complete while
            // the connector still holds the workspace. The next listing shows it again.
            Notifications.of(NotificationType.WORKSPACE_DELETE_PARTIAL)
                    .attribute("workspaceId", workspaceInfo.id())
                    .errorType(e)
                    .emit();
        }
    }
}
