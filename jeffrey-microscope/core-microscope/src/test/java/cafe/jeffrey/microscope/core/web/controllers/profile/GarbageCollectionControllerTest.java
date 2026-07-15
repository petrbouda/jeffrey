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
import cafe.jeffrey.profile.manager.gc.GarbageCollectionManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.gc.GCTimeseriesType;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData.G1Header;
import cafe.jeffrey.profile.manager.model.gc.zgc.ZgcAnalysisData;
import cafe.jeffrey.profile.manager.model.gc.zgc.ZgcAnalysisData.ZgcHeader;
import cafe.jeffrey.profile.manager.model.gc.finalizer.FinalizersData;
import cafe.jeffrey.profile.manager.model.gc.tables.StringSymbolTablesData;
import cafe.jeffrey.profile.manager.model.gc.tuning.ReferenceProcessingData;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class GarbageCollectionControllerTest {

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager profileManager;

    @Mock
    GarbageCollectionManager gcManager;

    @Test
    void getsTimeseries() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.gcManager()).thenReturn(gcManager);
        when(gcManager.timeseries(any(GCTimeseriesType.class))).thenReturn(new TimeseriesData(List.of()));

        MockMvcTester mvc = mockMvcTesterFor(new GarbageCollectionController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/gc/timeseries?timeseriesType=COUNT"))
                .hasStatusOk();
    }

    @Test
    void getsG1Analysis() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.gcManager()).thenReturn(gcManager);
        when(gcManager.g1Analysis()).thenReturn(emptyG1Analysis());

        MockMvcTester mvc = mockMvcTesterFor(new GarbageCollectionController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/gc/g1")).hasStatusOk();
    }

    @Test
    void getsZgcAnalysis() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.gcManager()).thenReturn(gcManager);
        when(gcManager.zgcAnalysis()).thenReturn(emptyZgcAnalysis());

        MockMvcTester mvc = mockMvcTesterFor(new GarbageCollectionController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/gc/zgc")).hasStatusOk();
    }

    @Test
    void getsStringSymbolTables() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.gcManager()).thenReturn(gcManager);
        when(gcManager.stringSymbolTables()).thenReturn(new StringSymbolTablesData(
                new StringSymbolTablesData.Header(0, 0, 0, 0), TimeseriesData.empty(), TimeseriesData.empty(),
                new StringSymbolTablesData.Deduplication(0, 0, 0, 0, 0, TimeseriesData.empty())));

        MockMvcTester mvc = mockMvcTesterFor(new GarbageCollectionController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/gc/string-symbol-tables")).hasStatusOk();
    }

    @Test
    void getsFinalizers() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.gcManager()).thenReturn(gcManager);
        when(gcManager.finalizers()).thenReturn(new FinalizersData(
                new FinalizersData.Header(0, 0, 0), List.of()));

        MockMvcTester mvc = mockMvcTesterFor(new GarbageCollectionController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/gc/finalizers")).hasStatusOk();
    }

    @Test
    void getsReferenceProcessing() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.gcManager()).thenReturn(gcManager);
        when(gcManager.referenceProcessing()).thenReturn(new ReferenceProcessingData(
                new ReferenceProcessingData.Header(0, 0, 0, null),
                List.of(), new TimeseriesData(List.of()), List.of()));

        MockMvcTester mvc = mockMvcTesterFor(new GarbageCollectionController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/gc/reference-processing")).hasStatusOk();
    }

    @Test
    void getsPhaseParallelBreakdown() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.gcManager()).thenReturn(gcManager);
        when(gcManager.phaseParallel()).thenReturn(List.of());

        MockMvcTester mvc = mockMvcTesterFor(new GarbageCollectionController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/gc/phase-parallel")).hasStatusOk();
    }

    private static G1AnalysisData emptyG1Analysis() {
        return new G1AnalysisData(
                new G1Header(0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(), TimeseriesData.empty(), List.of(), List.of(), List.of(),
                TimeseriesData.empty(), List.of(), List.of(), List.of());
    }

    private static ZgcAnalysisData emptyZgcAnalysis() {
        return new ZgcAnalysisData(
                new ZgcHeader(0, 0, 0, 0, 0, 0, 0),
                TimeseriesData.empty(), List.of(), List.of(), List.of(),
                TimeseriesData.empty(), List.of(), List.of());
    }

    @Test
    void getsPlabStatistics() {
        when(resolver.resolve("p-1")).thenReturn(profileManager);
        when(profileManager.gcManager()).thenReturn(gcManager);
        when(gcManager.plabStatistics()).thenReturn(List.of());

        MockMvcTester mvc = mockMvcTesterFor(new GarbageCollectionController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/p-1/gc/plab-statistics")).hasStatusOk();
    }

    @Test
    void profileNotFoundReturns404() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new GarbageCollectionController(resolver));

        assertThat(mvc.get().uri("/api/internal/profiles/ghost/gc/timeseries?timeseriesType=COUNT"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
    }
}
