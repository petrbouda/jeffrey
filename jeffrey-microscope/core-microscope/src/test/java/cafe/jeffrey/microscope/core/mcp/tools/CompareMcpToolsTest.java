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

import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.DifferentialFlamegraphManager;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompareMcpToolsTest {

    private static final String PRIMARY_ID = "after-the-change";
    private static final String BASELINE_ID = "before-the-change";
    private static final String PRIMARY_NAME = "Run 42";
    private static final String BASELINE_NAME = "Run 41";
    private static final String CPU_EVENT = EventTypeName.EXECUTION_SAMPLE;
    private static final String ALLOCATION_EVENT = EventTypeName.OBJECT_ALLOCATION_SAMPLE;
    private static final String ONLY_IN_PRIMARY_EVENT = EventTypeName.JAVA_MONITOR_ENTER;
    private static final String ONLY_IN_BASELINE_EVENT = EventTypeName.THREAD_PARK;

    private static final int DEFAULT_MOVEMENT_LIMIT = 15;
    private static final int MAX_MOVEMENT_LIMIT = 100;

    private static final String MOVEMENTS_MARKDOWN = "| method | delta |\n| Orders.load | +12% |";
    private static final String DIFF_EXPORT_MARKDOWN = "Orders.load 12% (was 4%)";

    private static final long ONE_MINUTE_SECONDS = 60L;
    private static final long TWENTY_SECONDS = 20L;

    @Mock
    ProfileManager primaryManager;

    @Mock
    ProfileManager baselineManager;

    @Mock
    FlamegraphManager primaryFlamegraphManager;

    @Mock
    FlamegraphManager baselineFlamegraphManager;

    @Mock
    DifferentialFlamegraphManager diffManager;

    @Captor
    ArgumentCaptor<GraphParameters> parametersCaptor;

    /**
     * The answers carry a link into the UI, and {@code UiLinks} reads the request bound to the
     * current thread to build it.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        primaryLasting(ONE_MINUTE_SECONDS);
        baselineLasting(ONE_MINUTE_SECONDS);
        when(primaryManager.flamegraphManager()).thenReturn(primaryFlamegraphManager);
        when(baselineManager.flamegraphManager()).thenReturn(baselineFlamegraphManager);
        when(primaryManager.diffFlamegraphManager(baselineManager)).thenReturn(diffManager);
        when(primaryFlamegraphManager.eventSummaries()).thenReturn(List.of());
        when(baselineFlamegraphManager.eventSummaries()).thenReturn(List.of());
        when(diffManager.eventSummaries()).thenReturn(List.of());
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void primaryLasting(long seconds) {
        when(primaryManager.info()).thenReturn(info(PRIMARY_ID, PRIMARY_NAME, seconds));
    }

    private void baselineLasting(long seconds) {
        when(baselineManager.info()).thenReturn(info(BASELINE_ID, BASELINE_NAME, seconds));
    }

    private static ProfileInfo info(String id, String name, long seconds) {
        return new ProfileInfo(
                id, "project-1", "workspace-1", name, RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(seconds), Instant.EPOCH,
                true, false, "recording-" + id);
    }

    private CompareMcpTools tools() {
        Function<String, ProfileManager> resolver = profileId -> {
            if (BASELINE_ID.equals(profileId)) {
                return baselineManager;
            }
            throw new AssertionError("unexpected baseline profile id: " + profileId);
        };
        return new CompareMcpTools(primaryManager, resolver);
    }

    private static EventSummaryResult shared(
            String code, long primarySamples, long baselineSamples, long weight) {

        return new EventSummaryResult(
                code, code,
                new EventSummaryResult.SingleResult(
                        code, code, null, null, primarySamples, weight, false, Map.of()),
                new EventSummaryResult.SingleResult(
                        code, code, null, null, baselineSamples, weight, false, Map.of()));
    }

    private static EventSummaryResult exclusive(String code, long samples) {
        return new EventSummaryResult(
                code, code,
                new EventSummaryResult.SingleResult(code, code, null, null, samples, 0, false, Map.of()),
                null);
    }

    @Test
    void omittedWindowDoesNotClipBaselineToPrimaryLength() {
        when(diffManager.rankedMovements(any(), anyInt())).thenReturn(MOVEMENTS_MARKDOWN);
        tools().movements(BASELINE_ID, CPU_EVENT, null, null, null, null, null, null);
        verify(diffManager).rankedMovements(parametersCaptor.capture(), anyInt());
        assertTrue(parametersCaptor.getValue().timeRange() == null,
                "No explicit window must leave both recordings unfiltered");
    }

    @Test
    void omittedEndKeepsEachRecordingsOwnEnd() {
        when(diffManager.rankedMovements(any(), anyInt())).thenReturn(MOVEMENTS_MARKDOWN);
        tools().movements(BASELINE_ID, CPU_EVENT, null, 10_000L, null, null, null, null);
        verify(diffManager).rankedMovements(parametersCaptor.capture(), anyInt());
        assertTrue(parametersCaptor.getValue().timeRange().end() == null,
                "A start-only window must not clip the longer recording to the shorter recording's end");
    }

    @Nested
    class ListComparability {

        @Test
        void namesBothSidesAndWhatTheyHaveInCommon() {
            when(diffManager.eventSummaries())
                    .thenReturn(List.of(shared(CPU_EVENT, 4_200, 3_900, 0)));

            String out = tools().list(BASELINE_ID);

            assertTrue(out.contains("\"profileId\":\"" + PRIMARY_ID + "\""), out);
            assertTrue(out.contains("\"profileId\":\"" + BASELINE_ID + "\""), out);
            assertTrue(out.contains("\"eventType\":\"" + CPU_EVENT + "\""), out);
            assertTrue(out.contains("\"primarySamples\":4200"), out);
            assertTrue(out.contains("\"baselineSamples\":3900"), out);
        }

        /**
         * Both volumes travel with the type so a reader can see what they are about to compare
         * before asking for a delta - two recordings can always be subtracted and the result always
         * looks like a finding.
         */
        @Test
        void carriesTheWeightOnlyForAnEventTypeThatIsWeighed() {
            when(diffManager.eventSummaries()).thenReturn(List.of(
                    shared(CPU_EVENT, 4_200, 3_900, 0),
                    shared(ALLOCATION_EVENT, 130, 96, 9_000_000L)));

            String out = tools().list(BASELINE_ID);

            assertTrue(out.contains("\"weightUnit\":\"bytes\""), out);
            assertTrue(out.contains("\"primaryWeight\":9000000"), out);
            assertTrue(out.contains("\"primaryWeight\":null"), out);
        }

        @Test
        void saysSoWhenTheTwoProfilesShareNothingComparable() {
            String out = tools().list(BASELINE_ID);

            assertTrue(out.contains("no event type in common"), out);
            assertTrue(out.contains("profiles_features"), out);
        }

        /**
         * A type recorded on one side only is a difference between the two profiler configurations,
         * and a reader who is not told that will read its absence as the application no longer doing
         * the work.
         */
        @Test
        void reportsATypeRecordedOnOneSideOnlyAsAConfigurationDifference() {
            when(diffManager.eventSummaries())
                    .thenReturn(List.of(shared(CPU_EVENT, 4_200, 3_900, 0)));
            when(primaryFlamegraphManager.eventSummaries()).thenReturn(List.of(
                    exclusive(CPU_EVENT, 4_200), exclusive(ONLY_IN_PRIMARY_EVENT, 71)));
            when(baselineFlamegraphManager.eventSummaries()).thenReturn(List.of(
                    exclusive(CPU_EVENT, 3_900), exclusive(ONLY_IN_BASELINE_EVENT, 12)));

            String out = tools().list(BASELINE_ID);

            assertTrue(out.contains("\"onlyInPrimary\":[\"" + ONLY_IN_PRIMARY_EVENT + "\"]"), out);
            assertTrue(out.contains("\"onlyInBaseline\":[\"" + ONLY_IN_BASELINE_EVENT + "\"]"), out);
            assertTrue(out.contains("profiler-configuration difference"), out);
        }

        /**
         * Sample counts scale with recording time, so recordings of noticeably different length
         * produce deltas that look precise and mean nothing unless the reader is told first.
         */
        @Test
        void warnsWhenTheTwoRecordingsAreOfVeryDifferentLength() {
            when(diffManager.eventSummaries())
                    .thenReturn(List.of(shared(CPU_EVENT, 4_200, 3_900, 0)));
            baselineLasting(TWENTY_SECONDS);

            assertTrue(tools().list(BASELINE_ID).contains("noticeably different length"));
        }

        @Test
        void staysQuietAboutLengthWhenTheRecordingsAreComparable() {
            when(diffManager.eventSummaries())
                    .thenReturn(List.of(shared(CPU_EVENT, 4_200, 3_900, 0)));

            assertFalse(tools().list(BASELINE_ID).contains("noticeably different length"));
        }
    }

    @Nested
    class BaselineArgument {

        @Test
        void refusesAMissingBaseline() {
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> tools().list(null));

            assertTrue(thrown.getMessage().contains("baselineProfileId is required"), thrown.getMessage());
        }

        @Test
        void refusesABlankBaselineTheSameWay() {
            assertThrows(IllegalArgumentException.class, () -> tools().list("   "));
        }

        /**
         * A profile subtracted from itself produces a tree in which nothing moved, which is a
         * well-formed answer to a question nobody meant to ask.
         */
        @Test
        void refusesToCompareAProfileWithItself() {
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> tools().list(PRIMARY_ID));

            assertTrue(thrown.getMessage().contains("same profile"), thrown.getMessage());
        }

        @Test
        void trimsTheBaselineIdBeforeResolvingIt() {
            when(diffManager.eventSummaries())
                    .thenReturn(List.of(shared(CPU_EVENT, 4_200, 3_900, 0)));

            assertTrue(tools().list("  " + BASELINE_ID + "  ").contains(BASELINE_ID));
        }
    }

    @Nested
    class Movements {

        @Test
        void ranksTheMovementsAndSaysWhereToDrillIn() {
            when(diffManager.rankedMovements(any(), anyInt())).thenReturn(MOVEMENTS_MARKDOWN);

            String out = tools().movements(
                    BASELINE_ID, CPU_EVENT, null, null, null, null, null, null);

            assertTrue(out.contains("Orders.load"), out);
            assertTrue(out.contains("compare_flamegraph"), out);
            assertTrue(out.contains("/profiles/" + PRIMARY_ID), out);
        }

        @Test
        void fallsBackToTheDefaultLimitWhenNoneIsAsked() {
            when(diffManager.rankedMovements(any(), anyInt())).thenReturn(MOVEMENTS_MARKDOWN);

            tools().movements(BASELINE_ID, CPU_EVENT, null, null, null, null, null, null);

            verify(diffManager).rankedMovements(any(), eq(DEFAULT_MOVEMENT_LIMIT));
        }

        /**
         * The cap may be lowered but not raised: a model asking for everything is asking for its own
         * context to be spent on one ranking.
         */
        @Test
        void capsALimitTheCallerAskedToRaise() {
            when(diffManager.rankedMovements(any(), anyInt())).thenReturn(MOVEMENTS_MARKDOWN);

            tools().movements(BASELINE_ID, CPU_EVENT, 5_000, null, null, null, null, null);

            verify(diffManager).rankedMovements(any(), eq(MAX_MOVEMENT_LIMIT));
        }

        /**
         * Thread names differ between two runs - {@code pool-1-thread-7} is not the same worker
         * twice - so a per-thread tree would report every branch as appeared or vanished. A
         * comparison is always thread-aggregated.
         */
        @Test
        void comparesThreadAggregatedWhateverTheFiltersSay() {
            when(diffManager.rankedMovements(any(), anyInt())).thenReturn(MOVEMENTS_MARKDOWN);

            tools().movements(BASELINE_ID, CPU_EVENT, null, null, null, true, true, true);

            verify(diffManager).rankedMovements(parametersCaptor.capture(), anyInt());
            assertFalse(parametersCaptor.getValue().threadMode());
            assertEquals(CPU_EVENT, parametersCaptor.getValue().eventType().code());
        }

        @Test
        void refusesAMissingEventType() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().movements(BASELINE_ID, null, null, null, null, null, null, null));

            assertTrue(thrown.getMessage().contains("eventType is required"), thrown.getMessage());
        }

        @Test
        void refusesAWindowThatEndsBeforeItStarts() {
            assertThrows(IllegalArgumentException.class,
                    () -> tools().movements(
                            BASELINE_ID, CPU_EVENT, null, 30_000L, 10_000L, null, null, null));
        }
    }

    @Nested
    class DifferentialFlamegraph {

        @Test
        void exportsTheMergedTreeAndSaysWhatItsAbsencesMean() {
            when(diffManager.generateAiExport(any(), any())).thenReturn(DIFF_EXPORT_MARKDOWN);

            String out = tools().flamegraph(
                    BASELINE_ID, CPU_EVENT, null, null, null, null, null, null);

            assertTrue(out.contains("Orders.load"), out);
            assertTrue(out.contains("did not move"), out);
            assertTrue(out.contains("/profiles/" + PRIMARY_ID), out);
        }

        @Test
        void refusesAThresholdOutsideTheOpenZeroToHundredRange() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().flamegraph(
                            BASELINE_ID, CPU_EVENT, 0.0, null, null, null, null, null));

            assertTrue(thrown.getMessage().contains("thresholdPct"), thrown.getMessage());
        }
    }
}
