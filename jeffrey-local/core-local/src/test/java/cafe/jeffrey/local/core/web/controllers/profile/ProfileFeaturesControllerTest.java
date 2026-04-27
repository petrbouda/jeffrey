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

package cafe.jeffrey.local.core.web.controllers.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.local.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.ai.oql.duckdb.service.JfrAnalysisAssistantService;
import cafe.jeffrey.profile.manager.HeapDumpManager;
import cafe.jeffrey.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;

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

    @Mock
    JfrAnalysisAssistantService assistantService;

    @Test
    void disabledIncludesAiAndHeapDumpWhenUnavailable() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.featuresManager()).thenReturn(featuresManager);
        when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
        when(featuresManager.getDisabledFeatures()).thenReturn(List.of());
        when(assistantService.isAvailable()).thenReturn(false);
        when(heapDumpManager.heapDumpExists()).thenReturn(false);

        MockMvcTester mvc = mockMvcTesterFor(new ProfileFeaturesController(resolver, assistantService));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/features/disabled"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$", v -> assertThat(v).asArray().contains("AI_ANALYSIS", "HEAP_DUMP"));
    }

    @Test
    void profileNotFoundReturns404() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new ProfileFeaturesController(resolver, assistantService));

        assertThat(mvc.get().uri("/api/internal/profiles/ghost/features/disabled"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
    }
}
