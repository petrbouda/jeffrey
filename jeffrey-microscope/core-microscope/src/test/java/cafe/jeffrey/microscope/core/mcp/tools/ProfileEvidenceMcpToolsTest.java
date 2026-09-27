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
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.model.EventSummary;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.common.analysis.AnalysisResult;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.profile.mcp.McpTestToolsets;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.provider.profile.api.CpuTimeSampleLoss;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileEvidenceMcpToolsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);

    /** The snapshot links the profile's page, built off the request being served. */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void snapshotCarriesIdentityUnitsDenominatorsAndReplayArguments() {
        ProfileManager manager = manager("p");
        when(manager.flamegraphManager().allEventSummaries()).thenReturn(List.of(event("jdk.ObjectAllocationSample", 7, 4096)));
        RecordingCommitResolver commits = mock(RecordingCommitResolver.class);
        when(commits.resolve("recording-p")).thenReturn(Optional.of("abc123"));
        JsonNode result = tools(manager, commits).evidence(null).structuredContent();
        assertEquals(2, result.get("schemaVersion").asInt());
        assertEquals("recording-p", result.get("profile").get("recordingId").asString());
        assertEquals("abc123", result.get("profile").get("recordingCommit").asString());
        assertEquals("whole-recording", result.get("filters").get("scope").asString());
        assertEquals("bytes", result.get("eventTypes").get(0).get("weightUnit").asString());
        assertEquals(4096, result.get("eventTypes").get(0).get("weight").asLong());
        assertTrue(result.get("semantics").asString().contains("current"));
        assertFalse(result.has("replay"), result.toString());
        JsonNode replay = result.get("followUp").get("nextTools").get(0);
        assertEquals("profiles_evidence", replay.get("tool").asString());
        assertEquals("p", replay.get("arguments").get("profileId").asString());
        assertEquals(100, replay.get("arguments").get("limit").asInt());
        assertFalse(replay.get("why").asString().isBlank());
        assertEquals(1, result.get("followUp").get("nextTools").size());
        assertEquals(0, result.get("followUp").get("guidance").size());
    }

    /** The replay carries the row limit the snapshot was read with, so reading it again matches. */
    @Test
    void replayCarriesTheLimitThisSnapshotWasReadWith() {
        JsonNode result = tools(manager("p"), mock(RecordingCommitResolver.class)).evidence(7).structuredContent();

        JsonNode arguments = result.get("followUp").get("nextTools").get(0).get("arguments");
        assertEquals(7, arguments.get("limit").asInt());
        assertEquals(2, arguments.size(), arguments.toString());
    }

    /** Every instant in the document is UTC epoch milliseconds; no ISO string is left to parse. */
    @Test
    void snapshotCarriesTimeAsEpochMilliseconds() {
        JsonNode result = tools(manager("p"), mock(RecordingCommitResolver.class)).evidence(null).structuredContent();

        assertEquals(CLOCK.millis(), result.get("generatedAtEpochMs").asLong());
        assertFalse(result.has("generatedAt"), result.toString());
        JsonNode profile = result.get("profile");
        assertEquals(0, profile.get("startedAtEpochMs").asLong());
        assertEquals(60_000, profile.get("finishedAtEpochMs").asLong());
        assertEquals(0, profile.get("createdAtEpochMs").asLong());
        assertEquals(60_000, profile.get("durationMs").asLong());
        assertFalse(profile.has("startedAt"), profile.toString());
    }

    /**
     * A full document fits the advertised schema: findings with their open evidence, a row-limited
     * truncation map and the reported sampler variant with every count filled in.
     */
    @Test
    void snapshotConformsToTheGeneratedSchema() {
        ProfileManager manager = manager("p");
        when(manager.flamegraphManager().allEventSummaries()).thenReturn(List.of(
                event("jdk.ExecutionSample", 7, 0), event("jdk.ObjectAllocationSample", 8, 4096)));
        when(manager.autoAnalysisManager().analysisResults()).thenReturn(List.of(
                new AutoAnalysisResult("GC Check", AnalysisResult.Severity.WARNING, "Long pauses", "Pauses",
                        null, "70", "gc")));
        when(manager.samplerHealthManager().cpuTimeSampleLoss()).thenReturn(new CpuTimeSampleLoss(80, 20, 2));

        JsonNode result = tools(manager, mock(RecordingCommitResolver.class)).evidence(1).structuredContent();

        McpSchemaConformance.assertConforms(result, schemaOf("evidence", Integer.class));
        assertEquals(1, McpNextToolConformance.assertFollowable(result, specsOf(ProfileEvidenceMcpTools.class, "profiles")));
        assertEquals("ROW_LIMIT", result.get("truncation").get("eventTypes").get("reason").asString());
        assertEquals("REPORTED", result.get("samplerHealth").get("status").asString());
        assertEquals("COMPUTED", result.get("autoAnalysis").asString());
        assertFalse(result.has("autoAnalysisComputed"), result.toString());
        UiLinkRoutes.assertResolves(result.get("uiLink").asString());
        assertTrue(result.get("uiLink").asString().endsWith("/profiles/p"), result.get("uiLink").asString());
    }

    /**
     * Rules that have not run are said to have not run, never read as a clean recording: the findings
     * stay empty, autoAnalysis says NOT_COMPUTED, and the call that runs them is handed back.
     */
    @Test
    void snapshotOfAnUnanalysedProfileSaysSoAndNamesTheCallThatRunsTheRules() {
        ProfileManager manager = manager("p");
        when(manager.autoAnalysisManager().isComputed()).thenReturn(false);
        when(manager.autoAnalysisManager().canGenerate()).thenReturn(true);

        JsonNode result = tools(manager, mock(RecordingCommitResolver.class)).evidence(null).structuredContent();

        McpSchemaConformance.assertConforms(result, schemaOf("evidence", Integer.class));
        assertEquals("NOT_COMPUTED", result.get("autoAnalysis").asString());
        assertEquals(0, result.get("findings").size());
        JsonNode compute = result.get("followUp").get("nextTools").get(1);
        assertEquals("jvm_autoAnalysis", compute.get("tool").asString());
        assertEquals("p", compute.get("arguments").get("profileId").asString());
        assertTrue(compute.get("arguments").get("compute").asBoolean());
        assertEquals(2, McpNextToolConformance.assertFollowable(result, CatalogueSpecs.of(
                specsOf(ProfileEvidenceMcpTools.class, "profiles"), specsOf(JvmMcpTools.class, "jvm"))));
        verify(manager.autoAnalysisManager(), never()).analysisResults();
    }

    /** A profile the rules cannot run on gets no call that could only fail. */
    @Test
    void snapshotOfAProfileTheRulesCannotRunOnOffersNoCall() {
        ProfileManager manager = manager("p");
        when(manager.autoAnalysisManager().isComputed()).thenReturn(false);
        when(manager.autoAnalysisManager().canGenerate()).thenReturn(false);

        JsonNode result = tools(manager, mock(RecordingCommitResolver.class)).evidence(null).structuredContent();

        McpSchemaConformance.assertConforms(result, schemaOf("evidence", Integer.class));
        assertEquals("CANNOT_COMPUTE", result.get("autoAnalysis").asString());
        assertEquals(1, result.get("followUp").get("nextTools").size());
    }

    /** Where the jvm family is not served, neither the compute call nor a finding's jvm call is handed out. */
    @Test
    void dropsTheCallsToAFamilyThatIsNotServed() {
        ProfileManager manager = manager("p");
        when(manager.autoAnalysisManager().isComputed()).thenReturn(false);
        when(manager.autoAnalysisManager().canGenerate()).thenReturn(true);
        AdvertisedFamilies profilesOnly = new AdvertisedFamilies(Set.of(AdvertisedFamilies.PROFILES));

        JsonNode result = new ProfileEvidenceMcpTools(manager, mock(RecordingCommitResolver.class),
                mock(JfrFlamegraphPanelProvider.class), mock(StackSampleFlamegraphPanelProvider.class), CLOCK,
                profilesOnly).evidence(null).structuredContent();

        assertEquals(1, McpNextToolConformance.assertFollowable(result, specsOf(ProfileEvidenceMcpTools.class, "profiles")));
    }

    /** A finding names the call on this profile that carries its figures, with the arguments to pass. */
    @Test
    void aFindingNamesTheCallThatCarriesItsFigures() {
        ProfileManager manager = manager("p");
        when(manager.autoAnalysisManager().analysisResults()).thenReturn(List.of(
                new AutoAnalysisResult("Long GC Pauses", AnalysisResult.Severity.WARNING, "Long pauses", "Pauses",
                        null, "70", "gc_configuration")));

        JsonNode result = tools(manager, mock(RecordingCommitResolver.class)).evidence(null).structuredContent();

        JsonNode call = result.get("findings").get(0).get("nextTool");
        assertEquals("jvm_gcDetail", call.get("tool").asString());
        assertEquals("p", call.get("arguments").get("profileId").asString());
        assertEquals("CONFIGURATION", call.get("arguments").get("page").asString());
        assertEquals(2, McpNextToolConformance.assertFollowable(result, CatalogueSpecs.of(
                specsOf(ProfileEvidenceMcpTools.class, "profiles"), specsOf(JvmMcpTools.class, "jvm"))));
    }

    /** Without loss evidence the sampler's counts are null, which the nullable schema has to admit. */
    @Test
    void snapshotWithoutLossEvidenceConformsWithNullCounts() {
        JsonNode result = tools(manager("p"), mock(RecordingCommitResolver.class)).evidence(null).structuredContent();

        McpSchemaConformance.assertConforms(result, schemaOf("evidence", Integer.class));
        JsonNode sampler = result.get("samplerHealth");
        assertEquals("UNAVAILABLE", sampler.get("status").asString());
        for (String count : List.of("capturedSamples", "lostSamples", "lossEvents", "denominator",
                "denominatorDefinition", "lostSharePct")) {
            assertTrue(sampler.get(count).isNull(), count + " in " + sampler);
        }
    }

    /** A profile whose span is unknown has no duration, so the pair has no scale factor. */
    @Test
    void qualityOfAProfileWithAnUnknownSpanHasNoScaleFactor() {
        ProfileManager primary = manager("primary");
        ProfileManager baseline = manager("baseline");
        when(baseline.info()).thenReturn(new ProfileInfo("baseline", null, null, null, RecordingEventSource.JDK,
                Instant.EPOCH, null, Instant.EPOCH, true, false, null));

        JsonNode result = new CompareMcpTools(primary, id -> baseline, CLOCK, EVERY_FAMILY)
                .quality("baseline").structuredContent();

        McpSchemaConformance.assertConforms(result, schemaOf(CompareMcpTools.class, "quality", String.class));
        assertFalse(result.get("durationNormalization").get("available").asBoolean());
        assertTrue(result.get("durationNormalization").get("baselineScaleFactor").isNull());
        JsonNode profile = result.get("baseline").get("profile");
        assertTrue(profile.get("durationMs").isNull(), profile.toString());
        assertTrue(profile.get("finishedAtEpochMs").isNull(), profile.toString());
        assertTrue(profile.get("recordingId").isNull(), profile.toString());
    }

    @Test
    void qualityConformsToTheGeneratedSchema() {
        ProfileManager primary = manager("primary");
        ProfileManager baseline = manager("baseline");
        when(primary.flamegraphManager().allEventSummaries()).thenReturn(List.of(configured("10 ms"),
                event("jeffrey.HttpServerExchange", 9, 0)));
        when(baseline.flamegraphManager().allEventSummaries()).thenReturn(List.of(configured("20 ms")));

        JsonNode result = new CompareMcpTools(primary, id -> baseline, CLOCK, EVERY_FAMILY)
                .quality("baseline").structuredContent();

        McpSchemaConformance.assertConforms(result, schemaOf(CompareMcpTools.class, "quality", String.class));
        // The replay, and the finding's own call to compare_list with both profiles.
        assertEquals(2, McpNextToolConformance.assertFollowable(result, specsOf(CompareMcpTools.class, "compare")));
        assertEquals("compare_list", result.get("findings").get(0).get("nextTool").get("tool").asString());
        assertEquals(2, result.get("schemaVersion").asInt());
        assertEquals(CLOCK.millis(), result.get("generatedAtEpochMs").asLong());
        assertFalse(result.has("replay"), result.toString());
        JsonNode replay = result.get("followUp").get("nextTools").get(0);
        assertEquals("compare_quality", replay.get("tool").asString());
        assertEquals("primary", replay.get("arguments").get("profileId").asString());
        assertEquals("baseline", replay.get("arguments").get("baselineProfileId").asString());
        assertTrue(result.get("durationNormalization").get("available").asBoolean());
        assertEquals(1.0, result.get("durationNormalization").get("baselineScaleFactor").asDouble());
        assertEquals("COMPLETE", result.get("truncation").get("primary.eventTypes").get("reason").asString());
        assertEquals(1, result.get("findings").size());
        assertTrue(result.get("findings").get(0).get("evidence").isObject());
        assertEquals("primary", result.get("primary").get("profile").get("profileId").asString());
        assertEquals("baseline", result.get("baseline").get("profile").get("profileId").asString());
        assertFalse(result.get("primary").has("profileId"), result.get("primary").toString());
        // The pair's page is the differential grid, opened on this baseline.
        UiLinkRoutes.assertResolves(result.get("uiLink").asString());
        assertTrue(result.get("uiLink").asString().endsWith("/profiles/primary/flamegraphs/differential?baseline=baseline"));
        assertFalse(result.has("uiLinkNote"), result.toString());
    }

    /** What {@code tools/list} advertises for the family, as the profile-scoped toolset serves it. */
    private static List<McpToolSpec> specsOf(Class<?> tools, String family) {
        return McpTestToolsets.unscoped(tools, family, profileId -> {
            throw new UnsupportedOperationException("specs need no target");
        }).specs();
    }

    private static JsonNode schemaOf(String method, Class<?>... parameters) {
        return schemaOf(ProfileEvidenceMcpTools.class, method, parameters);
    }

    private static JsonNode schemaOf(Class<?> tools, String method, Class<?>... parameters) {
        McpOutputSchema declared = assertDoesNotThrow(() -> tools.getMethod(method, parameters))
                .getAnnotation(McpOutputSchema.class);
        return McpSchemaGenerator.schemaOf(declared.value());
    }

    @Test
    void limitOmitsWholeEventRecordsAndReportsCounts() {
        ProfileManager manager = manager("p");
        when(manager.flamegraphManager().allEventSummaries()).thenReturn(List.of(event("jdk.ExecutionSample", 7, 0), event("jdk.ObjectAllocationSample", 8, 4096)));
        JsonNode result = tools(manager, mock(RecordingCommitResolver.class)).evidence(1).structuredContent();
        assertEquals(1, result.get("eventTypes").size());
        assertEquals(2, result.get("truncation").get("eventTypes").get("total").asInt());
        assertEquals(1, result.get("truncation").get("eventTypes").get("returned").asInt());
    }

    @Test
    void qualityDoesNotInventWorkloadNormalizationOrHealthyMissingTelemetry() {
        ProfileManager primary = manager("primary");
        ProfileManager baseline = manager("baseline");
        when(primary.flamegraphManager().allEventSummaries()).thenReturn(List.of(event("jeffrey.HttpServerExchange", 9, 0)));
        JsonNode result = new CompareMcpTools(primary, id -> baseline, CLOCK, EVERY_FAMILY)
                .quality("baseline").structuredContent();
        assertFalse(result.get("workloadNormalization").get("available").asBoolean());
        assertEquals("UNAVAILABLE", result.get("primary").get("samplerHealth").get("status").asString());
        assertTrue(result.get("primary").get("samplerHealth").get("denominator").isNull());
        assertEquals(9, result.get("primary").get("observedWorkloadEvents").get(0).get("count").asLong());
    }

    @Test
    void evaluatedPassesKeepFindingIdentityAndUnevaluatedRulesRemainGaps() {
        ProfileManager manager = manager("p");
        when(manager.autoAnalysisManager().analysisResults()).thenReturn(List.of(
                new AutoAnalysisResult("GC Check", AnalysisResult.Severity.OK, "No long pauses", "Passed",
                        null, "0", "gc"),
                new AutoAnalysisResult("Missing Events", AnalysisResult.Severity.NA, null, null,
                        null, null, "gc")));
        JsonNode result = tools(manager, mock(RecordingCommitResolver.class)).evidence(null).structuredContent();
        assertEquals(1, result.get("findings").size());
        assertEquals("gc:gc-check", result.get("findings").get(0).get("id").asString());
        assertEquals("OK", result.get("findings").get(0).get("severity").asString());
        assertEquals("Missing Events", result.get("notEvaluatedRules").get(0).asString());
    }

    @Test
    void oversizedEventIsOmittedWholeWithoutLosingIdentity() {
        ProfileManager manager = manager("p");
        String enormousName = "x".repeat(McpToolOutput.MAX_CHARS);
        when(manager.flamegraphManager().allEventSummaries()).thenReturn(List.of(event(enormousName, 7, 0)));
        JsonNode result = tools(manager, mock(RecordingCommitResolver.class)).evidence(null).structuredContent();
        assertEquals("p", result.get("profile").get("profileId").asString());
        assertEquals(0, result.get("eventTypes").size());
        assertEquals(1, result.get("truncation").get("eventTypes").get("omitted").asInt());
    }

    @Test
    void qualityReportsMismatchedSamplingAndExactLossDenominator() {
        ProfileManager primary = manager("primary");
        ProfileManager baseline = manager("baseline");
        when(primary.flamegraphManager().allEventSummaries()).thenReturn(List.of(configured("10 ms")));
        when(baseline.flamegraphManager().allEventSummaries()).thenReturn(List.of(configured("20 ms")));
        when(primary.samplerHealthManager().cpuTimeSampleLoss()).thenReturn(new CpuTimeSampleLoss(80, 20, 2));
        JsonNode result = new CompareMcpTools(primary, id -> baseline, CLOCK, EVERY_FAMILY)
                .quality("baseline").structuredContent();
        assertEquals("MISMATCH", result.get("samplingConfiguration").get(0).get("status").asString());
        assertEquals(100, result.get("primary").get("samplerHealth").get("denominator").asLong());
        assertEquals(20.0, result.get("primary").get("samplerHealth").get("lostSharePct").asDouble());
    }

    private static EventSummaryResult configured(String period) {
        return new EventSummaryResult(new EventSummary("jdk.ExecutionSample", "CPU", RecordingEventSource.JDK,
                null, 1000, 0, true, false, List.of(), Map.of(), Map.of("period", period)));
    }

    static ProfileManager manager(String id) {
        ProfileManager manager = mock(ProfileManager.class, RETURNS_DEEP_STUBS);
        when(manager.info()).thenReturn(new ProfileInfo(id, "project", "workspace", id,
                RecordingEventSource.JDK, Instant.EPOCH, Instant.EPOCH.plusSeconds(60),
                Instant.EPOCH, true, false, "recording-" + id));
        when(manager.samplerHealthManager().cpuTimeSampleLoss()).thenReturn(CpuTimeSampleLoss.EMPTY);
        when(manager.featuresManager().getDisabledFeatures()).thenReturn(List.of());
        when(manager.autoAnalysisManager().analysisResults()).thenReturn(List.of());
        when(manager.autoAnalysisManager().isComputed()).thenReturn(true);
        when(manager.flamegraphManager().eventSummaries()).thenReturn(List.of());
        when(manager.flamegraphManager().allEventSummaries()).thenReturn(List.of());
        return manager;
    }

    static EventSummaryResult event(String code, long samples, long weight) {
        return new EventSummaryResult(code, code,
                new EventSummaryResult.SingleResult(code, code, "JDK", null, samples, weight, false, Map.of()), null);
    }

    private static ProfileEvidenceMcpTools tools(ProfileManager manager, RecordingCommitResolver commits) {
        return new ProfileEvidenceMcpTools(manager, commits, mock(JfrFlamegraphPanelProvider.class),
                mock(StackSampleFlamegraphPanelProvider.class), CLOCK, EVERY_FAMILY);
    }
}
