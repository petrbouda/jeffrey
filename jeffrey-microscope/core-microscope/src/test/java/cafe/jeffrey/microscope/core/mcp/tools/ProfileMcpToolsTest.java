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

import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.model.EventSummary;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.common.analysis.AnalysisResult;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.AutoAnalysisManager;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.SamplerHealthManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.profile.mcp.McpTestToolsets;
import cafe.jeffrey.profile.mcp.ProfileScopedToolset;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.provider.profile.api.CpuTimeSampleLoss;
import tools.jackson.databind.JsonNode;
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

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
                new StackSampleFlamegraphPanelProvider(),
                EVERY_FAMILY);
    }

    /** The link a viewLink answer carries, which is all it carries for the reader. */
    private String viewLink(String view, String objectId) {
        return tools().viewLink(view, objectId).structuredContent().get("uiLink").asString();
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

            String result = tools().get().text();

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

            assertTrue(tools().get().text().contains("\"recordingCommit\":\"abc123\""));
        }

        /**
         * A profile whose recording carried no start or end instant used to fail the whole call with a
         * NullPointerException instead of answering with the identity it does have.
         */
        @Test
        void answersForAProfileWithoutTimestamps() {
            stubProfile(RecordingEventSource.JDK);
            when(profileManager.info()).thenReturn(new ProfileInfo(
                    "p-1", "proj-1", "ws-1", "Checkout run", RecordingEventSource.JDK,
                    null, null, null, true, false, "rec-1"));

            String result = tools().get().text();

            assertTrue(result.contains("\"profileId\":\"p-1\""), result);
            assertTrue(result.contains("\"recordingStartedAtEpochMs\":null"), result);
            assertTrue(result.contains("\"recordingFinishedAtEpochMs\":null"), result);
            assertTrue(result.contains("\"durationMs\":null"), result);
            assertTrue(result.contains("\"createdAtEpochMs\":null"), result);
        }

        @Test
        void reportsAnUnknownCommitAsNullRatherThanOmittingIt() {
            stubProfile(RecordingEventSource.JDK);

            assertTrue(tools().get().text().contains("\"recordingCommit\":null"));
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

            String result = tools().features().text();

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

            assertTrue(tools().features().text().contains(FeatureType.HEAP_DUMP.name()));
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

            String result = tools().features().text();

            assertTrue(result.contains(FeatureType.SUBSECOND.name()));
            assertTrue(result.contains(FeatureType.TIMESERIES.name()));
        }

        @Test
        void carriesTheCapabilityGapsBesideTheDisabledFeatures() {
            stubProfile(RecordingEventSource.JDK);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);
            when(featuresManager.getDisabledFeatures()).thenReturn(List.of(FeatureType.TRACES));

            String result = tools().features().text();

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

            String result = tools().summary().text();

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

            String result = tools().summary().text();

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

            String result = tools().summary().text();

            assertTrue(result.contains("\"subject\":\"jvm_gc\""), result);
            assertFalse(result.contains("\"subject\":\"jvm_gcDetail\""), result);
        }

        @Test
        void tellsAMissingHeapDumpFromAnUnindexedOne() {
            stubProfile(RecordingEventSource.JDK);
            when(heapDumpManager.heapDumpExists()).thenReturn(false);

            assertTrue(tools().summary().text().contains("This profile has no heap dump"));

            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(false);

            String result = tools().summary().text();
            assertTrue(result.contains("index has not been built"), result);
            assertTrue(result.contains("heap_prepare builds it"), result);
        }

        @Test
        void reportsTheSamplesTheKernelDroppedAsAFactAboutEveryCpuShare() {
            stubProfile(RecordingEventSource.JDK);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);
            when(samplerHealthManager.cpuTimeSampleLoss()).thenReturn(new CpuTimeSampleLoss(900, 100, 3));

            String result = tools().summary().text();

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

            String result = tools().summary().text();

            assertTrue(result.contains("\"subject\":\"autoAnalysis\""), result);
            assertTrue(result.contains("jvm_autoAnalysis with compute true"), result);
        }

        /**
         * The gap is the only thing that separates "the rules ran and cleared the recording" from
         * "the rules did not run", since both leave topFindings empty. A profile whose rules ran must
         * therefore not carry it.
         */
        @Test
        void doesNotClaimTheRulesAreMissingWhenTheyRan() {
            stubProfile(RecordingEventSource.JDK);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);

            String result = tools().summary().text();

            assertTrue(result.contains("\"topFindings\":[]"), result);
            assertFalse(result.contains("\"subject\":\"autoAnalysis\""), result);
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

            String result = tools().summary().text();

            assertTrue(result.contains("This profile is a heap dump"), result);
            assertFalse(result.contains("\"subject\":\"jvm_gc\""), result);
            assertFalse(result.contains("\"subject\":\"allocation\""), result);
        }

        @Test
        void describesAnImportedSampleSetInOneLine() {
            stubProfile(RecordingEventSource.PPROF);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);

            String result = tools().summary().text();

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

            String result = tools().summary().text();

            assertTrue(result.contains("\"id\":\"garbage_collection:long-gc-pauses\""), result);
            assertTrue(result.contains("\"nextTool\":{\"tool\":\"jvm_gc\",\"arguments\":{\"profileId\":\"p-1\"}"),
                    result);
            assertFalse(result.contains("exceptions:thrown-errors"), result);
            assertFalse(result.contains("tlab:allocated-classes"), result);
        }
    }

    /**
     * Every answer is a typed record that fits the schema the tool advertises, carries the page it is
     * about for the user, and hands back its next calls ready to pass on.
     */
    @Nested
    class StructuredAnswers {

        private JsonNode conforming(String method, McpToolResult result) {
            JsonNode structured = result.structuredContent();
            McpSchemaConformance.assertConforms(structured, schemaOf(method));
            UiLinkRoutes.assertResolves(structured.get("uiLink").asString());
            return structured;
        }

        @Test
        void getCarriesTimeAsEpochMillisecondsAndNamesTheSummary() {
            stubProfile(RecordingEventSource.JDK);

            JsonNode detail = conforming("get", tools().get());

            assertEquals(START.toEpochMilli(), detail.get("recordingStartedAtEpochMs").asLong());
            assertEquals(START.plusSeconds(120).toEpochMilli(), detail.get("recordingFinishedAtEpochMs").asLong());
            assertEquals(120_000, detail.get("durationMs").asLong());
            assertEquals(START.toEpochMilli(), detail.get("createdAtEpochMs").asLong());
            assertFalse(detail.has("duration"), detail.toString());
            assertTrue(detail.get("uiLink").asString().endsWith("/profiles/p-1"));
            JsonNode next = detail.get("followUp").get("nextTools").get(0);
            assertEquals("profiles_summary", next.get("tool").asString());
            assertEquals(1, McpNextToolConformance.assertFollowable(detail, profilesSpecs()));
        }

        @Test
        void getOfAProfileWithoutTimestampsStillConforms() {
            stubProfile(RecordingEventSource.JDK);
            when(profileManager.info()).thenReturn(new ProfileInfo(
                    "p-1", null, null, null, RecordingEventSource.JDK, null, null, null, true, false, "rec-1"));

            JsonNode detail = conforming("get", tools().get());

            assertTrue(detail.get("durationMs").isNull());
        }

        @Test
        void featuresLinkTheEventTypesPage() {
            stubProfile(RecordingEventSource.JDK);

            JsonNode features = conforming("features", tools().features());

            assertTrue(features.get("uiLink").asString().endsWith("/profiles/p-1/event-types"));
            assertEquals("p-1", features.get("profileId").asString());
        }

        @Test
        void samplerHealthReportsTheCountsWithStatusReported() {
            stubProfile(RecordingEventSource.JDK);
            when(samplerHealthManager.cpuTimeSampleLoss()).thenReturn(new CpuTimeSampleLoss(900, 100, 3));

            JsonNode health = conforming("samplerHealth", tools().samplerHealth());

            assertEquals("REPORTED", health.get("status").asString());
            assertTrue(health.get("reason").isNull());
            assertEquals(100, health.get("lostSamples").asLong());
            assertFalse(health.has("nextSteps"), health.toString());
            assertEquals("profiles_features", health.get("followUp").get("nextTools").get(0).get("tool").asString());
            assertEquals(1, health.get("followUp").get("guidance").size());
            assertEquals(1, McpNextToolConformance.assertFollowable(health, profilesSpecs()));
        }

        /** No loss evidence is a status with a reason, not a sentence in place of the record. */
        @Test
        void samplerHealthWithoutLossEvidenceIsAStatusNotText() {
            stubProfile(RecordingEventSource.JDK);

            JsonNode health = conforming("samplerHealth", tools().samplerHealth());

            assertEquals("UNAVAILABLE", health.get("status").asString());
            assertTrue(health.get("reason").asString().contains("jdk.CPUTimeSampleLoss"));
            assertTrue(health.get("capturedSamples").isNull());
            assertTrue(health.get("lostSamples").isNull());
            assertTrue(health.get("lossEvents").isNull());
            // Uneven loss is advice about loss that happened; with no loss evidence it has nothing to qualify.
            assertEquals(0, health.get("followUp").get("guidance").size(), health.toString());
        }

        @Test
        void summaryOfAnAnalysedProfileSaysTheRulesRan() {
            stubProfile(RecordingEventSource.JDK);

            JsonNode summary = conforming("summary", tools().summary());

            assertEquals("COMPUTED", summary.get("autoAnalysis").asString());
            assertEquals(START.toEpochMilli(), summary.get("startedAtEpochMs").asLong());
            assertFalse(summary.has("startedAtMillis"), summary.toString());
            assertEquals(0, summary.get("followUp").get("nextTools").size());
        }

        /**
         * The rules have not run: the summary says so in a field of its own and names the call that
         * runs them, and never reads the empty cache as a clean recording.
         */
        @Test
        void summaryOfAnUnanalysedProfileNamesTheCallThatRunsTheRules() {
            stubProfile(RecordingEventSource.JDK);
            when(autoAnalysisManager.isComputed()).thenReturn(false);
            when(autoAnalysisManager.canGenerate()).thenReturn(true);

            JsonNode summary = conforming("summary", tools().summary());

            assertEquals("NOT_COMPUTED", summary.get("autoAnalysis").asString());
            JsonNode compute = summary.get("followUp").get("nextTools").get(0);
            assertEquals("jvm_autoAnalysis", compute.get("tool").asString());
            assertTrue(compute.get("arguments").get("compute").asBoolean());
            assertEquals(1, McpNextToolConformance.assertFollowable(summary,
                    CatalogueSpecs.of(profilesSpecs(), CatalogueSpecs.profileScoped(JvmMcpTools.class, "jvm"))));
            verify(autoAnalysisManager, never()).analysisResults();
        }

        @Test
        void summaryOfAProfileTheRulesCannotRunOnOffersNoCall() {
            stubProfile(RecordingEventSource.PPROF);
            when(autoAnalysisManager.isComputed()).thenReturn(false);
            when(autoAnalysisManager.canGenerate()).thenReturn(false);

            JsonNode summary = conforming("summary", tools().summary());

            assertEquals("CANNOT_COMPUTE", summary.get("autoAnalysis").asString());
            assertEquals(0, summary.get("followUp").get("nextTools").size());
        }

        /** A finding's call to a family this installation withholds is dropped; the finding stays. */
        @Test
        void summaryDropsAFindingsCallToAFamilyThatIsNotServed() {
            stubProfile(RecordingEventSource.JDK);
            when(autoAnalysisManager.analysisResults()).thenReturn(List.of(
                    rule("Long GC Pauses", AnalysisResult.Severity.WARNING, "garbage_collection")));
            ProfileMcpTools withoutJvm = new ProfileMcpTools(profileManager, recordingCommitResolver,
                    new JfrFlamegraphPanelProvider(), new StackSampleFlamegraphPanelProvider(),
                    new AdvertisedFamilies(Set.of(AdvertisedFamilies.PROFILES)));

            JsonNode summary = conforming("summary", withoutJvm.summary());

            assertEquals(1, summary.get("topFindings").size());
            assertTrue(summary.get("topFindings").get(0).get("nextTool").isNull());
        }

        @Test
        void linkAnswersWithTheProfilesPage() {
            stubProfile(RecordingEventSource.JDK);

            JsonNode link = conforming("link", tools().link());

            assertTrue(link.get("uiLink").asString().endsWith("/profiles/p-1"));
        }

        @Test
        void viewLinkEchoesTheViewAndTheObjectItPreselects() {
            stubProfile(RecordingEventSource.JDK);

            JsonNode link = conforming("viewLink", tools().viewLink("heap-dump/gc-root-path", " 18446744073709551615 "));

            assertEquals("heap-dump/gc-root-path", link.get("view").asString());
            assertEquals("18446744073709551615", link.get("objectId").asString());
            assertTrue(link.get("uiLink").asString().endsWith("?objectId=18446744073709551615"));
            assertTrue(conforming("viewLink", tools().viewLink("events", "42")).get("objectId").isNull());
        }

        @Test
        void viewLinkRefusesAnObjectIdThatIsNotADecimalHeapId() {
            stubProfile(RecordingEventSource.JDK);

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().viewLink("heap-dump/gc-root-path", "0x2a"));

            assertTrue(thrown.getMessage().contains("decimal"), thrown.getMessage());
        }

        private JsonNode schemaOf(String method) {
            Method tool = Arrays.stream(ProfileMcpTools.class.getMethods())
                    .filter(candidate -> candidate.getName().equals(method))
                    .findFirst()
                    .orElseThrow();
            return McpSchemaGenerator.schemaOf(tool.getAnnotation(McpOutputSchema.class).value());
        }

        private List<McpToolSpec> profilesSpecs() {
            return CatalogueSpecs.profileScoped(ProfileMcpTools.class, "profiles");
        }
    }

    @Nested
    class ViewLink {

        @Test
        void buildsALinkToTheNamedView() {
            stubProfile(RecordingEventSource.JDK);

            assertTrue(viewLink("garbage-collection", null)
                    .endsWith("/profiles/p-1/garbage-collection"));
        }

        @Test
        void keepsAMultiSegmentViewIntact() {
            stubProfile(RecordingEventSource.JDK);

            assertTrue(viewLink("heap-dump/leak-suspects", null)
                    .endsWith("/profiles/p-1/heap-dump/leak-suspects"));
        }

        /**
         * The one curated view that takes an argument; every other view ignores it rather than
         * carrying a parameter it does not read.
         */
        @Test
        void passesAnObjectIdOnlyToTheGcRootPathView() {
            stubProfile(RecordingEventSource.JDK);

            assertTrue(viewLink("heap-dump/gc-root-path", "42").endsWith("?objectId=42"));
            assertFalse(viewLink("garbage-collection", "42").contains("objectId"));
        }

        /**
         * The views are an enumeration in the schema now, not a list in the description, and every one
         * of them is a route the frontend serves. The router snapshot is the same manifest the IntelliJ
         * plugin's ProfileRouteManifestTest pins its tiles to; a view that is not in it would be a link
         * the router's catch-all turns into the recordings list.
         */
        @Test
        void advertisesEveryViewAsAnEnumOfRoutesTheFrontendServes() {
            Set<String> routes = ProfileRouteManifest.routes();

            List<String> views = viewEnum();

            assertFalse(views.isEmpty());
            List<String> missing = views.stream().filter(view -> !routes.contains(view)).toList();
            assertTrue(missing.isEmpty(), "views the frontend does not serve: " + missing);
        }

        /**
         * The schema's enum is the literal copy of the views MicroscopeView offers to viewLink, since an
         * annotation cannot read the enum; the two are held to each other here.
         */
        @Test
        void advertisesExactlyTheViewsTheEnumOffers() {
            Set<String> offered = Arrays.stream(MicroscopeView.values())
                    .filter(MicroscopeView::offeredByViewLink)
                    .map(MicroscopeView::path)
                    .collect(Collectors.toSet());

            assertEquals(offered, Set.copyOf(viewEnum()));
        }

        /** The schema enum and the check inside the tool are one list, so neither accepts what the other refuses. */
        @Test
        void acceptsEveryViewTheSchemaAdvertises() {
            stubProfile(RecordingEventSource.JDK);

            for (String view : viewEnum()) {
                assertTrue(viewLink(view, null).endsWith("/profiles/p-1/" + view), view);
            }
        }

        private static List<String> viewEnum() {
            ProfileScopedToolset<ProfileMcpTools> toolset = McpTestToolsets.unscoped(
                    ProfileMcpTools.class, "profiles", profileId -> {
                        throw new UnsupportedOperationException("specs need no target");
                    });
            JsonNode values = toolset.specs().stream()
                    .filter(spec -> spec.name().equals("profiles_viewLink"))
                    .findFirst()
                    .orElseThrow()
                    .inputSchema().path("properties").path("view").path("enum");
            List<String> views = new ArrayList<>();
            for (JsonNode value : values) {
                views.add(value.asString());
            }
            return views;
        }

        /**
         * A wrong guess has to fail as a wrong guess: a silently accepted name would become a link
         * that 404s into the SPA fallback, which the reader only discovers after clicking it.
         */
        @Test
        void refusesAnUnknownViewAndNamesTheValidOnes() {
            stubProfile(RecordingEventSource.JDK);

            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class, () -> viewLink("gc", null));

            assertTrue(thrown.getMessage().contains("Unknown view 'gc'"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("garbage-collection"), thrown.getMessage());
        }
    }
}
