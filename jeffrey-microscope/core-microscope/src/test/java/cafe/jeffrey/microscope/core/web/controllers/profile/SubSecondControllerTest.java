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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.SubSecondManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class SubSecondControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager profileManager;

    @Mock
    SubSecondManager subSecondManager;

    @Test
    void generates() {
        ObjectNode node = JsonNodeFactory.instance.objectNode().put("ok", true);
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.info()).thenReturn(new cafe.jeffrey.microscope.model.ProfileInfo(
                "p-1", "project-1", "ws-1", "Demo", RecordingEventSource.JDK,
                Instant.parse("2026-04-01T10:00:00Z"),
                Instant.parse("2026-04-01T10:05:00Z"),
                Instant.parse("2026-04-01T10:10:00Z"),
                true, false, "rec-1"));
        when(profileManager.subSecondManager()).thenReturn(subSecondManager);
        when(subSecondManager.generate(any(), anyBoolean(), any(), anyInt())).thenReturn(node);

        MockMvcTester mvc = mockMvcTesterFor(new SubSecondController(resolver));

        assertThat(mvc.post().uri("/api/internal/profiles/p-1/subsecond")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"eventType":"jdk.ObjectAllocationInNewTLAB","useWeight":false,"timeRange":null}"""))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.ok").asBoolean().isTrue();
    }

    @Test
    void profileNotFoundReturns404() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new SubSecondController(resolver));

        assertThat(mvc.post().uri("/api/internal/profiles/ghost/subsecond")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"eventType":"jdk.ObjectAllocationInNewTLAB","useWeight":false,"timeRange":null}"""))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
    }
}
