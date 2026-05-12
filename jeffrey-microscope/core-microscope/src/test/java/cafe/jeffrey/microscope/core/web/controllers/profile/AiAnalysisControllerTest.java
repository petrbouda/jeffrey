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
import cafe.jeffrey.profile.ai.duckdb.jfr.service.JfrAnalysisAssistantService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class AiAnalysisControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    JfrAnalysisAssistantService service;

    @Test
    void getsStatus() {
        when(service.isAvailable()).thenReturn(true);
        when(service.getProviderName()).thenReturn("claude");
        when(service.getModelName()).thenReturn("claude-opus-4-7");

        MockMvcTester mvc = mockMvcTesterFor(new AiAnalysisController(resolver, service));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/ai-analysis/status"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.available", v -> assertThat(v).asBoolean().isTrue())
                .hasPathSatisfying("$.provider", v -> assertThat(v).asString().isEqualTo("claude"))
                .hasPathSatisfying("$.model", v -> assertThat(v).asString().isEqualTo("claude-opus-4-7"));
    }
}
