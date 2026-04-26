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

package pbouda.jeffrey.local.core.web.controllers.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import pbouda.jeffrey.local.core.web.ProfileManagerResolver;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.manager.SubSecondManager;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static pbouda.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;

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
        when(profileManager.info()).thenReturn(new pbouda.jeffrey.shared.common.model.ProfileInfo(
                "p-1", "project-1", "ws-1", "Demo", RecordingEventSource.JDK,
                Instant.parse("2026-04-01T10:00:00Z"),
                Instant.parse("2026-04-01T10:05:00Z"),
                Instant.parse("2026-04-01T10:10:00Z"),
                true, false, "rec-1"));
        when(profileManager.subSecondManager()).thenReturn(subSecondManager);
        when(subSecondManager.generate(any(), anyBoolean(), any())).thenReturn(node);

        MockMvcTester mvc = mockMvcTesterFor(new SubSecondController(resolver));

        assertThat(mvc.post().uri("/api/internal/profiles/p-1/subsecond")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"eventType":"jdk.ObjectAllocationInNewTLAB","useWeight":false,"timeRange":null}"""))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.ok").asBoolean().isTrue();
    }
}
