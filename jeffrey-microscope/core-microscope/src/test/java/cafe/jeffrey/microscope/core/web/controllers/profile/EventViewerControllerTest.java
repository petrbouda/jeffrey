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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.EventViewerManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class EventViewerControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager profileManager;

    @Mock
    EventViewerManager eventViewerManager;

    @Test
    void listsEventTypes() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.eventViewerManager()).thenReturn(eventViewerManager);
        when(eventViewerManager.eventTypes()).thenReturn(List.of());

        MockMvcTester mvc = mockMvcTesterFor(new EventViewerController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/viewer/events/types"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }

    @Test
    void profileNotFoundReturns404() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new EventViewerController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/ghost/viewer/events/types"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
    }
}
