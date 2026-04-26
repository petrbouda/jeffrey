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

package cafe.jeffrey.local.core.web.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.local.core.web.ProjectManagerResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static cafe.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class ProjectDownloadTaskControllerTest {

    @Mock
    ProjectManagerResolver resolver;

    @Test
    void unknownTaskReturnsBadRequest() {
        MockMvcTester mvc = mockMvcTesterFor(new ProjectDownloadTaskController(resolver));

        // No task is registered for "ghost-task" — controller throws
        // Exceptions.invalidRequest, mapped to 400.
        assertThat(mvc.get().uri("/api/internal/workspaces/ws-1/projects/p-1/download/ghost-task/status"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("INVALID_REQUEST");
    }

    @Test
    void cancelUnknownTaskReturns404() {
        MockMvcTester mvc = mockMvcTesterFor(new ProjectDownloadTaskController(resolver));

        assertThat(mvc.delete().uri("/api/internal/workspaces/ws-1/projects/p-1/download/ghost-task"))
                .hasStatus(404);
    }
}
