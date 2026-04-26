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
import pbouda.jeffrey.local.core.web.ProjectManagerResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static pbouda.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class ProjectLiveStreamControllerTest {

    @Mock
    ProjectManagerResolver resolver;

    @Test
    void rejectsEmptySessionIds() {
        MockMvcTester mvc = mockMvcTesterFor(new ProjectLiveStreamController(resolver));

        assertThat(mvc.get().uri("/api/internal/workspaces/ws-1/projects/p-1/live-stream/subscribe"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.message").asString().isEqualTo("At least one sessionId is required");
    }
}
