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
import org.mockito.Mock;
import pbouda.jeffrey.local.core.manager.GitHubReleaseChecker;
import pbouda.jeffrey.local.core.web.ControllerTest;

import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pbouda.jeffrey.local.core.web.MockMvcSupport.mockMvcFor;

@ControllerTest
class VersionControllerTest {

    @Mock
    GitHubReleaseChecker releaseChecker;

    @Test
    void returnsCurrentVersion() throws Exception {
        mockMvcFor(new VersionController(releaseChecker))
                .perform(get("/api/internal/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(notNullValue())));
    }

    @Test
    void updateCheckReturns204WhenNoReleaseInfo() throws Exception {
        when(releaseChecker.check(anyString())).thenReturn(Optional.empty());

        mockMvcFor(new VersionController(releaseChecker))
                .perform(get("/api/internal/version/update-check"))
                .andExpect(status().isNoContent());
    }
}
