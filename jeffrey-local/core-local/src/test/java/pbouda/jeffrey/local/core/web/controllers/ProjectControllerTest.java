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

package pbouda.jeffrey.local.core.web.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import pbouda.jeffrey.local.core.manager.project.ProjectManager;
import pbouda.jeffrey.local.core.manager.project.ProjectsManager;
import pbouda.jeffrey.local.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.local.core.web.ProjectManagerResolver;
import pbouda.jeffrey.local.core.web.ProjectManagerResolver.ProjectContext;
import pbouda.jeffrey.shared.common.model.ProjectInfo;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static pbouda.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    @Mock
    ProjectManagerResolver resolver;

    @Mock
    WorkspaceManager workspaceManager;

    @Mock
    ProjectsManager projectsManager;

    @Mock
    ProjectManager projectManager;

    @Test
    void initializingAlwaysFalse() {
        MockMvcTester mvc = mockMvcTesterFor(new ProjectController(resolver));

        assertThat(mvc.get().uri("/api/internal/workspaces/ws-1/projects/p-1/initializing"))
                .hasStatusOk()
                .bodyText().isEqualTo("false");
    }

    @Test
    void restoreInvokesManager() {
        when(resolver.resolve("ws-1", "p-1"))
                .thenReturn(new ProjectContext(workspaceManager, projectsManager, projectManager));
        when(projectManager.info()).thenReturn(new ProjectInfo(
                "p-1", "p-1", "demo", "Demo", "demo", "ws-1",
                Instant.EPOCH, Instant.EPOCH, Map.of(), null));

        MockMvcTester mvc = mockMvcTesterFor(new ProjectController(resolver));

        assertThat(mvc.post().uri("/api/internal/workspaces/ws-1/projects/p-1/restore"))
                .hasStatusOk();
    }
}
