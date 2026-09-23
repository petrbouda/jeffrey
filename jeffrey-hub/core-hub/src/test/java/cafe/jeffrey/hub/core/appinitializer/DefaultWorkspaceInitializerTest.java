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

package cafe.jeffrey.hub.core.appinitializer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceStatus;

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
