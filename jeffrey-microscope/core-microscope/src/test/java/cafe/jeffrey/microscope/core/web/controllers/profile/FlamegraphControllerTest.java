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
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.RecordingEventSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class FlamegraphControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager profileManager;

    @Mock
    FlamegraphManager flamegraphManager;

    @Test
    void listsEvents() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.info()).thenReturn(FormatTestSupport.profileInfo("p-1", RecordingEventSource.JDK));
        when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
        when(flamegraphManager.eventSummaries()).thenReturn(List.of());

        MockMvcTester mvc = mockMvcTesterFor(new FlamegraphController(resolver, FormatTestSupport.recordingFormats()));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/flamegraph/events"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }

    @Test
    void listsAllEventsWithCategoriesForPprofProfile() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.info()).thenReturn(FormatTestSupport.profileInfo("p-1", RecordingEventSource.PPROF));
        when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
        when(flamegraphManager.allEventSummaries()).thenReturn(List.of(
                new EventSummaryResult("pprof.cpu", "CPU (pprof)", null, null, null),
                new EventSummaryResult("pprof.alloc_space", "Allocated Space (pprof)", null, null, null)));

        MockMvcTester mvc = mockMvcTesterFor(new FlamegraphController(resolver, FormatTestSupport.recordingFormats()));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/flamegraph/events"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$[0].category", v -> assertThat(v).asString().isEqualTo("EXECUTION"))
                .hasPathSatisfying("$[1].category", v -> assertThat(v).asString().isEqualTo("ALLOCATION"));
    }

    @Test
    void profileNotFoundReturns404() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new FlamegraphController(resolver, FormatTestSupport.recordingFormats()));

        assertThat(mvc.get().uri("/api/internal/profiles/ghost/flamegraph/events"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
    }
}
