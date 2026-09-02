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

import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.TraceManager;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationsPage;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.provider.profile.api.TraceOperationListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationSortField;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.SpanInterval;
import cafe.jeffrey.shared.common.model.SpanScope;
import cafe.jeffrey.shared.common.model.Type;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;

import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TracesControllerTest {

    private static final String PROFILE = "p-1";
    private static final String OPERATIONS_URI = "/api/internal/profiles/p-1/traces/operations";
    private static final String SPAN_FLAMEGRAPH_AI_EXPORT_URI =
            "/api/internal/profiles/p-1/traces/00000000000000ff/spans/0000000000000010/flamegraph/ai-export";
    private static final long TRACE_ID = 0xff;
    private static final long SPAN_ID = 0x10;
    private static final String SPAN_FLAMEGRAPH_REQUEST = """
            {
              "selfOnly": true,
              "eventType": "jdk.ExecutionSample",
              "useThreadMode": false,
              "useWeight": false,
              "excludeNonJavaSamples": false,
              "excludeIdleSamples": false,
              "onlyUnsafeAllocationSamples": false,
              "components": "FLAMEGRAPH_ONLY"
            }
            """;
    private static final ProfileInfo PROFILE_INFO = new ProfileInfo(
            PROFILE, "proj-1", "ws-1", "profile", RecordingEventSource.JDK,
            Instant.parse("2025-01-15T10:00:00Z"), Instant.parse("2025-01-15T10:01:00Z"),
            Instant.parse("2025-01-15T10:02:00Z"), true, false, "rec-1");

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager profileManager;

    @Mock
    TraceManager traceManager;

    @Mock
    FlamegraphManager flamegraphManager;

    @Mock
    JfrFlamegraphPanelProvider panelProvider;

    private MockMvcTester mvcWithTraceManager() {
        when(resolver.resolve(PROFILE)).thenReturn(profileManager);
        when(profileManager.traceManager()).thenReturn(traceManager);
        when(traceManager.operations(any())).thenReturn(new TraceOperationsPage(List.of(), 0));
        return mockMvcTesterFor(new TracesController(resolver, panelProvider));
    }

    private TraceOperationListQuery capturedQuery() {
        ArgumentCaptor<TraceOperationListQuery> captor =
                ArgumentCaptor.forClass(TraceOperationListQuery.class);
        verify(traceManager).operations(captor.capture());
        return captor.getValue();
    }

    @Test
    void aBareRequestGetsTheHistoricalDefaults() {
        MockMvcTester mvc = mvcWithTraceManager();

        assertThat(mvc.get().uri(OPERATIONS_URI))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.totalMatching").asNumber().isEqualTo(0);

        TraceOperationListQuery query = capturedQuery();
        assertEquals(TraceOperationSortField.TOTAL_TIME, query.sort());
        assertEquals(100, query.limit());
        assertEquals(0, query.offset());
        assertNull(query.nameContains(), "no search param means no narrowing");
    }

    @Test
    void theLimitIsBoundedNotTrusted() {
        MockMvcTester mvc = mvcWithTraceManager();

        assertThat(mvc.get().uri(OPERATIONS_URI + "?limit=999999")).hasStatusOk();

        assertEquals(10_000, capturedQuery().limit());
    }

    @Test
    void profileNotFoundReturns404() {
        when(resolver.resolve("ghost")).thenThrow(Exceptions.profileNotFound("ghost"));

        MockMvcTester mvc = mockMvcTesterFor(new TracesController(resolver, panelProvider));

        assertThat(mvc.get().uri("/api/internal/profiles/ghost/traces/operations"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code").asString().isEqualTo("PROFILE_NOT_FOUND");
    }

    @Nested
    class SpanFlamegraphAiExport {

        private MockMvcTester mvcWithSpanIntervals(List<SpanInterval> intervals) {
            when(resolver.resolve(PROFILE)).thenReturn(profileManager);
            when(profileManager.traceManager()).thenReturn(traceManager);
            when(traceManager.spanIntervals(TRACE_ID, SPAN_ID, true)).thenReturn(intervals);
            return mockMvcTesterFor(new TracesController(resolver, panelProvider));
        }

        @Test
        void exportsTheFlamegraphScopedToTheSpan() {
            when(profileManager.info()).thenReturn(PROFILE_INFO);
            when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
            when(flamegraphManager.generateAiExport(any())).thenReturn("# How to read this profile");
            SpanInterval interval = new SpanInterval(7, 1_000, 2_000);
            MockMvcTester mvc = mvcWithSpanIntervals(List.of(interval));

            assertThat(mvc.post().uri(SPAN_FLAMEGRAPH_AI_EXPORT_URI)
                    .contentType("application/json")
                    .accept("text/markdown")
                    .content(SPAN_FLAMEGRAPH_REQUEST))
                    .hasStatusOk()
                    .hasContentType("text/markdown;charset=UTF-8")
                    .hasBodyTextEqualTo("# How to read this profile");

            ArgumentCaptor<GraphParameters> captor = ArgumentCaptor.forClass(GraphParameters.class);
            verify(flamegraphManager).generateAiExport(captor.capture());
            GraphParameters params = captor.getValue();
            assertEquals(Type.EXECUTION_SAMPLE, params.eventType());
            assertEquals(SpanScope.of(List.of(interval)), params.spanScope(),
                    "the export must cover exactly the windows the drawn graph covers");
            assertNotNull(params.timeRange(), "full-profile range, narrowed by the scope");
        }

        @Test
        void aSpanWithNoWindowIs404() {
            MockMvcTester mvc = mvcWithSpanIntervals(List.of());

            // The error body is JSON, so a client accepting only Markdown could not be told at all.
            assertThat(mvc.post().uri(SPAN_FLAMEGRAPH_AI_EXPORT_URI)
                    .contentType("application/json")
                    .accept("text/markdown", "application/json")
                    .content(SPAN_FLAMEGRAPH_REQUEST))
                    .hasStatus(404)
                    .bodyJson()
                    .extractingPath("$.code").asString().isEqualTo("RESOURCE_NOT_FOUND");

            verify(profileManager, never()).flamegraphManager();
        }
    }

}
