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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import pbouda.jeffrey.local.core.web.ProfileManagerResolver;
import pbouda.jeffrey.profile.manager.FlamegraphManager;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.shared.common.exception.Exceptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static pbouda.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class DifferentialFlamegraphControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager primary;

    @Mock
    ProfileManager secondary;

    @Mock
    FlamegraphManager diffManager;

    @Test
    void listsEvents() {
        when(resolver.resolve("p-1")).thenReturn(primary);
        when(resolver.resolve("p-2")).thenReturn(secondary);
        when(primary.diffFlamegraphManager(secondary)).thenReturn(diffManager);
        when(diffManager.eventSummaries()).thenReturn(List.of());

        MockMvcTester mvc = mockMvcTesterFor(new DifferentialFlamegraphController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/diff/p-2/differential-flamegraph/events"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }

    @Test
    void primaryProfileNotFoundReturns404() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new DifferentialFlamegraphController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/ghost/diff/p-2/differential-flamegraph/events"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
    }
}
