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

package cafe.jeffrey.server.core.appinitializer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.server.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.server.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.server.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultWorkspaceInitializerTest {

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    WorkspaceManager existingWorkspace;

    private DefaultWorkspaceProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DefaultWorkspaceProperties();
        // properties default to $default / $default
    }

    @Nested
    class WorkspaceMissing {

        @Test
        void createsDefaultWorkspaceWithConfiguredReferenceIdAndName() {
            when(workspacesManager.findByReferenceId("$default")).thenReturn(Optional.empty());
            when(workspacesManager.create(any())).thenReturn(
                    new WorkspaceInfo("internal-id", "$default", "$default", "$default",
                            null, null, Instant.parse("2026-01-01T00:00:00Z"),
                            WorkspaceStatus.UNKNOWN, 0));

            DefaultWorkspaceInitializer initializer = new DefaultWorkspaceInitializer(
                    workspacesManager, properties);

            initializer.run();

            ArgumentCaptor<WorkspacesManager.CreateWorkspaceRequest> captor =
                    ArgumentCaptor.forClass(WorkspacesManager.CreateWorkspaceRequest.class);
            verify(workspacesManager).create(captor.capture());
            assertEquals("$default", captor.getValue().referenceId());
            assertEquals("$default", captor.getValue().name());
        }

        @Test
        void honorsCustomReferenceIdAndName() {
            properties.setReferenceId("$prod");
            properties.setName("$prod");

            when(workspacesManager.findByReferenceId("$prod")).thenReturn(Optional.empty());
            when(workspacesManager.create(any())).thenReturn(
                    new WorkspaceInfo("internal-id", "$prod", "$prod", "$prod",
                            null, null, Instant.parse("2026-01-01T00:00:00Z"),
                            WorkspaceStatus.UNKNOWN, 0));

            DefaultWorkspaceInitializer initializer = new DefaultWorkspaceInitializer(
                    workspacesManager, properties);

            initializer.run();

            ArgumentCaptor<WorkspacesManager.CreateWorkspaceRequest> captor =
                    ArgumentCaptor.forClass(WorkspacesManager.CreateWorkspaceRequest.class);
            verify(workspacesManager).create(captor.capture());
            assertEquals("$prod", captor.getValue().referenceId());
            assertEquals("$prod", captor.getValue().name());
        }
    }

    @Nested
    class WorkspaceAlreadyPresent {

        @Test
        void doesNotCreateAnything() {
            when(workspacesManager.findByReferenceId("$default"))
                    .thenReturn(Optional.of(existingWorkspace));

            DefaultWorkspaceInitializer initializer = new DefaultWorkspaceInitializer(
                    workspacesManager, properties);

            initializer.run();

            verify(workspacesManager, never()).create(any());
        }
    }

    @Nested
    class InvalidProperties {

        @Test
        void blankReferenceIdThrows() {
            properties.setReferenceId("");

            DefaultWorkspaceInitializer initializer = new DefaultWorkspaceInitializer(
                    workspacesManager, properties);

            assertThrows(IllegalArgumentException.class, initializer::run);
            verify(workspacesManager, never()).create(any());
        }

        @Test
        void blankNameThrows() {
            properties.setName(null);

            DefaultWorkspaceInitializer initializer = new DefaultWorkspaceInitializer(
                    workspacesManager, properties);

            assertThrows(IllegalArgumentException.class, initializer::run);
            verify(workspacesManager, never()).create(any());
        }
    }
}
