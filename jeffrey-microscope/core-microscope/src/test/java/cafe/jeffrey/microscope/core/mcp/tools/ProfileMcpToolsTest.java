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

import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.profile.common.analysis.AnalysisResult;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.AutoAnalysisManager;
import cafe.jeffrey.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.SamplerHealthManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.provider.profile.api.CpuTimeSampleLoss;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileMcpToolsTest {

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


    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    ProfileManager profileManager;

    @Mock
    ProfileFeaturesManager featuresManager;

    @Mock
    FlamegraphManager flamegraphManager;

    @Mock
    HeapDumpManager heapDumpManager;

    @Mock
    RecordingCommitResolver recordingCommitResolver;

    @Mock
    SamplerHealthManager samplerHealthManager;

    @Mock
    AutoAnalysisManager autoAnalysisManager;

    private ProfileMcpTools tools() {
        return new ProfileMcpTools(
                profileManager,
                recordingCommitResolver,
                new JfrFlamegraphPanelProvider(),
                new StackSampleFlamegraphPanelProvider());
    }

    private void stubProfile(RecordingEventSource eventSource) {
        when(profileManager.info()).thenReturn(new ProfileInfo(
                "p-1", "proj-1", "ws-1", "Checkout run", eventSource,
                START, START.plusSeconds(120), START, true, false, "rec-1"));
        when(profileManager.featuresManager()).thenReturn(featuresManager);
        when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
        when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
        when(profileManager.samplerHealthManager()).thenReturn(samplerHealthManager);
        when(profileManager.autoAnalysisManager()).thenReturn(autoAnalysisManager);
        when(featuresManager.getDisabledFeatures()).thenReturn(List.of());
        when(flamegraphManager.allEventSummaries()).thenReturn(List.of());
        when(flamegraphManager.eventSummaries()).thenReturn(List.of());
        when(samplerHealthManager.cpuTimeSampleLoss()).thenReturn(CpuTimeSampleLoss.EMPTY);
        when(autoAnalysisManager.analysisResults()).thenReturn(List.of());
        when(autoAnalysisManager.isComputed()).thenReturn(true);
        when(recordingCommitResolver.resolve("rec-1")).thenReturn(Optional.empty());
    }

    private static EventSummaryResult recorded(String eventType, long samples) {
        return new EventSummaryResult(new EventSummary(
                eventType, eventType, null, null, samples, 0, true, false, List.of(), null, null));
    }

    private static AutoAnalysisResult rule(String rule, AnalysisResult.Severity severity, String topic) {
        return new AutoAnalysisResult(rule, severity, "explanation", rule + " summary", "solution", "50", topic);
    }

    @Nested
    class Get {

        @Test
        void returnsTheProfilesIdentityAndSize() {
            stubProfile(RecordingEventSource.JDK);
            when(profileManager.sizeInBytes()).thenReturn(4096L);

            String result = tools().get();

            assertTrue(result.contains("\"profileId\":\"p-1\""));
            assertTrue(result.contains("\"sizeInBytes\":4096"));
        }

        /**
         * The commit is the one fact that lets a client in a checkout know whether it reads the code
         * that ran; it comes from the recording's tags, not from the profile itself.
         */
        @Test
        void reportsTheRecordingsCommitWhenItWasTagged() {
            stubProfile(RecordingEventSource.JDK);
            when(recordingCommitResolver.resolve("rec-1")).thenReturn(Optional.of("abc123"));

            assertTrue(tools().get().contains("\"recordingCommit\":\"abc123\""));
        }

        @Test
        void reportsAnUnknownCommitAsNullRatherThanOmittingIt() {
            stubProfile(RecordingEventSource.JDK);

            assertTrue(tools().get().contains("\"recordingCommit\":null"));
        }
    }

    @Nested
    class Features {

        @Test
        void reportsTheEventTypesTheProfileRecorded() {
            stubProfile(RecordingEventSource.JDK);
            when(flamegraphManager.allEventSummaries()).thenReturn(List.of(
                    new EventSummaryResult(new EventSummary(
                            "jdk.ExecutionSample", "Execution Sample", null, null,
                            1200, 0, true, false, List.of(), null, null))));

            String result = tools().features();

            assertTrue(result.contains("jdk.ExecutionSample"));
            assertTrue(result.contains("1200"));
        }

        /**
         * A profile whose heap-dump index has not been built cannot answer the heap tools, so the
         * client must be told before it tries rather than after it fails.
         */
        @Test
        void marksHeapDumpUnavailableWhenThereIsNoIndex() {
            stubProfile(RecordingEventSource.JDK);
            when(heapDumpManager.heapDumpExists()).thenReturn(false);

            assertTrue(tools().features().contains(FeatureType.HEAP_DUMP.name()));
        }

        /**
         * pprof profiles are aggregated and carry no per-sample timestamps, so anything time-resolved
         * would draw a single spike.
         */
        @Test
        void marksTimeResolvedViewsUnavailableForPprof() {
            stubProfile(RecordingEventSource.PPROF);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);

            String result = tools().features();

            assertTrue(result.contains(FeatureType.SUBSECOND.name()));
            assertTrue(result.contains(FeatureType.TIMESERIES.name()));
        }

        @Test
        void carriesTheCapabilityGapsBesideTheDisabledFeatures() {
            stubProfile(RecordingEventSource.JDK);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);
            when(featuresManager.getDisabledFeatures()).thenReturn(List.of(FeatureType.TRACES));

            String result = tools().features();

            assertTrue(result.contains("\"capabilityGaps\":["), result);
            assertTrue(result.contains("\"subject\":\"TRACES\""), result);
            assertTrue(result.contains("This profile holds no traces"), result);
        }
    }

    /**
     * What the summary says a profile cannot answer. Every line is a fact about the recording — an
     * event type the profiler never captured, a report never built — and never a verdict about the
     * application.
     */
    @Nested
    class CapabilityGaps {

        @Test
        void namesTheFlamegraphGroupsTheProfilerNeverRecorded() {
            stubProfile(RecordingEventSource.JDK);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);
            when(flamegraphManager.eventSummaries()).thenReturn(List.of(
                    recorded("jdk.ExecutionSample", 4200)));

            String result = tools().summary();

            assertTrue(result.contains("\"subject\":\"allocation\""), result);
            assertTrue(result.contains("The recording holds no Allocation Samples (jdk.ObjectAllocationInNewTLAB)"), result);
            assertTrue(result.contains("cannot be assessed from this profile"), result);
            assertFalse(result.contains("\"subject\":\"execution\""), result);
        }

        @Test
        void namesTheJvmSectionsWithNoEventsBehindThem() {
            stubProfile(RecordingEventSource.JDK);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);
            when(flamegraphManager.allEventSummaries()).thenReturn(List.of(
                    recorded("jdk.GarbageCollection", 12)));

            String result = tools().summary();

            assertFalse(result.contains("\"subject\":\"jvm_gc\""), result);
            assertTrue(result.contains("\"subject\":\"jvm_safepoints\""), result);
            assertTrue(result.contains("so jvm_safepoints has nothing to render"), result);
        }

        /**
         * The GC overview and its detail pages are gated on the same events; one sentence about those
         * events is enough.
         */
        @Test
        void saysOnceWhenTwoSectionsAreGatedOnTheSameEvents() {
            stubProfile(RecordingEventSource.JDK);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);

            String result = tools().summary();

            assertTrue(result.contains("\"subject\":\"jvm_gc\""), result);
            assertFalse(result.contains("\"subject\":\"jvm_gcDetail\""), result);
        }

        @Test
        void tellsAMissingHeapDumpFromAnUnindexedOne() {
            stubProfile(RecordingEventSource.JDK);
            when(heapDumpManager.heapDumpExists()).thenReturn(false);

            assertTrue(tools().summary().contains("This profile has no heap dump"));

            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(false);

            String result = tools().summary();
            assertTrue(result.contains("index has not been built"), result);
            assertTrue(result.contains("heap_prepare builds it"), result);
        }

        @Test
        void reportsTheSamplesTheKernelDroppedAsAFactAboutEveryCpuShare() {
            stubProfile(RecordingEventSource.JDK);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);
            when(samplerHealthManager.cpuTimeSampleLoss()).thenReturn(new CpuTimeSampleLoss(900, 100, 3));

            String result = tools().summary();

            assertTrue(result.contains("\"subject\":\"sampler\""), result);
            assertTrue(result.contains("dropped 100 of 1,000 samples (10.0%) in 3 loss events"), result);
        }

        @Test
        void saysWhenTheRulesHaveNotRunRatherThanLettingAnEmptyListPassAsClean() {
            stubProfile(RecordingEventSource.JDK);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);
            when(autoAnalysisManager.isComputed()).thenReturn(false);
            when(autoAnalysisManager.canGenerate()).thenReturn(true);

            String result = tools().summary();

            assertTrue(result.contains("\"autoAnalysisMissing\":true"), result);
            assertTrue(result.contains("\"subject\":\"autoAnalysis\""), result);
            assertTrue(result.contains("jvm_autoAnalysis with compute true"), result);
        }

        /**
         * A heap dump is not a recording. One sentence explains every JFR family away; listing each of
         * them as missing would bury it.
         */
        @Test
        void describesAHeapDumpProfileInOneLineRatherThanAsEveryJfrFamilyMissing() {
            stubProfile(RecordingEventSource.HEAP_DUMP);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);

            String result = tools().summary();

            assertTrue(result.contains("This profile is a heap dump"), result);
            assertFalse(result.contains("\"subject\":\"jvm_gc\""), result);
            assertFalse(result.contains("\"subject\":\"allocation\""), result);
        }

        @Test
        void describesAnImportedSampleSetInOneLine() {
            stubProfile(RecordingEventSource.PPROF);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);

            String result = tools().summary();

            assertTrue(result.contains("imported pprof sample set"), result);
            assertFalse(result.contains("\"subject\":\"jvm_gc\""), result);
        }
    }

    /**
     * The summary leads with the rules that flagged something, in the shared finding shape, so the
     * first thing a reader sees names a category and the tool with the figures behind it.
     */
    @Nested
    class TopFindings {

        @Test
        void leadsWithTheRulesThatFlaggedSomethingAndLeavesThePassesToTheFullTool() {
            stubProfile(RecordingEventSource.JDK);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);
            when(autoAnalysisManager.analysisResults()).thenReturn(List.of(
                    rule("Long GC Pauses", AnalysisResult.Severity.WARNING, "garbage_collection"),
                    rule("Thrown Errors", AnalysisResult.Severity.OK, "exceptions"),
                    rule("Allocated Classes", AnalysisResult.Severity.NA, "tlab")));

            String result = tools().summary();

            assertTrue(result.contains("\"id\":\"garbage_collection:long-gc-pauses\""), result);
            assertTrue(result.contains("\"nextTool\":\"jvm_gc\""), result);
            assertFalse(result.contains("exceptions:thrown-errors"), result);
            assertFalse(result.contains("tlab:allocated-classes"), result);
            assertTrue(result.contains("\"autoAnalysisMissing\":false"), result);
        }
    }

    @Nested
    class ViewLink {

        @Test
        void buildsALinkToTheNamedView() {
            stubProfile(RecordingEventSource.JDK);

            assertTrue(tools().viewLink("garbage-collection", null)
                    .endsWith("/profiles/p-1/garbage-collection"));
        }

        @Test
        void keepsAMultiSegmentViewIntact() {
            stubProfile(RecordingEventSource.JDK);

            assertTrue(tools().viewLink("heap-dump/leak-suspects", null)
                    .endsWith("/profiles/p-1/heap-dump/leak-suspects"));
        }

        /**
         * The one curated view that takes an argument; every other view ignores it rather than
         * carrying a parameter it does not read.
         */
        @Test
        void passesAnObjectIdOnlyToTheGcRootPathView() {
            stubProfile(RecordingEventSource.JDK);

            assertTrue(tools().viewLink("heap-dump/gc-root-path", "42").endsWith("?objectId=42"));
            assertFalse(tools().viewLink("garbage-collection", "42").contains("objectId"));
        }

        /**
         * A wrong guess has to fail as a wrong guess: a silently accepted name would become a link
         * that 404s into the SPA fallback, which the reader only discovers after clicking it.
         */
        @Test
        void refusesAnUnknownViewAndNamesTheValidOnes() {
            stubProfile(RecordingEventSource.JDK);

            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class, () -> tools().viewLink("gc", null));

            assertTrue(thrown.getMessage().contains("Unknown view 'gc'"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("garbage-collection"), thrown.getMessage());
        }
    }
}
