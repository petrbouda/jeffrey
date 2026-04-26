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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import pbouda.jeffrey.local.core.web.ProfileManagerResolver;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.manager.TimeseriesManager;
import pbouda.jeffrey.timeseries.TimeseriesData;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static pbouda.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class DifferentialTimeseriesControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager primary;

    @Mock
    ProfileManager secondary;

    @Mock
    TimeseriesManager diffMgr;

    @Test
    void generates() {
        when(resolver.resolve("p-1")).thenReturn(primary);
        when(resolver.resolve("p-2")).thenReturn(secondary);
        when(primary.diffTimeseriesManager(secondary)).thenReturn(diffMgr);
        when(diffMgr.timeseries(any())).thenReturn(new TimeseriesData(List.of()));

        MockMvcTester mvc = mockMvcTesterFor(new DifferentialTimeseriesController(resolver));

        assertThat(mvc.post().uri("/api/internal/profiles/p-1/diff/p-2/differential-timeseries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"eventType":"jdk.ObjectAllocationInNewTLAB","useWeight":false,\
                        "excludeNonJavaSamples":false,"excludeIdleSamples":false,\
                        "onlyUnsafeAllocationSamples":false}"""))
                .hasStatusOk();
    }
}
