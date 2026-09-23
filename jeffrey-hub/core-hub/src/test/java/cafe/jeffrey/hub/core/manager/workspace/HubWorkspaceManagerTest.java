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

import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceStatus;
import cafe.jeffrey.hub.persistence.api.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HubWorkspaceManagerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-04-30T10:00:00Z"), ZoneOffset.UTC);

    /**
     * A workspace deleted rows-only came back: its projects' directories stayed on the volume,
     * and the reconciler re-creates any project whose on-disk declaration still exists. So each
     * project is deleted the way a project is deleted on its own — which removes its directory
     * — and the workspace row goes last.
     */
    @Test
    void deletesEveryProjectFullyBeforeTheWorkspaceRow() {
        ProjectManager first = mock(ProjectManager.class);
        ProjectManager second = mock(ProjectManager.class);
        ProjectsManager projectsManager = mock(ProjectsManager.class);
        when(projectsManager.findAll()).thenReturn(List.of(first, second));
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceInfo workspaceInfo = new WorkspaceInfo(
                "ws-1", "ws-1", "repo-1", "production",
                null, null, FIXED_CLOCK.instant(), WorkspaceStatus.AVAILABLE, 2);
        HubWorkspaceManager manager = new HubWorkspaceManager(
                FIXED_CLOCK, new HubJeffreyDirs(Path.of("/tmp/jeffrey-hub-test")), workspaceInfo,
                workspaceRepository, _ -> projectsManager);

        manager.delete();

        InOrder order = inOrder(first, second, workspaceRepository);
        order.verify(first).delete();
        order.verify(second).delete();
        order.verify(workspaceRepository).delete();
    }
}
