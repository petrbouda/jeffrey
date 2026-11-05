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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.flamegraph.code.CallSites;
import cafe.jeffrey.flamegraph.code.MethodKey;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.TraceAttributesManager;
import cafe.jeffrey.profile.manager.TraceManager;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationsPage;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import cafe.jeffrey.provider.profile.api.CallSiteSelection;
import cafe.jeffrey.flamegraph.code.CallSiteQuery;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CodeSurfaceMcpToolsTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final String JDBC_QUERY = "jeffrey.JdbcQuery";
    private static final String JDBC_UPDATE = "jeffrey.JdbcUpdate";
    private static final List<String> OWN = List.of("com.acme.");
    private static final TraceOperationId OPERATION = new TraceOperationId(
            "GET /orders", "SERVER", "jeffrey.HttpServerExchange");
    private static final MethodKey FILTER = new MethodKey("com.acme.Filter", "filter");
    private static final MethodKey HANDLER = new MethodKey("com.acme.Controller", "get");

    @Mock
    ProfileManager manager;

    @Mock
    FlamegraphManager flamegraphs;

    @Mock
    TraceManager traces;

    @Mock
    TraceAttributesManager attributes;

    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        when(manager.info()).thenReturn(new ProfileInfo("profile", "project", "workspace", "name",
                RecordingEventSource.JDK, START, START.plusSeconds(60), START, true, false, "recording"));
        when(manager.flamegraphManager()).thenReturn(flamegraphs);
        when(manager.traceManager()).thenReturn(traces);
        when(manager.traceAttributesManager()).thenReturn(attributes);
        when(flamegraphs.allEventSummaries()).thenReturn(List.of(summary(JDBC_QUERY)));
        when(flamegraphs.graphableEventSummaries()).thenReturn(List.of(summary(JDBC_QUERY)));
        when(traces.operations(any())).thenReturn(new TraceOperationsPage(List.of(), 0));
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static EventSummaryResult summary(String code) {
        return new EventSummaryResult(code, code,
                new EventSummaryResult.SingleResult(code, code, null, null, 100, 1000, false, Map.of()), null);
    }

    private CodeSurfaceMcpTools tools() {
        return new CodeSurfaceMcpTools(manager, EVERY_FAMILY);
    }

    @Nested
    class EntryPointShares {

        @Test
        void tiedCandidatesShareTheEventsRatherThanSplitThemAcrossStatementTypes() {
            List<EventSummaryResult> types = List.of(summary(JDBC_QUERY), summary(JDBC_UPDATE));
            when(flamegraphs.allEventSummaries()).thenReturn(types);
            when(flamegraphs.graphableEventSummaries()).thenReturn(types);
            when(flamegraphs.callSites(any(), any())).thenReturn(new CallSites(100, 1000, 0, 0, 0,
                    List.of(), List.of(), List.of(
                            new CallSites.OperationEntry(OPERATION, FILTER, 100, 100),
                            new CallSites.OperationEntry(OPERATION, HANDLER, 100, 100))));
            when(traces.operations(any())).thenReturn(new TraceOperationsPage(List.of(
                    new TraceOperationRow(OPERATION.name(), OPERATION.kind(), OPERATION.eventType(),
                            100, 0, 0, 0, 100, 1000, 10, 10, 10, 10)), 1));

            JsonNode answer = tools().runtimeSurface(OWN).structuredContent();

            JsonNode entries = answer.path("operations").path("operations").get(0).path("entryPoints");
            assertEquals(2, entries.size());
            for (JsonNode entry : entries) {
                assertEquals(200, entry.path("value").asLong());
                assertEquals(100.0, entry.path("sharePct").asDouble(), answer::toString);
            }
        }
    }

    @Nested
    class BoundedStatements {

        @Test
        void completeGroupFiguresAndOmissionsDoNotDependOnReturnedSites() {
            CallSites.CallSite smallSite = site(1, 10);
            CallSites.CallSite largeSite = site(1, 5000);
            CallSites.Group spread = new CallSites.Group("spread", "spread", 6,
                    1000, 10000, 20, List.of(smallSite, smallSite, smallSite));
            CallSites.Group single = new CallSites.Group("single", "single", 6,
                    1, 5000, 1, List.of(largeSite));
            when(flamegraphs.callSites(any(), any())).thenReturn(new CallSites(2000, 50000, 0,
                    100, 40, List.of(smallSite, smallSite, smallSite, largeSite), List.of(spread, single), List.of()));

            JsonNode answer = tools().runtimeSurface(OWN).structuredContent();

            JsonNode statements = answer.path("statements").path("statements");
            assertEquals(2, statements.size());
            assertEquals("spread", statements.get(0).path("statement").asString());
            assertEquals(1000, statements.get(0).path("count").asLong());
            assertEquals(10000, statements.get(0).path("totalNanos").asLong());
            assertEquals(17, statements.get(0).path("omittedCallSites").asLong());
            assertEquals(38, answer.path("statements").path("omittedStatements").asLong());
            ArgumentCaptor<CallSiteQuery> query = ArgumentCaptor.forClass(CallSiteQuery.class);
            verify(flamegraphs).callSites(any(), query.capture());
            assertEquals(new CallSiteSelection.Groups(15, 3), query.getValue().selection());
        }

        @Test
        void equalDisplayedNamesDoNotMergeDistinctWholeValues() {
            CallSites.CallSite site = site(1, 100);
            CallSites.Group first = new CallSites.Group("first-digest", "same displayed prefix", 201,
                    1, 100, 1, List.of(site));
            CallSites.Group second = new CallSites.Group("second-digest", "same displayed prefix", 201,
                    1, 100, 1, List.of(site));
            when(flamegraphs.callSites(any(), any())).thenReturn(new CallSites(2, 200, 0, 2, 2,
                    List.of(site, site), List.of(first, second), List.of()));

            JsonNode answer = tools().runtimeSurface(OWN).structuredContent();

            JsonNode statements = answer.path("statements").path("statements");
            assertEquals(2, statements.size());
            for (JsonNode statement : statements) {
                assertEquals(1, statement.path("count").asLong());
            }
            assertEquals(0, answer.path("statements").path("omittedStatements").asLong());
        }
    }

    private static CallSites.CallSite site(long count, long duration) {
        MethodKey method = new MethodKey("com.acme.Repository", "find");
        return new CallSites.CallSite(method, 10, List.of(new CallSites.PathFrame(method, 10)),
                "find", 4, count, duration, duration, 0, 0, 0, List.of());
    }

    @Nested
    class CallSiteFollowUps {

        @Test
        void omissionCountsUseAllSitesWhileTheRequestedCapReachesSql() {
            CallSites.CallSite site = site(1, 100);
            when(flamegraphs.callSites(any(), any())).thenReturn(new CallSites(100, 10000, 5,
                    25, 20, List.of(site), List.of(), List.of()));

            JsonNode answer = tools().callSites(JDBC_QUERY, OWN, null, null, null, 1, null, null)
                    .structuredContent();

            assertEquals(100, answer.path("totalEvents").asLong());
            assertEquals(24, answer.path("omittedSites").asLong());
            assertEquals(1, answer.path("sites").size());
            ArgumentCaptor<CallSiteQuery> query = ArgumentCaptor.forClass(CallSiteQuery.class);
            verify(flamegraphs).callSites(any(), query.capture());
            assertEquals(new CallSiteSelection.Sites(1), query.getValue().selection());
        }

        @Test
        void methodReadKeepsTheRequestedWindow() {
            MethodKey method = new MethodKey("com.acme.Repository", "find");
            CallSites.CallSite site = new CallSites.CallSite(method, 10,
                    List.of(new CallSites.PathFrame(method, 10)), "find", 4,
                    1, 100, 100, 0, 0, 0, List.of());
            when(flamegraphs.callSites(any(), any())).thenReturn(
                    new CallSites(1, 100, 0, 1, 1, List.of(site), List.of(), List.of()));
            long from = START.plusSeconds(1).toEpochMilli();
            long to = START.plusSeconds(2).toEpochMilli();

            JsonNode answer = tools().callSites(JDBC_QUERY, OWN, null, null, null, null, from, to)
                    .structuredContent();

            JsonNode call = answer.path("followUp").path("nextTools").get(0);
            assertEquals("code_method", call.path("tool").asString());
            assertEquals(from, call.path("arguments").path("startEpochMs").asLong(), answer::toString);
            assertEquals(to, call.path("arguments").path("endEpochMs").asLong(), answer::toString);
        }
    }
}
