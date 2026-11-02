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

import cafe.jeffrey.microscope.connector.api.DiscoveryClient;
import cafe.jeffrey.microscope.connector.api.FileStreamClient;
import cafe.jeffrey.microscope.connector.api.ConnectorClients;
import cafe.jeffrey.microscope.connector.api.InstancesClient;
import cafe.jeffrey.microscope.connector.api.ProjectsClient;
import cafe.jeffrey.microscope.connector.api.RepositoryClient;
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.core.manager.project.ConnectorProjectManagerFactory;
import cafe.jeffrey.microscope.model.connector.AzureFilesAddress;
import cafe.jeffrey.microscope.model.connector.AzureFilesAuth;
import cafe.jeffrey.microscope.model.connector.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.connector.ConnectorInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;
import cafe.jeffrey.microscope.persistence.api.WorkspaceRepository;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConnectorWorkspaceManagerTest {

    private static final Instant NOW = Instant.parse("2026-03-01T00:00:00Z");
    private static final WorkspaceInfo WORKSPACE =
            new WorkspaceInfo("ws-1", "prod", "prod", "Production", null, null, NOW, WorkspaceStatus.AVAILABLE);

    private final DiscoveryClient discovery = mock(DiscoveryClient.class);
    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);

    private ConnectorWorkspaceManager managerOn(boolean readOnly) {
        ConnectorInfo connector = new ConnectorInfo("Share", new AzureFilesAddress(
                "https://acct.file.core.windows.net", "recordings", "", AzureFilesProtocol.SMB, AzureFilesAuth.SAS,
                readOnly));
        ConnectorClients clients = new ConnectorClients(discovery, mock(RepositoryClient.class), mock(FileStreamClient.class),
                mock(InstancesClient.class), mock(ProjectsClient.class));
        return new ConnectorWorkspaceManager(mock(MicroscopeJeffreyDirs.class), connector, WORKSPACE, workspaceRepository,
                clients, mock(ConnectorProjectManagerFactory.class));
    }

    /**
     * The connector delete is best-effort, so a refusal there would still wipe this workspace's local
     * profiles and answer success: a read-only source is refused before anything is touched.
     */
    @Test
    void refusesOnAReadOnlySourceBeforeTouchingAnythingLocal() {
        assertThrows(JeffreyClientException.class, () -> managerOn(true).delete());

        verifyNoInteractions(discovery, workspaceRepository);
    }

    @Test
    void deletesOnTheSourceAndThenLocallyWhenTheSourceMayBeChanged() {
        when(workspaceRepository.delete()).thenReturn(List.of());

        managerOn(false).delete();

        verify(discovery).deleteWorkspace("ws-1");
        verify(workspaceRepository).delete();
    }
}
