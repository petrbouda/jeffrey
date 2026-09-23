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

package cafe.jeffrey.hub.core.manager.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.persistence.api.WorkspacesRepository;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HubWorkspacesManagerTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-04-30T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    private WorkspacesRepository repository;
    private HubWorkspacesManager manager;

    @BeforeEach
    void setUp() {
        repository = mock(WorkspacesRepository.class);
        WorkspaceManager.Factory factory = info -> mock(WorkspaceManager.class);
        manager = new HubWorkspacesManager(FIXED_CLOCK, repository, factory);
    }

    @Nested
    class Create {

        @Test
        void persistsWorkspaceWhenIdAndNameAreUnique() {
            when(repository.findByReferenceId("dev-pb")).thenReturn(Optional.empty());
            when(repository.existsByName("Dev Workspace")).thenReturn(false);
            when(repository.create(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkspaceInfo created = manager.create(new WorkspacesManager.CreateWorkspaceRequest("dev-pb", "Dev Workspace"));

            assertEquals("dev-pb", created.referenceId());
            assertEquals("Dev Workspace", created.name());
            assertEquals(WorkspaceStatus.UNKNOWN, created.status());
            verify(repository).create(any());
        }

        @Test
        void blankReferenceId_throwsIllegalArgumentException() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                    new WorkspacesManager.CreateWorkspaceRequest("   ", "Dev Workspace"));

            assertTrue(ex.getMessage().toLowerCase().contains("reference id"));
            verify(repository, never()).create(any());
        }

        @Test
        void invalidFormatReferenceId_throwsIllegalArgumentException() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                    new WorkspacesManager.CreateWorkspaceRequest("-leading-dash", "Dev Workspace"));

            assertTrue(ex.getMessage().toLowerCase().contains("invalid"));
            verify(repository, never()).create(any());
        }

        @Test
        void blankName_throwsIllegalArgumentException() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                    manager.create(new WorkspacesManager.CreateWorkspaceRequest("dev-pb", "  ")));

            assertTrue(ex.getMessage().contains("Name"));
            verify(repository, never()).create(any());
        }

        @Test
        void duplicateReferenceId_throwsAlreadyExists() {
            WorkspaceInfo existing = new WorkspaceInfo("ws-internal-1", "dev-pb", null, "Existing", null, null, FIXED_TIME, WorkspaceStatus.AVAILABLE, 0);
            when(repository.findByReferenceId("dev-pb")).thenReturn(Optional.of(existing));

            var ex = assertThrows(WorkspaceAlreadyExistsException.class, () ->
                    manager.create(new WorkspacesManager.CreateWorkspaceRequest("dev-pb", "New Name")));

            assertTrue(ex.getMessage().contains("dev-pb"));
            verify(repository, never()).create(any());
        }

        @Test
        void duplicateName_throwsAlreadyExists() {
            when(repository.findByReferenceId("dev-pb")).thenReturn(Optional.empty());
            when(repository.existsByName("Dev Workspace")).thenReturn(true);

            var ex = assertThrows(WorkspaceAlreadyExistsException.class, () ->
                    manager.create(new WorkspacesManager.CreateWorkspaceRequest("dev-pb", "Dev Workspace")));

            assertTrue(ex.getMessage().contains("Dev Workspace"));
            verify(repository, never()).create(any());
        }
    }
}
