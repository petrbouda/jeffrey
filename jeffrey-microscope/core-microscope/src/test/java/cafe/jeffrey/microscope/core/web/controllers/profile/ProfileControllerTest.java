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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager profileManager;

    @Test
    void getsProfileInfo() {
        ProfileInfo info = sampleProfile();
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.info()).thenReturn(info);

        MockMvcTester mvc = mockMvcTesterFor(new ProfileController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.id").asString().isEqualTo("p-1");
    }

    @Test
    void unknownProfileReturnsNotFound() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new ProfileController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/ghost"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
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
