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
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.common.analysis.AnalysisResult;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.provider.profile.api.CpuTimeSampleLoss;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileEvidenceMcpToolsTest {

    @Test
    void snapshotCarriesIdentityUnitsDenominatorsAndReplayArguments() {
        ProfileManager manager = manager("p");
        when(manager.flamegraphManager().allEventSummaries()).thenReturn(List.of(event("jdk.ObjectAllocationSample", 7, 4096)));
        RecordingCommitResolver commits = mock(RecordingCommitResolver.class);
        when(commits.resolve("recording-p")).thenReturn(Optional.of("abc123"));
        JsonNode result = tools(manager, commits).evidence(null).structuredContent();
        assertEquals(1, result.get("schemaVersion").asInt());
        assertEquals("recording-p", result.get("profile").get("recordingId").asString());
        assertEquals("abc123", result.get("profile").get("recordingCommit").asString());
        assertEquals("whole-recording", result.get("filters").get("scope").asString());
        assertEquals("bytes", result.get("eventTypes").get(0).get("weightUnit").asString());
        assertEquals(4096, result.get("eventTypes").get(0).get("weight").asLong());
        assertEquals("profiles_evidence", result.get("replay").get("tool").asString());
        assertTrue(result.get("semantics").asString().contains("current"));
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
        JsonNode result = new CompareMcpTools(primary, id -> baseline).quality("baseline").structuredContent();
        assertFalse(result.get("workloadNormalization").get("available").asBoolean());
        assertEquals("unavailable", result.get("primary").get("samplerHealth").get("status").asString());
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
        JsonNode result = new CompareMcpTools(primary, id -> baseline).quality("baseline").structuredContent();
        assertEquals("mismatch", result.get("samplingConfiguration").get(0).get("status").asString());
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
                mock(StackSampleFlamegraphPanelProvider.class));
    }
}
