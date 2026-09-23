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
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class ProfileFeaturesControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager profileManager;

    @Mock
    ProfileFeaturesManager featuresManager;

    @Mock
    HeapDumpManager heapDumpManager;


    @Test
    void disabledIncludesAiAndHeapDumpWhenUnavailable() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.featuresManager()).thenReturn(featuresManager);
        when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
        when(profileManager.info()).thenReturn(profileInfo(RecordingEventSource.JDK));
        when(featuresManager.getDisabledFeatures()).thenReturn(List.of());
        when(heapDumpManager.heapDumpExists()).thenReturn(false);

        MockMvcTester mvc = mockMvcTesterFor(new ProfileFeaturesController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/features/disabled"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$", v -> assertThat(v).asArray().contains("HEAP_DUMP"));
    }

    @Test
    void profileNotFoundReturns404() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new ProfileFeaturesController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/ghost/features/disabled"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
    }

    private static ProfileInfo profileInfo(RecordingEventSource eventSource) {
        return new ProfileInfo(
                "p-1", "proj-1", "ws-1", "profile", eventSource,
                Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, true, false, "rec-1");
    }
}
