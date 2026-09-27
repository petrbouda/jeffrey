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

import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.ide.IdeOpenResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeResolveRequest;
import cafe.jeffrey.microscope.core.manager.ide.IdeResolveResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeSourceResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeTarget;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetStatus;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult.IdeInstanceView;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult.IdeProjectView;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.manager.ProfileManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdeMcpToolsTest {

    private static final String PROFILE_ID = "p-1";
    private static final String RECORDING_ID = "rec-1";
    private static final String FQN = "com.example.OrderService";
    private static final Instant RECORDED_AT = Instant.parse("2026-05-01T10:00:00Z");

    @Mock
    IdeBridge ideBridge;

    @Mock
    ProfileManager profileManager;

    @Mock
    RecordingCommitResolver recordingCommitResolver;

    private IdeMcpTools tools;

    @BeforeEach
    void setUp() {
        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                RECORDED_AT, RECORDED_AT.plusSeconds(60), RECORDED_AT, true, false, RECORDING_ID));
        when(recordingCommitResolver.resolve(any())).thenReturn(Optional.empty());
        tools = new IdeMcpTools(ideBridge, profileManager, recordingCommitResolver, PROFILE_ID, EVERY_FAMILY);
    }

    private static IdeProjectView project(String id, String name, boolean hasClass) {
        return new IdeProjectView(id, name, "/code/" + name, "main", "abc1234def5678", false, hasClass);
    }

    private void windowsOpen(IdeProjectView... projects) {
        when(ideBridge.discoverTargets(eq(PROFILE_ID), any()))
                .thenReturn(new IdeTargetsResult(null, List.of(
                        new IdeInstanceView(63342, "IntelliJ IDEA", "2026.1", 4821, List.of(projects)))));
    }

    private void resolvesTo(IdeResolveResult result) {
        when(ideBridge.resolve(any())).thenReturn(result);
    }

    private static JsonNode resolved(McpToolResult result) {
        return StructuredAnswers.jsonWithoutPage(IdeMcpTools.class, "resolve", result);
    }

    private static JsonNode windowsOf(McpToolResult result) {
        return StructuredAnswers.jsonWithoutPage(IdeMcpTools.class, "windows", result);
    }

    private static IdeResolveResult found() {
        return new IdeResolveResult(
                true, "/code/app/OrderService.java", 214, "JAVA_LINE", false, false, false,
                "2026-05-01T09:00:00Z", null);
    }

    /**
     * The whole point of the family: a lookup must not move somebody's editor. Every tool but
     * {@code ide_open} is expected to leave the IDE where it was.
     */
    @Nested
    class ResolvingDoesNotJump {

        @Test
        void resolveNeverOpensAnything() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(linked());
            resolvesTo(found());

            tools.resolve(FQN, "process", 214);

            verify(ideBridge, never()).open(any());
        }

        @Test
        void resolveReportsTheFileAndLine() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(linked());
            resolvesTo(found());

            JsonNode answer = resolved(tools.resolve(FQN, "process", 214));

            assertEquals("OK", answer.get("status").asString());
            assertEquals("/code/app/OrderService.java", answer.get("file").asString());
            assertEquals(214, answer.get("line").asInt());
            assertEquals(Instant.parse("2026-05-01T09:00:00Z").toEpochMilli(),
                    answer.get("sourceModifiedAtEpochMs").asLong(), "the IDE's ISO instant as epoch milliseconds");
            assertTrue(StructuredAnswers.guidance(answer).contains("Read the file at this line"), answer.toString());
        }

        @Test
        void passesTheRecordingTimeSoTheIdeCanReportAStaleFile() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(linked());
            resolvesTo(found());

            tools.resolve(FQN, "process", 214);

            ArgumentCaptor<IdeResolveRequest> request = ArgumentCaptor.forClass(IdeResolveRequest.class);
            verify(ideBridge).resolve(request.capture());
            assertEquals(RECORDED_AT, request.getValue().recordingTime());
        }
    }

    /**
     * A location that cannot be cited has to say so. Each caveat comes back with the one instruction
     * that makes it actionable, rather than as a flag the reader has to interpret.
     */
    @Nested
    class QualifiedLocations {

        @Test
        void aDecompiledFileSaysNotToCiteItsLines() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(linked());
            resolvesTo(new IdeResolveResult(
                    true, "/jars/lib.jar!/Pool.class", 88, "JAVA_LINE", true, false, false, null, null));

            JsonNode answer = resolved(tools.resolve(FQN, "acquire", 88));

            assertTrue(answer.get("decompiled").asBoolean());
            assertTrue(StructuredAnswers.guidance(answer).contains("decompiled"), answer.toString());
            assertEquals(FQN, StructuredAnswers.call(answer, "ide_source").get("className").asString());
            assertTrue(answer.get("sourceModifiedAtEpochMs").isNull());
        }

        @Test
        void aStaleFileSaysTheLineMayNoLongerExist() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(linked());
            resolvesTo(new IdeResolveResult(
                    true, "/code/app/OrderService.java", 214, "JAVA_LINE", false, false, true, null, null));

            JsonNode answer = resolved(tools.resolve(FQN, "process", 214));

            assertTrue(answer.get("stale").asBoolean());
            assertTrue(StructuredAnswers.guidance(answer).contains("no longer exists"), answer.toString());
            assertEquals(List.of("profiles_get"), StructuredAnswers.nextTools(answer));
        }

        @Test
        void anImpreciseHitSaysItIsTheDeclaration() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(linked());
            resolvesTo(new IdeResolveResult(
                    true, "/code/app/OrderService.java", 30, "JAVA_LINE", false, true, false, null, null));

            JsonNode answer = resolved(tools.resolve(FQN, "process", -1));

            assertTrue(answer.get("imprecise").asBoolean());
            assertTrue(StructuredAnswers.guidance(answer).contains("declaration"), answer.toString());
        }
    }

    /**
     * With no reader to answer a picker, the window may be chosen only when there is nothing to
     * choose between. Anything else is reported with the candidates named.
     */
    @Nested
    class ChoosingTheWindow {

        @Test
        void linksTheOnlyWindowHoldingTheClass() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(IdeTargetStatus.notLinked());
            windowsOpen(project("a", "service", true), project("b", "unrelated", false));
            resolvesTo(found());

            tools.resolve(FQN, "process", 214);

            ArgumentCaptor<IdeTarget> selected = ArgumentCaptor.forClass(IdeTarget.class);
            verify(ideBridge).selectTarget(eq(PROFILE_ID), selected.capture());
            assertEquals("a", selected.getValue().projectId());
        }

        @Test
        void linksASingleWindowEvenWhenItDoesNotAdmitToTheClass() {
            // The normal case for a frame in a dependency: the class is not in the project's own
            // sources, and there is still only one checkout this could be about.
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(IdeTargetStatus.notLinked());
            windowsOpen(project("a", "service", false));
            resolvesTo(found());

            tools.resolve(FQN, "process", 214);

            verify(ideBridge).selectTarget(eq(PROFILE_ID), any());
        }

        @Test
        void refusesToChooseBetweenTwoWindowsThatBothHaveTheClass() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(IdeTargetStatus.notLinked());
            windowsOpen(project("a", "service", true), project("b", "service-fork", true));

            JsonNode answer = resolved(tools.resolve(FQN, "process", 214));

            verify(ideBridge, never()).selectTarget(any(), any());
            verify(ideBridge, never()).resolve(any());
            assertEquals("NOT_LINKED", answer.get("status").asString());
            assertEquals("service-fork", answer.get("candidates").get(1).get("project").asString());
            assertEquals("b", answer.get("candidates").get(1).get("projectId").asString());
            assertTrue(answer.get("file").isNull());
            assertEquals(FQN, StructuredAnswers.call(answer, "ide_windows").get("className").asString());
            assertTrue(StructuredAnswers.guidance(answer).contains("ide_link"), answer.toString());
        }

        @Test
        void saysSoWhenNoIdeIsRunningAtAll() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(IdeTargetStatus.notLinked());
            when(ideBridge.discoverTargets(eq(PROFILE_ID), any())).thenReturn(IdeTargetsResult.empty());

            JsonNode answer = resolved(tools.resolve(FQN, "process", 214));

            assertEquals("NO_IDE_RUNNING", answer.get("status").asString());
            assertTrue(answer.get("reason").asString().contains("No IntelliJ IDEA window"));
            assertEquals(List.of(), StructuredAnswers.nextTools(answer));
        }

        @Test
        void linkingAWindowAnswersWhichOne() {
            windowsOpen(project("a", "service", true));

            JsonNode answer = StructuredAnswers.jsonWithoutPage(IdeMcpTools.class, "link", tools.link("a"));

            assertEquals("a", answer.get("projectId").asString());
            assertEquals("service", answer.get("project").asString());
            verify(ideBridge).selectTarget(eq(PROFILE_ID), any());
        }

        @Test
        void openingWhileSeveralWindowsCouldBeMeantListsThemAndOpensNothing() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(IdeTargetStatus.notLinked());
            windowsOpen(project("a", "service", true), project("b", "service-fork", true));

            JsonNode answer = StructuredAnswers.jsonWithoutPage(IdeMcpTools.class, "open",
                    tools.open(FQN, "process", 214));

            assertEquals("NOT_LINKED", answer.get("status").asString());
            assertEquals(2, answer.get("candidates").size());
            verify(ideBridge, never()).open(any());
        }

        @Test
        void openingALinkedWindowSaysItOpened() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(linked());
            when(ideBridge.open(any())).thenReturn(IdeOpenResult.succeeded());

            JsonNode answer = StructuredAnswers.jsonWithoutPage(IdeMcpTools.class, "open",
                    tools.open(FQN, "process", 214));

            assertEquals("OK", answer.get("status").asString());
            assertEquals(214, answer.get("line").asInt());
        }

        /** The source tool answers text, so its ambiguous answer names the candidates in words. */
        @Test
        void sourceWhileSeveralWindowsCouldBeMeantNamesThemInText() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(IdeTargetStatus.notLinked());
            windowsOpen(project("a", "service", true), project("b", "service-fork", true));

            String answer = tools.source(FQN);

            assertTrue(answer.contains("service-fork (projectId b)"), answer);
            assertTrue(answer.contains("ide_link"), answer);
        }

        @Test
        void linkRefusesAProjectIdThatIsNotOpen() {
            windowsOpen(project("a", "service", true));

            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> tools.link("gone"));

            verify(ideBridge, never()).selectTarget(any(), any());
            assertTrue(error.getMessage().contains("gone"), error.getMessage());
        }
    }

    @Nested
    class TheWindowListing {

        @Test
        void marksWhichWindowIsOnTheProfiledCommit() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(IdeTargetStatus.notLinked());
            when(recordingCommitResolver.resolve(RECORDING_ID)).thenReturn(Optional.of("abc1234"));
            when(ideBridge.discoverTargets(eq(PROFILE_ID), any()))
                    .thenReturn(new IdeTargetsResult(null, List.of(new IdeInstanceView(
                            63342, "IntelliJ IDEA", "2026.1", 4821, List.of(
                            project("a", "service", true),
                            new IdeProjectView("b", "fork", "/code/fork", "main", "999999999", false, true))))));

            JsonNode answer = windowsOf(tools.windows(FQN));

            // The recording's short commit is a prefix of the first window's HEAD and not of the
            // second, so exactly one row can be confirmed as the profiled build.
            assertTrue(answer.get("windows").get(0).get("sameCommitAsRecording").asBoolean());
            assertFalse(answer.get("windows").get(1).get("sameCommitAsRecording").asBoolean());
            assertEquals("abc1234", answer.get("recordingCommit").asString());
        }

        @Test
        void saysWhenTheRecordingCarriesNoCommitToCompareAgainst() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(IdeTargetStatus.notLinked());
            windowsOpen(project("a", "service", true));

            JsonNode answer = windowsOf(tools.windows(FQN));

            assertTrue(answer.get("windows").get(0).get("sameCommitAsRecording").isNull());
            assertTrue(StructuredAnswers.guidance(answer).contains("no commit tag"), answer.toString());
            assertEquals("a", StructuredAnswers.call(answer, "ide_link").get("projectId").asString(),
                    "the only window holding the class can be linked straight away");
        }

        @Test
        void saysSoWhenNoIdeAnswers() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(IdeTargetStatus.notLinked());
            when(ideBridge.discoverTargets(eq(PROFILE_ID), any())).thenReturn(IdeTargetsResult.empty());

            JsonNode answer = windowsOf(tools.windows(null));

            assertEquals("NO_IDE_RUNNING", answer.get("status").asString());
            assertEquals(0, answer.get("windows").size());
        }

        /** Without a class name, whether a window holds it is unknown, never a false no. */
        @Test
        void aWindowListedWithoutAClassNameSaysNothingAboutTheClass() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(IdeTargetStatus.notLinked());
            windowsOpen(project("a", "service", false), project("b", "other", false));

            JsonNode answer = windowsOf(tools.windows(null));

            assertTrue(answer.get("windows").get(0).get("hasClass").isNull());
            assertEquals(List.of(), StructuredAnswers.nextTools(answer), "nothing to link without a choice");
        }

        @Test
        void explainsItselfUnderTheSingleUrlBridge() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(IdeTargetStatus.notSelectable());

            JsonNode answer = windowsOf(tools.windows(FQN));

            assertEquals("NOT_SELECTABLE", answer.get("status").asString());
            assertTrue(answer.get("reason").asString().contains("JFR Profiler"));
            verify(ideBridge, never()).discoverTargets(any(), any());
        }
    }

    @Nested
    class Surface {

        /** ide_source answers a document; every other ide_ tool answers a record. */
        @Test
        void everyToolButSourceDeclaresAnOutputSchema() {
            assertEquals(List.of("source"), StructuredAnswers.unschematised(IdeMcpTools.class));
        }

        /** The plugin sends its resolver's enum names verbatim; the schema says which they are. */
        @Test
        void aLocationsKindNamesThePluginsValues() {
            String description = McpSchemaGenerator.schemaOf(IdeMcpTools.Location.class)
                    .path("properties").path("kind").path("description").asString();

            for (String kind : List.of("JAVA_PRECISE", "JAVA_LINE", "KOTLIN_LINE", "KOTLIN_FALLBACK")) {
                assertTrue(description.contains(kind), description);
            }
        }
    }

    @Nested
    class Arguments {

        @Test
        void aBlankClassNameIsRefusedWithHowToGetOne() {
            IllegalArgumentException failure =
                    assertThrows(IllegalArgumentException.class, () -> tools.resolve(" ", "process", 1));

            assertTrue(failure.getMessage().contains("className"));
            assertTrue(failure.getMessage().contains("flamegraph_export"));
        }

        @Test
        void sourceFallsBackToTheBridgeMessageWhenThereIsNone() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(linked());
            when(ideBridge.fetchSource(any()))
                    .thenReturn(IdeSourceResult.failed("Source is not available for this class"));

            ToolExecutionException error = assertThrows(
                    ToolExecutionException.class,
                    () -> tools.source(FQN));

            assertTrue(error.getMessage().contains("Source is not available"), error.getMessage());
        }

        @Test
        void decompiledSourceIsLabelledAsSuch() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(linked());
            when(ideBridge.fetchSource(any()))
                    .thenReturn(IdeSourceResult.succeeded("class Pool {}", true));

            String answer = tools.source(FQN);

            assertTrue(answer.contains("Decompiled"));
            assertFalse(answer.contains("as the IDE has it."));
        }
    }

    private static IdeTargetStatus linked() {
        return IdeTargetStatus.linked(
                new IdeTarget(63342, "a", "IntelliJ IDEA", "service", "/code/service", 4821));
    }
}
