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

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.common.analysis.AnalysisResult;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.manager.AutoAnalysisManager;
import cafe.jeffrey.profile.manager.ContainerManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.container.ContainerCpuThrottlingData;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.profile.mcp.McpTestToolsets;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.EventTypeName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The findings resource reads what is already there and starts nothing: the cached auto-analysis,
 * the container verdict drawn from stored events, and the capability gaps. Whether the analysis has
 * run decides the status, and a missing one is answered with the call that would run it — never by
 * running it.
 */
class ProfileFindingsReaderTest {

    private static final String PROFILE_ID = "p-1";
    private static final String AUTO_ANALYSIS_TOOL = "jvm_autoAnalysis";
    private static final String AUTO_ANALYSIS_LINK = "http://localhost/profiles/p-1/auto-analysis";
    private static final String NO_JFR_FILE = "no JFR recording file is available for this profile: it was "
            + "removed, or the profile was not imported from a JFR recording";

    private final ProfileManager manager = ProfileManagerFixture.jfrProfile(PROFILE_ID);
    private final AutoAnalysisManager autoAnalysis = mock(AutoAnalysisManager.class);

    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        when(manager.autoAnalysisManager()).thenReturn(autoAnalysis);
    }

    @AfterEach
    void resetRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    class Computed {

        @BeforeEach
        void computed() {
            when(autoAnalysis.isComputed()).thenReturn(true);
            when(autoAnalysis.analysisResults()).thenReturn(List.of(
                    new AutoAnalysisResult("GC Pauses", AnalysisResult.Severity.WARNING, "Long pauses",
                            "Pauses are long", "Tune the heap", "70", "garbage_collection"),
                    new AutoAnalysisResult("Exceptions", AnalysisResult.Severity.OK, "Few exceptions",
                            "Exceptions are rare", null, "0", "exceptions"),
                    new AutoAnalysisResult("Code Cache", AnalysisResult.Severity.NA, null, null,
                            null, null, "code_cache")));
        }

        @Test
        void answersOkWithTheCachedFindingsAndTheirCounts() {
            JsonNode findings = read(EVERY_FAMILY);

            assertEquals(AutoAnalysisStatus.COMPUTED.name(), findings.get("status").asString());
            assertTrue(findings.get("reason").isNull(), findings.toString());
            assertEquals(PROFILE_ID, findings.get("profileId").asString());
            assertEquals(2, findings.get("findings").size());
            assertEquals("garbage_collection:gc-pauses", findings.get("findings").get(0).get("id").asString());
            assertEquals(1, findings.get("severityCounts").get("WARNING").asInt());
            assertEquals(1, findings.get("severityCounts").get("OK").asInt());
            assertEquals(0, findings.get("severityCounts").get("CRITICAL").asInt());
            assertEquals("Code Cache", findings.get("notEvaluatedRules").get(0).asString());
            assertEquals(0, findings.get("followUp").get("nextTools").size());
            verify(autoAnalysis, never()).generate();
        }

        /**
         * The container verdict is the other judgement the surface makes; merged with the rules it is
         * ordered by severity with them, so a throttled container leads.
         */
        @Test
        void mergesTheContainerFindingWithTheRules() {
            recordedContainerThrottling(ContainerCpuThrottlingData.Severity.HIGH);

            JsonNode findings = read(EVERY_FAMILY);

            assertEquals(3, findings.get("findings").size());
            assertEquals("container:cpu-throttling", findings.get("findings").get(0).get("id").asString());
            assertEquals("CRITICAL", findings.get("findings").get(0).get("severity").asString());
            assertEquals(1, findings.get("severityCounts").get("CRITICAL").asInt());
        }

        @Test
        void linksTheAutoAnalysisPage() {
            assertEquals(AUTO_ANALYSIS_LINK, read(EVERY_FAMILY).get("uiLink").asString());
        }

        @Test
        void linksAPageTheFrontendServes() {
            assertTrue(ProfileRouteManifest.routes().contains(ProfileFindingsReader.AUTO_ANALYSIS_VIEW.path()));
        }

        @Test
        void conformsToTheGeneratedSchema() {
            recordedContainerThrottling(ContainerCpuThrottlingData.Severity.MEDIUM);

            McpSchemaConformance.assertConforms(read(EVERY_FAMILY), McpSchemaGenerator.schemaOf(ProfileFindings.class));
        }
    }

    @Nested
    class NotComputed {

        @BeforeEach
        void notComputed() {
            when(autoAnalysis.isComputed()).thenReturn(false);
        }

        /**
         * A resource read has no task to hand back and no operation to follow, so it must never be
         * the thing that starts a slow, whole-recording run: it says so and names the call instead.
         */
        @Test
        void neverRunsTheAnalysisAndNamesTheCallThatWould() {
            when(autoAnalysis.canGenerate()).thenReturn(true);

            JsonNode findings = read(EVERY_FAMILY);

            verify(autoAnalysis, never()).generate();
            verify(autoAnalysis, never()).analysisResults();
            assertEquals(AutoAnalysisStatus.NOT_COMPUTED.name(), findings.get("status").asString());
            assertTrue(findings.get("reason").asString().contains("never runs"), findings.toString());
            JsonNode next = findings.get("followUp").get("nextTools");
            assertEquals(1, next.size());
            assertEquals(AUTO_ANALYSIS_TOOL, next.get(0).get("tool").asString());
            assertEquals(PROFILE_ID, next.get(0).get("arguments").get("profileId").asString());
            assertTrue(next.get(0).get("arguments").get("compute").asBoolean());
            assertEquals(1, McpNextToolConformance.assertFollowable(findings, jvmSpecs()));
        }

        @Test
        void stillCarriesTheContainerFindingAndTheCapabilityGaps() {
            when(autoAnalysis.canGenerate()).thenReturn(true);
            recordedContainerThrottling(ContainerCpuThrottlingData.Severity.LOW);

            JsonNode findings = read(EVERY_FAMILY);

            assertEquals(1, findings.get("findings").size());
            assertEquals("container:cpu-throttling", findings.get("findings").get(0).get("id").asString());
            assertTrue(subjects(findings).contains("autoAnalysis"), findings.toString());
        }

        /**
         * A profile with no JFR file to read cannot be analysed at all, so offering the call would only
         * send the reader after a refusal.
         */
        @Test
        void offersNoCallWhenTheAnalysisCannotRun() {
            when(autoAnalysis.canGenerate()).thenReturn(false);

            JsonNode findings = read(EVERY_FAMILY);

            assertEquals(AutoAnalysisStatus.CANNOT_COMPUTE.name(), findings.get("status").asString());
            assertTrue(findings.get("reason").asString().contains(NO_JFR_FILE), findings.toString());
            assertTrue(remedyOf(findings, "autoAnalysis").contains(NO_JFR_FILE), findings.toString());
            assertEquals(0, findings.get("followUp").get("nextTools").size());
            McpSchemaConformance.assertConforms(findings, McpSchemaGenerator.schemaOf(ProfileFindings.class));
            verify(autoAnalysis, never()).generate();
        }

        /**
         * A pprof or OTLP import never had a JFR file, so the rules cannot run on it: the same status,
         * worded so it does not claim a file was lost.
         */
        @Test
        void saysANonJfrProfileHasNoFileToAnalyse() {
            ProfileManager pprof = ProfileManagerFixture.profile(PROFILE_ID, RecordingEventSource.PPROF);
            AutoAnalysisManager none = mock(AutoAnalysisManager.class);
            when(pprof.autoAnalysisManager()).thenReturn(none);

            ProfileFindings findings = new ProfileFindingsReader(pprof, mock(JfrFlamegraphPanelProvider.class),
                    mock(StackSampleFlamegraphPanelProvider.class), EVERY_FAMILY).read();
            JsonNode document = Json.readTree(Json.toString(findings));

            assertEquals(AutoAnalysisStatus.CANNOT_COMPUTE, findings.status());
            assertTrue(findings.reason().contains("not imported from a JFR recording"), findings.reason());
            assertEquals(0, findings.followUp().nextTools().size());
            McpSchemaConformance.assertConforms(document, McpSchemaGenerator.schemaOf(ProfileFindings.class));
            verify(none, never()).generate();
        }

        @Test
        void dropsTheCallWhereTheJvmFamilyIsWithheld() {
            when(autoAnalysis.canGenerate()).thenReturn(true);

            JsonNode findings = read(new AdvertisedFamilies(Set.of(AdvertisedFamilies.PROFILES)));

            assertEquals(AutoAnalysisStatus.NOT_COMPUTED.name(), findings.get("status").asString());
            assertEquals(0, findings.get("followUp").get("nextTools").size());
        }

        @Test
        void conformsToTheGeneratedSchema() {
            when(autoAnalysis.canGenerate()).thenReturn(true);

            McpSchemaConformance.assertConforms(read(EVERY_FAMILY), McpSchemaGenerator.schemaOf(ProfileFindings.class));
        }
    }

    private JsonNode read(AdvertisedFamilies advertised) {
        ProfileFindings findings = new ProfileFindingsReader(manager, mock(JfrFlamegraphPanelProvider.class),
                mock(StackSampleFlamegraphPanelProvider.class), advertised).read();
        return Json.readTree(Json.toString(findings));
    }

    private void recordedContainerThrottling(ContainerCpuThrottlingData.Severity severity) {
        when(manager.flamegraphManager().allEventSummaries()).thenReturn(List.of(
                ProfileEvidenceMcpToolsTest.event(EventTypeName.CONTAINER_CPU_THROTTLING, 4, 0)));
        ContainerManager container = mock(ContainerManager.class);
        when(manager.containerManager()).thenReturn(container);
        when(container.throttling()).thenReturn(new ContainerCpuThrottlingData(
                new ContainerCpuThrottlingData.Verdict(true, severity, "CPU throttled", "Throttled periods"),
                new ContainerCpuThrottlingData.Summary(1000, 250, 4200.0, 25.0, 80.0, 2.0, 100.0, 2L),
                List.of(),
                List.of()));
    }

    private static String remedyOf(JsonNode findings, String subject) {
        for (JsonNode gap : findings.get("capabilityGaps")) {
            if (subject.equals(gap.get("subject").asString())) {
                return gap.get("remedy").asString();
            }
        }
        throw new AssertionError("no capability gap " + subject + " in " + findings);
    }

    private static List<String> subjects(JsonNode findings) {
        List<String> subjects = new ArrayList<>();
        for (JsonNode gap : findings.get("capabilityGaps")) {
            subjects.add(gap.get("subject").asString());
        }
        return subjects;
    }

    /** What {@code tools/list} advertises for the jvm family, as the profile-scoped toolset serves it. */
    private static List<McpToolSpec> jvmSpecs() {
        return McpTestToolsets.unscoped(JvmMcpTools.class, AdvertisedFamilies.JVM, profileId -> {
            throw new UnsupportedOperationException("specs need no target");
        }).specs();
    }
}
