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
import pbouda.jeffrey.profile.ai.oql.model.AiStatusResponse;
import pbouda.jeffrey.profile.ai.oql.service.HeapDumpContextExtractor;
import pbouda.jeffrey.profile.ai.oql.service.OqlAssistantService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static pbouda.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class OqlAssistantControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    OqlAssistantService assistantService;

    @Mock
    HeapDumpContextExtractor contextExtractor;

    @Test
    void getsStatus() {
        when(assistantService.getStatus()).thenReturn(new AiStatusResponse(false, "none", false));

        MockMvcTester mvc = mockMvcTesterFor(new OqlAssistantController(resolver, assistantService, contextExtractor));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/heap/oql-assistant/status"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.provider").asString().isEqualTo("none");
    }
}
