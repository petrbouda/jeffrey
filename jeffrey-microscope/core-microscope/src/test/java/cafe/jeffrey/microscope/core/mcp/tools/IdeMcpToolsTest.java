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

import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.ide.IdeResolveRequest;
import cafe.jeffrey.microscope.core.manager.ide.IdeResolveResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeSourceResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeTarget;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetStatus;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult.IdeInstanceView;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult.IdeProjectView;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
        tools = new IdeMcpTools(ideBridge, profileManager, recordingCommitResolver, PROFILE_ID);
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

            String answer = tools.resolve(FQN, "process", 214);

            assertTrue(answer.contains("/code/app/OrderService.java"));
            assertTrue(answer.contains("214"));
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

            String answer = tools.resolve(FQN, "acquire", 88);

            assertTrue(answer.contains("decompiled"));
            assertTrue(answer.contains("\"decompiled\" : true") || answer.contains("\"decompiled\":true"));
        }

        @Test
        void aStaleFileSaysTheLineMayNoLongerExist() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(linked());
            resolvesTo(new IdeResolveResult(
                    true, "/code/app/OrderService.java", 214, "JAVA_LINE", false, false, true, null, null));

            assertTrue(tools.resolve(FQN, "process", 214).contains("no longer exists"));
        }

        @Test
        void anImpreciseHitSaysItIsTheDeclaration() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(linked());
            resolvesTo(new IdeResolveResult(
                    true, "/code/app/OrderService.java", 30, "JAVA_LINE", false, true, false, null, null));

            assertTrue(tools.resolve(FQN, "process", -1).contains("declaration"));
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

            String answer = tools.resolve(FQN, "process", 214);

            verify(ideBridge, never()).selectTarget(any(), any());
            verify(ideBridge, never()).resolve(any());
            assertTrue(answer.contains("service-fork"));
            assertTrue(answer.contains("ide_link"));
        }

        @Test
        void saysSoWhenNoIdeIsRunningAtAll() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(IdeTargetStatus.notLinked());
            when(ideBridge.discoverTargets(eq(PROFILE_ID), any())).thenReturn(IdeTargetsResult.empty());

            assertTrue(tools.resolve(FQN, "process", 214).contains("No IntelliJ IDEA window"));
        }

        @Test
        void linkRefusesAProjectIdThatIsNotOpen() {
            windowsOpen(project("a", "service", true));

            String answer = tools.link("gone");

            verify(ideBridge, never()).selectTarget(any(), any());
            assertTrue(answer.contains("gone"));
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

            String answer = tools.windows(FQN);

            // The recording's short commit is a prefix of the first window's HEAD and not of the
            // second, so exactly one row can be confirmed as the profiled build.
            assertTrue(answer.contains("| yes |"));
            assertTrue(answer.contains("| no |"));
        }

        @Test
        void saysWhenTheRecordingCarriesNoCommitToCompareAgainst() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(IdeTargetStatus.notLinked());
            windowsOpen(project("a", "service", true));

            assertTrue(tools.windows(FQN).contains("no commit tag"));
        }

        @Test
        void explainsItselfUnderTheSingleUrlBridge() {
            when(ideBridge.targetStatus(PROFILE_ID)).thenReturn(IdeTargetStatus.notSelectable());

            String answer = tools.windows(FQN);

            assertTrue(answer.contains("JFR Profiler"));
            verify(ideBridge, never()).discoverTargets(any(), any());
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

            assertTrue(tools.source(FQN).contains("Source is not available"));
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
