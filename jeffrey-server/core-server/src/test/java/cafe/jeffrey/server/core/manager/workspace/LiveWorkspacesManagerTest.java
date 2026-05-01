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

package cafe.jeffrey.server.core.manager.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.server.persistence.api.WorkspacesRepository;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;

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

class LiveWorkspacesManagerTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-04-30T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    private WorkspacesRepository repository;
    private LiveWorkspacesManager manager;

    @BeforeEach
    void setUp() {
        repository = mock(WorkspacesRepository.class);
        WorkspaceManager.Factory factory = info -> mock(WorkspaceManager.class);
        manager = new LiveWorkspacesManager(FIXED_CLOCK, repository, factory);
    }

    @Nested
    class Create {

        @Test
        void persistsWorkspaceWhenIdAndNameAreUnique() {
            when(repository.findByReferenceId("dev-pb")).thenReturn(Optional.empty());
            when(repository.existsByName("Dev Workspace")).thenReturn(false);
            when(repository.create(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkspaceInfo created = manager.create(WorkspacesManager.CreateWorkspaceRequest.builder()
                    .referenceId("dev-pb")
                    .name("Dev Workspace")
                    .build());

            assertEquals("dev-pb", created.referenceId());
            assertEquals("Dev Workspace", created.name());
            assertEquals(WorkspaceStatus.UNKNOWN, created.status());
            verify(repository).create(any());
        }

        @Test
        void blankReferenceId_throwsIllegalArgumentException() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                    WorkspacesManager.CreateWorkspaceRequest.builder()
                            .referenceId("   ")
                            .name("Dev Workspace")
                            .build());

            assertTrue(ex.getMessage().toLowerCase().contains("reference id"));
            verify(repository, never()).create(any());
        }

        @Test
        void invalidFormatReferenceId_throwsIllegalArgumentException() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                    WorkspacesManager.CreateWorkspaceRequest.builder()
                            .referenceId("-leading-dash")
                            .name("Dev Workspace")
                            .build());

            assertTrue(ex.getMessage().toLowerCase().contains("invalid"));
            verify(repository, never()).create(any());
        }

        @Test
        void blankName_throwsIllegalArgumentException() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                    manager.create(WorkspacesManager.CreateWorkspaceRequest.builder()
                            .referenceId("dev-pb")
                            .name("  ")
                            .build()));

            assertTrue(ex.getMessage().contains("Name"));
            verify(repository, never()).create(any());
        }

        @Test
        void duplicateReferenceId_throwsAlreadyExists() {
            WorkspaceInfo existing = new WorkspaceInfo("ws-internal-1", "dev-pb", null, "Existing", null, null, FIXED_TIME, WorkspaceStatus.AVAILABLE, 0);
            when(repository.findByReferenceId("dev-pb")).thenReturn(Optional.of(existing));

            var ex = assertThrows(WorkspaceAlreadyExistsException.class, () ->
                    manager.create(WorkspacesManager.CreateWorkspaceRequest.builder()
                            .referenceId("dev-pb")
                            .name("New Name")
                            .build()));

            assertTrue(ex.getMessage().contains("dev-pb"));
            verify(repository, never()).create(any());
        }

        @Test
        void duplicateName_throwsAlreadyExists() {
            when(repository.findByReferenceId("dev-pb")).thenReturn(Optional.empty());
            when(repository.existsByName("Dev Workspace")).thenReturn(true);

            var ex = assertThrows(WorkspaceAlreadyExistsException.class, () ->
                    manager.create(WorkspacesManager.CreateWorkspaceRequest.builder()
                            .referenceId("dev-pb")
                            .name("Dev Workspace")
                            .build()));

            assertTrue(ex.getMessage().contains("Dev Workspace"));
            verify(repository, never()).create(any());
        }
    }
}
