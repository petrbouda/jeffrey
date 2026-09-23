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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.microscope.core.mcp.McpProfileContextCache;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager profileManager;

    @Mock
    McpProfileContextCache contextCache;

    @Test
    void getsProfileInfo() {
        ProfileInfo info = sampleProfile();
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.info()).thenReturn(info);

        MockMvcTester mvc = mockMvcTesterFor(new ProfileController(resolver, contextCache));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.id").asString().isEqualTo("p-1");
    }

    @Test
    void unknownProfileReturnsNotFound() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new ProfileController(resolver, contextCache));

        assertThat(mvc.get().uri("/api/internal/profiles/ghost"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
    }

    @Test
    void invalidatesTheMcpContextBeforeDeletingAProfile() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.info()).thenReturn(sampleProfile());
        doAnswer(invocation -> {
            contextCache.acquire("p-1");
            return null;
        }).when(profileManager).delete();
        MockMvcTester mvc = mockMvcTesterFor(new ProfileController(resolver, contextCache));

        assertThat(mvc.delete().uri("/api/internal/profiles/p-1")).hasStatusOk();

        InOrder deletion = inOrder(contextCache, profileManager);
        deletion.verify(contextCache).invalidate("p-1");
        deletion.verify(profileManager).delete();
        deletion.verify(contextCache).acquire("p-1");
        deletion.verify(contextCache).invalidate("p-1");
    }

    static ProfileInfo sampleProfile() {
        return new ProfileInfo(
                "p-1",
                "project-1",
                "ws-1",
                "Demo profile",
                RecordingEventSource.JDK,
                Instant.parse("2026-04-01T10:00:00Z"),
                Instant.parse("2026-04-01T10:05:00Z"),
                Instant.parse("2026-04-01T10:10:00Z"),
                true,
                false,
                "rec-1");
    }
}
