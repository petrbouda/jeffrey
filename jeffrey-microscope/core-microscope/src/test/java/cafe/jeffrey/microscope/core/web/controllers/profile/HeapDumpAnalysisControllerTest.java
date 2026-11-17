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
import cafe.jeffrey.profile.heapdump.model.ConsumerReport;
import cafe.jeffrey.profile.manager.heapdump.analysis.BiggestObjectsAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.ConsumerReportAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.LeakSuspectsAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.StringHeapAnalysis;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class HeapDumpAnalysisControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager profileManager;

    @Mock
    HeapDumpManager heapDumpManager;

    @Test
    void leakSuspectsExistsReportsFalseWhenAbsent() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
        when(heapDumpManager.reportExists(new LeakSuspectsAnalysis())).thenReturn(false);

        MockMvcTester mvc = mockMvcTesterFor(new HeapDumpAnalysisController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/heap/leak-suspects/exists"))
                .hasStatusOk()
                .bodyText().isEqualTo("false");
    }

    @Test
    void profileNotFoundReturns404() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new HeapDumpAnalysisController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/ghost/heap/leak-suspects/exists"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
    }

    /** The path variable's pattern names exactly the reports the controller maps, so neither drifts. */
    @Test
    void routesExactlyTheReportsItMaps() {
        String alternatives = HeapDumpAnalysisController.REPORT
                .substring("{report:".length(), HeapDumpAnalysisController.REPORT.length() - 1);

        assertEquals(HeapDumpAnalysisController.REPORTS.keySet(), Set.of(alternatives.split("\\|")));
    }

    @Test
    void readsAStoredReport() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
        when(heapDumpManager.report(new ConsumerReportAnalysis()))
                .thenReturn(Optional.of(new ConsumerReport(50_000, List.of(), List.of())));

        MockMvcTester mvc = mockMvcTesterFor(new HeapDumpAnalysisController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/heap/consumers"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.totalHeapSize").asNumber().isEqualTo(50_000);
    }

    @Test
    void runsAReportWithItsDefaultTopN() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);

        MockMvcTester mvc = mockMvcTesterFor(new HeapDumpAnalysisController(resolver));

        assertThat(mvc.post().uri("/api/internal/profiles/p-1/heap/biggest-objects/run")).hasStatusOk();
        verify(heapDumpManager).runReport(new BiggestObjectsAnalysis(20));
    }

    @Test
    void runsAReportWithTheTopNTheRequestNames() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);

        MockMvcTester mvc = mockMvcTesterFor(new HeapDumpAnalysisController(resolver));

        assertThat(mvc.post().uri("/api/internal/profiles/p-1/heap/string-analysis/run?topN=7")).hasStatusOk();
        verify(heapDumpManager).runReport(new StringHeapAnalysis(7));
    }

    /** A name outside the report pattern never reaches the handler, so no profile is even looked up. */
    @Test
    void anUnknownReportIsNotRouted() {
        MockMvcTester mvc = mockMvcTesterFor(new HeapDumpAnalysisController(resolver));

        mvc.get().uri("/api/internal/profiles/p-1/heap/no-such-report/exists").exchange();

        verifyNoInteractions(resolver);
    }
}
