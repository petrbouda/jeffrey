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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultWorkspaceInitializerTest {

    @Mock
    WorkspacesManager workspacesManager;

    private DefaultWorkspaceProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DefaultWorkspaceProperties();
        // the reference id defaults to $default
    }

    @Nested
    class WorkspaceMissing {

        @Test
        void createsTheDefaultWorkspaceUnderItsReferenceId() {
            when(workspacesManager.exists("$default")).thenReturn(false);
            when(workspacesManager.create("$default")).thenReturn(
                    new WorkspaceInfo("$default", Instant.parse("2026-01-01T00:00:00Z"), 0));

            new DefaultWorkspaceInitializer(workspacesManager, properties).run();

            verify(workspacesManager).create("$default");
        }

        @Test
        void honorsACustomReferenceId() {
            properties.setReferenceId("$prod");
            when(workspacesManager.exists("$prod")).thenReturn(false);
            when(workspacesManager.create("$prod")).thenReturn(
                    new WorkspaceInfo("$prod", Instant.parse("2026-01-01T00:00:00Z"), 0));

            new DefaultWorkspaceInitializer(workspacesManager, properties).run();

            verify(workspacesManager).create("$prod");
        }
    }

    @Nested
    class WorkspaceAlreadyPresent {

        @Test
        void doesNotCreateAnything() {
            when(workspacesManager.exists("$default")).thenReturn(true);

            new DefaultWorkspaceInitializer(workspacesManager, properties).run();

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
    }
}
