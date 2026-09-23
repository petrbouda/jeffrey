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
import cafe.jeffrey.profile.manager.ProfileCustomManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.custom.HttpManager;
import cafe.jeffrey.shared.common.exception.Exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class HttpOverviewControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager profileManager;

    @Mock
    ProfileCustomManager customManager;

    @Mock
    HttpManager httpManager;

    @Test
    void getsOverview() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.custom()).thenReturn(customManager);
        when(customManager.httpManager(any())).thenReturn(httpManager);
        when(httpManager.overviewData()).thenReturn(null);

        MockMvcTester mvc = mockMvcTesterFor(new HttpOverviewController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/http/overview"))
                .hasStatusOk();
    }

    @Test
    void profileNotFoundReturns404() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new HttpOverviewController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/ghost/http/overview"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
    }
}
