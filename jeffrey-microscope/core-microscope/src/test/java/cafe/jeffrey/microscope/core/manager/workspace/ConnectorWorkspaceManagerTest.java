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
import cafe.jeffrey.microscope.core.manager.project.ConnectorProjectManagerFactory;
import cafe.jeffrey.microscope.connector.azure.AzureFilesAddress;
import cafe.jeffrey.microscope.connector.azure.AzureFilesAuth;
import cafe.jeffrey.microscope.connector.azure.AzureFilesProtocol;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;
import cafe.jeffrey.microscope.common.exception.JeffreyClientException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ConnectorWorkspaceManagerTest {

    private static final Instant NOW = Instant.parse("2026-03-01T00:00:00Z");
    private static final WorkspaceInfo WORKSPACE =
            new WorkspaceInfo("ws-1", "prod", "prod", "Production", null, null, NOW, WorkspaceStatus.AVAILABLE);

    private final DiscoveryClient discovery = mock(DiscoveryClient.class);

    private ConnectorWorkspaceManager managerOn(boolean readOnly) {
        ConnectorInfo connector = new ConnectorInfo("Share", new AzureFilesAddress(
                "https://acct.file.core.windows.net", "recordings", "", AzureFilesProtocol.SMB, AzureFilesAuth.SAS,
                readOnly));
        ConnectorClients clients = new ConnectorClients(discovery, mock(RepositoryClient.class), mock(FileStreamClient.class),
                mock(InstancesClient.class), mock(ProjectsClient.class));
        return new ConnectorWorkspaceManager(connector, WORKSPACE, clients, mock(ConnectorProjectManagerFactory.class));
    }

    /** The connector delete is best-effort and answers success either way, so a read-only source is refused up front. */
    @Test
    void refusesOnAReadOnlySourceWithoutCallingIt() {
        assertThrows(JeffreyClientException.class, () -> managerOn(true).delete());

        verifyNoInteractions(discovery);
    }

    @Test
    void deletesOnTheSourceWhenTheSourceMayBeChanged() {
        managerOn(false).delete();

        verify(discovery).deleteWorkspace("ws-1");
    }

    /** Best-effort: a failing source must not turn the delete into an error for the caller. */
    @Test
    void swallowsAFailureOnTheSource() {
        doThrow(new IllegalStateException("share unreachable")).when(discovery).deleteWorkspace("ws-1");

        assertDoesNotThrow(() -> managerOn(false).delete());
    }
}
