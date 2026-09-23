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
import cafe.jeffrey.profile.manager.IoManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.io.FileForceStats;
import cafe.jeffrey.profile.manager.model.io.IoEndpoint;
import cafe.jeffrey.profile.manager.model.io.IoEndpointTimeline;
import cafe.jeffrey.profile.manager.model.io.IoKind;
import cafe.jeffrey.profile.manager.model.io.IoMetric;
import cafe.jeffrey.profile.manager.model.io.IoTargetFilter;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class IoControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager profileManager;

    @Mock
    IoManager ioManager;

    @Test
    void getsFileForceStats() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.ioManager()).thenReturn(ioManager);
        when(ioManager.fileForce()).thenReturn(new FileForceStats(0, 0, 0, 0, 0, List.of()));

        MockMvcTester mvc = mockMvcTesterFor(new IoController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/io/file/force"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.count").isEqualTo(0);
    }

    @Test
    void timelineScopesToTheRequestedPeer() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.ioManager()).thenReturn(ioManager);
        when(ioManager.timeline(IoKind.SOCKET, IoTargetFilter.ofNullable("db:1521")))
                .thenReturn(new TimeseriesData(new SingleSerie("Bytes Read / sec", List.of(List.of(1L, 512L)))));

        MockMvcTester mvc = mockMvcTesterFor(new IoController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/io/socket/timeline").param("target", "db:1521"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.series[0].name").isEqualTo("Bytes Read / sec");
    }

    @Test
    void timelineWithoutTargetCoversEveryPeer() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.ioManager()).thenReturn(ioManager);
        when(ioManager.timeline(IoKind.SOCKET, IoTargetFilter.all()))
                .thenReturn(TimeseriesData.empty());

        MockMvcTester mvc = mockMvcTesterFor(new IoController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/io/socket/timeline"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.series").asArray().isEmpty();
    }

    @Test
    void getsEndpointTimelines() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.ioManager()).thenReturn(ioManager);
        when(ioManager.endpointTimelines(IoKind.SOCKET, IoMetric.BYTES)).thenReturn(List.of(new IoEndpointTimeline(
                new IoEndpoint("db:1521", 4, 2048, 900, 400),
                new SingleSerie("Bytes / sec", List.of(List.of(1L, 2048L))))));

        MockMvcTester mvc = mockMvcTesterFor(new IoController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/io/socket/endpoint-timelines"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].endpoint.target").isEqualTo("db:1521");
    }

    @Test
    void endpointTimelinesHonoursTheCountMetric() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.ioManager()).thenReturn(ioManager);
        when(ioManager.endpointTimelines(IoKind.SOCKET, IoMetric.COUNT)).thenReturn(List.of(new IoEndpointTimeline(
                new IoEndpoint("cache:6379", 41200, 4096, 900, 400),
                new SingleSerie("Ops / sec", List.of(List.of(1L, 620L))))));

        MockMvcTester mvc = mockMvcTesterFor(new IoController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/io/socket/endpoint-timelines?metric=COUNT"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].serie.name").isEqualTo("Ops / sec");
    }

    @Test
    void profileNotFoundReturns404() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new IoController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/ghost/io/file/force"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
    }
}
