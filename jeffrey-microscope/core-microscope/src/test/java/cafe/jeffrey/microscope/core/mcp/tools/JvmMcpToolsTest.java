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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.common.analysis.AnalysisResult;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.profile.manager.AutoAnalysisManager;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileConfigurationManager;
import cafe.jeffrey.profile.manager.ExceptionsManager;
import cafe.jeffrey.profile.manager.ClassLoadingManager;
import cafe.jeffrey.profile.manager.SystemResourcesManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.profile.manager.VmOperationManager;
import cafe.jeffrey.profile.manager.gc.GarbageCollectionManager;
import cafe.jeffrey.profile.manager.model.gc.GCEfficiency;
import cafe.jeffrey.profile.manager.model.gc.GCHeader;
import cafe.jeffrey.profile.manager.model.gc.GCOverviewData;
import cafe.jeffrey.profile.manager.model.gc.GCPauseDistribution;
import cafe.jeffrey.profile.manager.model.gc.ManualGCCalls;
import cafe.jeffrey.profile.manager.model.vmoperation.SafepointLatencyData;
import cafe.jeffrey.profile.manager.model.vmoperation.SafepointOffender;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOperationStat;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOverview;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.EventTypeName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JvmMcpToolsTest {

    /**
     * The tools build a UI link off the incoming request, the way ProfileMcpTools#link does.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }


    private static final long MILLI_IN_NANOS = 1_000_000L;

    @Mock
    ProfileManager profileManager;

    @Mock
    FlamegraphManager flamegraphManager;

    @Mock
    GarbageCollectionManager gcManager;

    @Mock
    VmOperationManager vmOperationManager;

    @Mock
    AutoAnalysisManager autoAnalysisManager;

    @Mock
    ExceptionsManager exceptionsManager;

    @Mock
    ClassLoadingManager classLoadingManager;

    @Mock
    SystemResourcesManager systemResourcesManager;

    @Mock
    cafe.jeffrey.profile.manager.SecurityManager securityManager;

    @Mock
    ProfileConfigurationManager configurationManager;

    private JvmMcpTools tools() {
        // Every rendered section carries a link to the page it is drawn on, built from the profile id.
        when(profileManager.info()).thenReturn(new ProfileInfo(
                "p-1", "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        return new JvmMcpTools(profileManager);
    }

    /**
     * A recording holds only what the profiler was told to capture, and every section is gated on
     * that, so each test says which event types this recording carries.
     */
    private void recorded(String... eventTypes) {
        when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
        when(profileManager.gcManager()).thenReturn(gcManager);
        when(profileManager.vmOperationManager()).thenReturn(vmOperationManager);
        when(profileManager.autoAnalysisManager()).thenReturn(autoAnalysisManager);
        when(profileManager.profileConfigurationManager()).thenReturn(configurationManager);
        when(profileManager.exceptionsManager()).thenReturn(exceptionsManager);
        when(profileManager.classLoadingManager()).thenReturn(classLoadingManager);
        when(profileManager.systemResourcesManager()).thenReturn(systemResourcesManager);
        when(profileManager.securityManager()).thenReturn(securityManager);

        when(flamegraphManager.allEventSummaries()).thenReturn(
                List.of(eventTypes).stream()
                        .map(JvmMcpToolsTest::summary)
                        .toList());
    }

    private static EventSummaryResult summary(String eventType) {
        return new EventSummaryResult(new EventSummary(
                eventType, eventType, null, null, 1, 0, false, false, List.of(), null, null));
    }

    @Nested
    class Sections {

        @Test
        void marksASectionAvailableWhenTheRecordingCarriesAnyOfItsEvents() {
            recorded(EventTypeName.GARBAGE_COLLECTION);

            String result = tools().sections();

            assertTrue(result.contains("\"id\":\"gc\",\"title\":\"Garbage Collection\",\"available\":true"));
            assertTrue(result.contains("\"id\":\"safepoints\",\"title\":\"Safepoints and VM Operations\",\"available\":false"));
        }

        /**
         * The events a section needs travel with it, so a reader who finds a section unavailable is
         * told what to ask the profiler for next time rather than left guessing.
         */
        @Test
        void namesTheEventsEachSectionIsBuiltFrom() {
            recorded();

            assertTrue(tools().sections().contains(EventTypeName.SAFEPOINT_LATENCY));
        }

        /**
         * Auto analysis runs over the recording file rather than over parsed events, so no particular
         * event has to be present for it to be answerable.
         */
        @Test
        void reportsAutoAnalysisAsAvailableWithoutAnyEvents() {
            recorded();

            assertTrue(tools().sections().contains("\"id\":\"autoAnalysis\",\"title\":\"Auto Analysis\",\"available\":true"));
        }
    }

    @Nested
    class NotRecorded {

        /**
         * A dashboard rendered from events the profiler never captured is a page of zeroes, which
         * reads like a finding. The refusal has to happen before the manager is touched.
         */
        @Test
        void refusesASectionTheRecordingHasNoEventsForWithoutRenderingIt() {
            recorded(EventTypeName.EXECUTION_SAMPLE);

            String result = tools().gc();

            assertTrue(result.contains("no data for the Garbage Collection section"));
            assertTrue(result.contains(EventTypeName.GARBAGE_COLLECTION));
            verify(gcManager, never()).overviewData();
        }

        @Test
        void pointsTheReaderAtTheDiscoveryTool() {
            recorded();

            assertTrue(tools().safepoints().contains("jvm_sections"));
        }
    }

    @Nested
    class NextSteps {

        /**
         * The figures alone do not say what to do next, and the tool description that does was read
         * many turns earlier. The exports have always opened with their own reading instructions;
         * a dashboard carries the same thing beside the numbers it is read with.
         */
        @Test
        void sendsAGcReaderOnToTheAllocationFlamegraph() {
            recorded(EventTypeName.GARBAGE_COLLECTION);
            when(gcManager.garbageCollectorType()).thenReturn(GarbageCollectorType.G1);
            when(gcManager.overviewData()).thenReturn(Gc.overview());

            String result = tools().gc();

            assertTrue(result.contains("jdk.ObjectAllocationSample"));
            assertTrue(result.contains("jvm_safepoints"));
        }

        /**
         * Routing, never a verdict: the lines are unconditional, so nothing here claims that this
         * particular recording spent too long collecting.
         */
        @Test
        void carriesTheSameRoutingWhateverTheFiguresSay() {
            recorded(EventTypeName.GARBAGE_COLLECTION);
            when(gcManager.garbageCollectorType()).thenReturn(GarbageCollectorType.G1);
            when(gcManager.overviewData()).thenReturn(Gc.overview());

            String result = tools().gc();

            assertTrue(result.contains("\"nextSteps\""));
            assertTrue(result.contains("\"section\":\"gc\""));
            assertTrue(result.contains("\"dashboard\""));
        }

        @Test
        void tellsAConfigurationReaderToPreferTheseValuesOverAManifest() {
            recorded(EventTypeName.JVM_INFORMATION);
            when(configurationManager.configuration()).thenReturn(Configuration.configuration());

            assertTrue(tools().configuration(null).contains("deployment manifest"));
        }
    }

    @Nested
    class Gc {

        /**
         * The whole reason this is a tool: pause figures come from sumOfPauses and longestPause, the
         * fields a hand-written query passes over in favour of the event's duration.
         */
        @Test
        void reportsThePauseBudgetInMilliseconds() {
            recorded(EventTypeName.GARBAGE_COLLECTION);
            when(gcManager.garbageCollectorType()).thenReturn(GarbageCollectorType.G1);
            when(gcManager.overviewData()).thenReturn(overview());

            String result = tools().gc();

            assertTrue(result.contains("\"collector\":\"G1\""));
            assertTrue(result.contains("\"totalPauseMillis\":250.0"));
            assertTrue(result.contains("\"longestPauseMillis\":40.0"));
            assertTrue(result.contains("\"collections\":12"));
        }

        @Test
        void carriesTheManualCollectionCountsThatExplainAnUnexpectedFullGc() {
            recorded(EventTypeName.GARBAGE_COLLECTION);
            when(gcManager.garbageCollectorType()).thenReturn(GarbageCollectorType.G1);
            when(gcManager.overviewData()).thenReturn(overview());

            String result = tools().gc();

            assertTrue(result.contains("\"systemGcCalls\":3"));
            assertTrue(result.contains("\"diagnosticGcCalls\":1"));
        }

        static GCOverviewData overview() {
            GCHeader header = new GCHeader(
                    12, 9, 2, 1,
                    40 * MILLI_IN_NANOS, 30 * MILLI_IN_NANOS, 35 * MILLI_IN_NANOS,
                    2048, 170, BigDecimal.valueOf(99), BigDecimal.ONE,
                    250 * MILLI_IN_NANOS, BigDecimal.TEN,
                    new ManualGCCalls(0, 3, 1));

            return new GCOverviewData(
                    header,
                    List.of(),
                    new GCPauseDistribution(List.of()),
                    new GCEfficiency(0, 0, BigDecimal.valueOf(99), BigDecimal.ONE),
                    List.of(),
                    List.of());
        }
    }

    @Nested
    class Safepoints {

        /**
         * The thread state is what turns a slow thread into a diagnosis, so it has to survive into
         * the rendered dashboard rather than being aggregated away.
         */
        @Test
        void namesTheOffendingThreadsWithTheStateTheyWereIn() {
            recorded(EventTypeName.SAFEPOINT_LATENCY);
            when(vmOperationManager.overview()).thenReturn(
                    new VmOverview(4, 12 * MILLI_IN_NANOS, 9 * MILLI_IN_NANOS, "G1CollectForAllocation",
                            true, true, true));
            when(vmOperationManager.vmOperations()).thenReturn(List.of(
                    new VmOperationStat("G1CollectForAllocation", 4, 12 * MILLI_IN_NANOS,
                            9 * MILLI_IN_NANOS, true, true)));
            when(vmOperationManager.safepointOffenders()).thenReturn(new SafepointLatencyData(
                    List.of(new SafepointOffender("worker-3", "_thread_in_native", 4,
                            8 * MILLI_IN_NANOS, 7 * MILLI_IN_NANOS, 20 * MILLI_IN_NANOS)),
                    1, 8 * MILLI_IN_NANOS, 20 * MILLI_IN_NANOS));

            String result = tools().safepoints();

            assertTrue(result.contains("\"threadName\":\"worker-3\""));
            assertTrue(result.contains("\"threadState\":\"_thread_in_native\""));
            assertTrue(result.contains("\"totalSafepointPauseMillis\":12.0"));
            assertTrue(result.contains("\"longestPauseOperation\":\"G1CollectForAllocation\""));
        }
    }

    @Nested
    class AutoAnalysis {

        /**
         * Generating it loads the whole recording through the JMC toolkit, so an empty cache is
         * reported rather than quietly paid for inside an MCP call.
         */
        @Test
        void saysWhereToComputeItRatherThanReturningNothing() {
            recorded();
            when(autoAnalysisManager.analysisResults()).thenReturn(List.of());

            String result = tools().autoAnalysis(null);

            assertTrue(result.contains("has not been computed"));
            assertTrue(result.contains("profiles_link"));
            assertFalse(result.contains("\"findings\""));
        }

        /**
         * Computing is asked for rather than assumed, but the tool that refuses must also say how.
         */
        @Test
        void computesItOnRequest() {
            recorded();
            when(autoAnalysisManager.analysisResults())
                    .thenReturn(List.of())
                    .thenReturn(List.of(new AutoAnalysisResult(
                            "Long GC Pauses", AnalysisResult.Severity.WARNING,
                            "Pauses above 100ms were observed", "GC pauses are long",
                            "Consider a larger young generation", "78")));

            String result = tools().autoAnalysis(true);

            verify(autoAnalysisManager).generate();
            assertTrue(result.contains("\"rule\":\"Long GC Pauses\""));
        }

        @Test
        void refusesToComputeWhenTheInstallationWithholdsIt() {
            recorded();
            when(autoAnalysisManager.analysisResults()).thenReturn(List.of());

            String result = new JvmMcpTools(profileManager, false).autoAnalysis(true);

            assertTrue(result.contains("compute.enabled"));
            verify(autoAnalysisManager, never()).generate();
        }

        @Test
        void returnsTheCachedFindingsWithTheirSeverity() {
            recorded();
            when(autoAnalysisManager.analysisResults()).thenReturn(List.of(new AutoAnalysisResult(
                    "Long GC Pauses", AnalysisResult.Severity.WARNING,
                    "Pauses above 100ms were observed", "GC pauses are long",
                    "Consider a larger young generation", "78")));

            String result = tools().autoAnalysis(null);

            assertTrue(result.contains("\"rule\":\"Long GC Pauses\""));
            assertTrue(result.contains("\"severity\":\"WARNING\""));
            assertTrue(result.contains("\"findingCount\":1"));
        }
    }

    @Nested
    class Configuration {

        @Test
        void listsTheSectionNamesWhenNoneIsNamed() {
            recorded(EventTypeName.JVM_INFORMATION);
            when(configurationManager.configuration()).thenReturn(configuration());

            String result = tools().configuration(null);

            assertTrue(result.contains("JVM Information"));
            assertTrue(result.contains("GC Heap Configuration"));
        }

        @Test
        void returnsOneSectionsValuesWhenItIsNamed() {
            recorded(EventTypeName.JVM_INFORMATION);
            when(configurationManager.configuration()).thenReturn(configuration());

            String result = tools().configuration("GC Heap Configuration");

            assertTrue(result.contains("Maximum Heap Size"));
            assertFalse(result.contains("JVM Version"));
        }

        @Test
        void refusesASectionNameTheProfileDoesNotHave() {
            recorded(EventTypeName.JVM_INFORMATION);
            when(configurationManager.configuration()).thenReturn(configuration());

            String result = tools().configuration("Nonexistent");

            assertTrue(result.contains("no configuration section named 'Nonexistent'"));
        }

        static JsonNode configuration() {
            return Json.readObjectNode("""
                    {
                      "JVM Information": {"JVM Version": "25.0.1"},
                      "GC Heap Configuration": {"Maximum Heap Size": 4096}
                    }
                    """);
        }
    }

    /**
     * The areas the UI has always answered and the MCP surface did not: exceptions, class loading,
     * system and host, security, and the GC pages beneath the overview.
     */
    @Nested
    class NewSections {

        @Test
        void everyNewSectionIsListedWithTheEventsItNeeds() {
            recorded();

            String result = tools().sections();

            assertTrue(result.contains("\"id\":\"exceptions\""));
            assertTrue(result.contains("\"id\":\"classLoading\""));
            assertTrue(result.contains("\"id\":\"system\""));
            assertTrue(result.contains("\"id\":\"security\""));
            assertTrue(result.contains("\"id\":\"gcDetail\""));
        }

        /**
         * A section whose events were never captured is refused with the event types it needed, so an
         * absence never arrives as a page of zeroes.
         */
        @Test
        void refusesASectionWhoseEventsWereNotRecorded() {
            recorded();

            assertTrue(tools().exceptions().contains("no data"));
            assertTrue(tools().classLoading().contains("no data"));
            assertTrue(tools().system().contains("no data"));
            assertTrue(tools().security().contains("no data"));
        }

        @Test
        void namesTheEventTypesASectionNeededWhenRefusing() {
            recorded();

            assertTrue(tools().exceptions().contains("jdk.ExceptionStatistics"));
        }
    }

    @Nested
    class GcDetail {

        @Test
        void listsThePagesWhenNoneIsAskedFor() {
            recorded("jdk.GarbageCollection");

            String result = tools().gcDetail(null);

            assertTrue(result.contains("tenuring"));
            assertTrue(result.contains("zgc"));
            assertTrue(result.contains("plab"));
        }

        @Test
        void refusesAPageThatDoesNotExistNamingTheOnesThatDo() {
            recorded("jdk.GarbageCollection");

            String result = tools().gcDetail("nonsense");

            assertTrue(result.startsWith("Error: "));
            assertTrue(result.contains("tenuring"));
        }

        @Test
        void refusesEveryPageWhenNoCollectionWasRecorded() {
            recorded();

            assertTrue(tools().gcDetail("tenuring").contains("no data"));
        }
    }

}
