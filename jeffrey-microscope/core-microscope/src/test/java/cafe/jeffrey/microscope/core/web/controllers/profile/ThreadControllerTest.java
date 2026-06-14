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
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.ThreadManager;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class ThreadControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager profileManager;

    @Mock
    ThreadManager threadManager;

    @Test
    void getsActiveThreadsTimeseries() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.threadManager()).thenReturn(threadManager);
        when(threadManager.activeThreadsSerie()).thenReturn(new SingleSerie("threads", List.of()));

        MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread/timeseries"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.name").asString().isEqualTo("threads");
    }

    @Test
    void getsThreadDumpAnalysis() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.threadManager()).thenReturn(threadManager);
        when(threadManager.threadDumpAnalysis()).thenReturn(new ThreadDumpAnalysis(
                new ThreadDumpAnalysis.Header(0, 0, 0, 0, 0, 0),
                List.of(), new TimeseriesData(List.of()), List.of(), List.of(), List.of(), List.of(),
                new ThreadDumpAnalysis.Heatmap(List.of(), List.of())));

        MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread/dumps")).hasStatusOk();
    }

    @Test
    void getsSingleThreadDump() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.threadManager()).thenReturn(threadManager);
        when(threadManager.threadDump(0)).thenReturn(new ParsedDump(0, List.of(), List.of(), ""));

        MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/thread/dumps/0")).hasStatusOk();
    }

    @Test
    void profileNotFoundReturns404() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new ThreadController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/ghost/thread/timeseries"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
    }
}
