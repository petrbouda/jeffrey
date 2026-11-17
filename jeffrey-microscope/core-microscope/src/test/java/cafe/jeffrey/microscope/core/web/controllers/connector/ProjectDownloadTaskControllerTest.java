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

package cafe.jeffrey.microscope.core.web.controllers.connector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.recordings.core.download.DownloadTaskRegistry;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class ProjectDownloadTaskControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    ProjectManagerResolver resolver;

    private final DownloadTaskRegistry taskRegistry = new DownloadTaskRegistry(CLOCK);

    @AfterEach
    void closeRegistry() {
        taskRegistry.close();
    }

    @Test
    void unknownTaskReturnsBadRequest() {
        MockMvcTester mvc = mockMvcTesterFor(new ProjectDownloadTaskController(resolver, taskRegistry));

        // No task is registered for "ghost-task" — controller throws
        // Exceptions.invalidRequest, mapped to 400.
        assertThat(mvc.get().uri("/api/internal/connector/workspaces/ws-1/projects/p-1/download/ghost-task/status"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("INVALID_REQUEST");
    }

    @Test
    void cancelUnknownTaskReturns404() {
        MockMvcTester mvc = mockMvcTesterFor(new ProjectDownloadTaskController(resolver, taskRegistry));

        assertThat(mvc.delete().uri("/api/internal/connector/workspaces/ws-1/projects/p-1/download/ghost-task"))
                .hasStatus(404);
    }
}
