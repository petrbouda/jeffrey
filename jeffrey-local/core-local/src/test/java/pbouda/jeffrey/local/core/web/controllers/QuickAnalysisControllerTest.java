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

package pbouda.jeffrey.local.core.web.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import pbouda.jeffrey.local.core.manager.qanalysis.QuickAnalysisManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static pbouda.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class QuickAnalysisControllerTest {

    @Mock
    QuickAnalysisManager quickAnalysisManager;

    @Test
    void createsGroup() {
        when(quickAnalysisManager.createGroup("My Group")).thenReturn("group-1");

        MockMvcTester mvc = mockMvcTesterFor(new QuickAnalysisController(quickAnalysisManager));

        assertThat(mvc.post().uri("/api/internal/quick-analysis/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"My Group"}"""))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.groupId").asString().isEqualTo("group-1");
    }

    @Test
    void rejectsBlankGroupName() {
        MockMvcTester mvc = mockMvcTesterFor(new QuickAnalysisController(quickAnalysisManager));

        assertThat(mvc.post().uri("/api/internal/quick-analysis/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":""}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").asString().isEqualTo("Group name is required");
    }

    @Test
    void listsRecordings() {
        when(quickAnalysisManager.listRecordings()).thenReturn(List.of());

        MockMvcTester mvc = mockMvcTesterFor(new QuickAnalysisController(quickAnalysisManager));

        assertThat(mvc.get().uri("/api/internal/quick-analysis/recordings"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$").asArray().isEmpty();
    }
}
