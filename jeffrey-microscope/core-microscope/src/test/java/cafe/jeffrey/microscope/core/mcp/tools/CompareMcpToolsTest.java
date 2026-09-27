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
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.DifferentialFlamegraphManager;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.shared.common.model.EventTypeName;
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
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);

    /** The run under examination, and the baseline recorded an hour before it: two different clocks. */
    private static final Instant PRIMARY_START = Instant.parse("2026-03-01T12:00:00Z");
    private static final Instant BASELINE_START = PRIMARY_START.minusSeconds(3_600);
    private static final long PRIMARY_START_MS = PRIMARY_START.toEpochMilli();
    private static final long BASELINE_START_MS = BASELINE_START.toEpochMilli();

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
        when(diffManager.rankedMovements(any(), anyInt())).thenReturn(MOVEMENTS_MARKDOWN);
        when(diffManager.generateAiExport(any(), any())).thenReturn(DIFF_EXPORT_MARKDOWN);
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void primaryLasting(long seconds) {
        when(primaryManager.info()).thenReturn(info(PRIMARY_ID, PRIMARY_NAME, PRIMARY_START, seconds));
    }

    private void baselineLasting(long seconds) {
        when(baselineManager.info()).thenReturn(info(BASELINE_ID, BASELINE_NAME, BASELINE_START, seconds));
    }

    private static ProfileInfo info(String id, String name, Instant start, long seconds) {
        return new ProfileInfo(
                id, "project-1", "workspace-1", name, RecordingEventSource.JDK,
                start, start.plusSeconds(seconds), start,
                true, false, "recording-" + id);
    }

    /** A profile whose recording carries no window at all -- an import, or a parse that never reached the end. */
    private static ProfileInfo infoWithoutTimestamps(String id, String name) {
        return new ProfileInfo(
                id, "project-1", "workspace-1", name, RecordingEventSource.JDK,
                null, null, Instant.EPOCH, true, false, "recording-" + id);
    }

    private CompareMcpTools tools() {
        return tools(EVERY_FAMILY);
    }

    private CompareMcpTools tools(AdvertisedFamilies advertised) {
        Function<String, ProfileManager> resolver = profileId -> {
            if (BASELINE_ID.equals(profileId)) {
                return baselineManager;
            }
            throw new AssertionError("unexpected baseline profile id: " + profileId);
        };
        return new CompareMcpTools(primaryManager, resolver, CLOCK, advertised);
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

    /** The answer's structured content, checked against the schema the tool advertises and its link. */
    private static JsonNode conforming(String method, McpToolResult result) {
        JsonNode structured = result.structuredContent();
        McpSchemaConformance.assertConforms(structured, schemaOf(method));
        UiLinkRoutes.assertResolves(structured.get("uiLink").asString());
        return structured;
    }

    private static JsonNode schemaOf(String method) {
        Method tool = Arrays.stream(CompareMcpTools.class.getMethods())
                .filter(candidate -> candidate.getName().equals(method))
                .findFirst()
                .orElseThrow();
        return McpSchemaGenerator.schemaOf(tool.getAnnotation(McpOutputSchema.class).value());
    }

    private static List<McpToolSpec> reachable() {
        return CatalogueSpecs.of(
                CatalogueSpecs.profileScoped(CompareMcpTools.class, AdvertisedFamilies.COMPARE),
                CatalogueSpecs.profileScoped(FlamegraphMcpTools.class, AdvertisedFamilies.FLAMEGRAPH),
                CatalogueSpecs.profileScoped(ProfileMcpTools.class, AdvertisedFamilies.PROFILES));
    }

    private static List<String> nextTools(JsonNode structured) {
        return structured.get("followUp").get("nextTools").valueStream()
                .map(call -> call.get("tool").asString())
                .toList();
    }

    @Test
    void omittedWindowDoesNotClipBaselineToPrimaryLength() {
        tools().movements(BASELINE_ID, CPU_EVENT, null, null, null, null, null, null);
        verify(diffManager).rankedMovements(parametersCaptor.capture(), anyInt());
        assertNull(parametersCaptor.getValue().timeRange(),
                "No explicit window must leave both recordings unfiltered");
    }

    @Test
    void omittedEndKeepsEachRecordingsOwnEnd() {
        tools().movements(BASELINE_ID, CPU_EVENT, null, PRIMARY_START_MS + 10_000, null, null, null, null);
        verify(diffManager).rankedMovements(parametersCaptor.capture(), anyInt());
        assertEquals(Duration.ofSeconds(10), parametersCaptor.getValue().timeRange().start());
        assertNull(parametersCaptor.getValue().timeRange().end(),
                "A start-only window must not clip the longer recording to the shorter recording's end");
    }

    /**
     * The window is given on the primary's clock and applied at the same offset into each recording,
     * however far apart the two ran; the answer says where it landed in both.
     */
    @Nested
    class Window {

        @Test
        void isGivenOnThePrimarysClockAndAppliedAtTheSameOffsetIntoBoth() {
            JsonNode out = conforming("movements", tools().movements(BASELINE_ID, CPU_EVENT, null,
                    PRIMARY_START_MS + 10_000, PRIMARY_START_MS + 20_000, null, null, null));

            verify(diffManager).rankedMovements(parametersCaptor.capture(), anyInt());
            assertEquals(Duration.ofSeconds(10), parametersCaptor.getValue().timeRange().start());
            assertEquals(Duration.ofSeconds(20), parametersCaptor.getValue().timeRange().end());
            JsonNode window = out.get("window");
            assertEquals(PRIMARY_START_MS + 10_000, window.get("primary").get("startEpochMs").asLong());
            assertEquals(PRIMARY_START_MS + 20_000, window.get("primary").get("endEpochMs").asLong());
            assertEquals(BASELINE_START_MS + 10_000, window.get("baseline").get("startEpochMs").asLong());
            assertEquals(BASELINE_START_MS + 20_000, window.get("baseline").get("endEpochMs").asLong());
        }

        @Test
        void anOpenEndIsEachRecordingsOwnEnd() {
            baselineLasting(TWENTY_SECONDS);

            JsonNode window = conforming("movements", tools().movements(BASELINE_ID, CPU_EVENT, null,
                    PRIMARY_START_MS + 10_000, null, null, null, null)).get("window");

            assertEquals(PRIMARY_START_MS + 60_000, window.get("primary").get("endEpochMs").asLong());
            assertEquals(BASELINE_START_MS + 20_000, window.get("baseline").get("endEpochMs").asLong());
        }

        @Test
        void noWindowIsTheWholeOfBoth() {
            assertTrue(conforming("movements", tools().movements(BASELINE_ID, CPU_EVENT, null, null, null,
                    null, null, null)).get("window").isNull());
        }

        @Test
        void refusesAWindowOutsideThePrimaryAndNamesItsSpan() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().flamegraph(BASELINE_ID, CPU_EVENT, null, 10_000L, 20_000L, null, null, null));

            assertTrue(thrown.getMessage().contains(PRIMARY_ID), thrown.getMessage());
            assertTrue(thrown.getMessage().contains(PRIMARY_START_MS + ".." + (PRIMARY_START_MS + 60_000)),
                    thrown.getMessage());
            verify(diffManager, never()).generateAiExport(any(), any());
        }

        /** The baseline is shorter than the offset the window reaches; placing it there would compare nothing. */
        @Test
        void refusesAWindowReachingPastTheBaselinesEndAndSaysHowLongItIs() {
            baselineLasting(TWENTY_SECONDS);

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().movements(BASELINE_ID, CPU_EVENT, null,
                            PRIMARY_START_MS + 10_000, PRIMARY_START_MS + 30_000, null, null, null));

            assertTrue(thrown.getMessage().contains(BASELINE_ID), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("20000 ms long"), thrown.getMessage());
        }

        @Test
        void acceptsAnEndExactlyAtTheBaselinesEnd() {
            baselineLasting(TWENTY_SECONDS);

            JsonNode window = conforming("movements", tools().movements(BASELINE_ID, CPU_EVENT, null,
                    PRIMARY_START_MS + 10_000, PRIMARY_START_MS + 20_000, null, null, null)).get("window");

            assertEquals(BASELINE_START_MS + 20_000, window.get("baseline").get("endEpochMs").asLong());
        }

        /** A start at the baseline's very end leaves it nothing to compare. */
        @Test
        void refusesAStartOnlyWindowAtTheBaselinesEnd() {
            baselineLasting(TWENTY_SECONDS);

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().movements(BASELINE_ID, CPU_EVENT, null,
                            PRIMARY_START_MS + 20_000, null, null, null, null));

            assertTrue(thrown.getMessage().contains(BASELINE_ID), thrown.getMessage());
        }

        @Test
        void refusesAWindowOnARecordingWithoutTimestamps() {
            when(baselineManager.info()).thenReturn(infoWithoutTimestamps(BASELINE_ID, BASELINE_NAME));

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().movements(BASELINE_ID, CPU_EVENT, null,
                            PRIMARY_START_MS + 10_000, null, null, null, null));

            assertTrue(thrown.getMessage().contains("omit startEpochMs and endEpochMs"), thrown.getMessage());
        }

        @Test
        void refusesAWindowThatEndsBeforeItStarts() {
            assertThrows(IllegalArgumentException.class,
                    () -> tools().movements(BASELINE_ID, CPU_EVENT, null,
                            PRIMARY_START_MS + 30_000, PRIMARY_START_MS + 10_000, null, null, null));
        }

        @Test
        void isTakenInEpochMillisecondsByBothExports() {
            for (String tool : List.of("compare_movements", "compare_flamegraph")) {
                JsonNode properties = new ReflectiveToolset(tools(), "compare").specs().stream()
                        .filter(candidate -> candidate.name().equals(tool))
                        .findFirst()
                        .orElseThrow()
                        .inputSchema().path("properties");

                assertTrue(properties.has("startEpochMs"), tool);
                assertTrue(properties.has("endEpochMs"), tool);
                assertFalse(properties.has("startMs"), tool);
                assertFalse(properties.has("endMs"), tool);
            }
        }
    }

    /**
     * The link opens the differential graph of this very comparison: the baseline, the event type, the
     * window on the primary's clock and the filters travel in it, so the reader sees what the agent read.
     */
    @Nested
    class PairLink {

        private String linkOf(JsonNode out) {
            return out.get("uiLink").asString();
        }

        @Test
        void namesTheBaselineTheEventTypeAndTheDifferentialMode() {
            String uiLink = linkOf(conforming("flamegraph",
                    tools().flamegraph(BASELINE_ID, CPU_EVENT, null, null, null, false, true, true)));

            assertTrue(uiLink.contains("/profiles/" + PRIMARY_ID + "/flamegraph-view?"), uiLink);
            assertTrue(uiLink.contains("eventType=" + CPU_EVENT), uiLink);
            assertTrue(uiLink.contains("graphMode=DIFFERENTIAL"), uiLink);
            assertTrue(uiLink.contains("baseline=" + BASELINE_ID), uiLink);
            assertTrue(uiLink.contains("excludeIdleSamples=true"), uiLink);
            assertTrue(uiLink.contains("excludeNonJavaSamples=true"), uiLink);
            assertFalse(uiLink.contains("useWeight"), uiLink);
            assertFalse(uiLink.contains("useThreadMode"), "a comparison is always thread-aggregated: " + uiLink);
        }

        /** The view reads the weighting as a flag; an unsaid one is the comparison's own default. */
        @Test
        void carriesTheWeightingTheComparisonResolved() {
            String allocation = linkOf(conforming("flamegraph",
                    tools().flamegraph(BASELINE_ID, ALLOCATION_EVENT, null, null, null, null, null, null)));
            String cpu = linkOf(conforming("flamegraph",
                    tools().flamegraph(BASELINE_ID, CPU_EVENT, null, null, null, null, null, null)));

            assertTrue(allocation.contains("useWeight=true"), allocation);
            assertFalse(cpu.contains("useWeight"), cpu);
        }

        @Test
        void carriesTheWindowOnThePrimarysClock() {
            JsonNode out = conforming("flamegraph", tools().flamegraph(BASELINE_ID, CPU_EVENT, null,
                    PRIMARY_START_MS + 10_000, PRIMARY_START_MS + 20_000, null, null, null));

            assertTrue(linkOf(out).contains("startEpochMs=" + (PRIMARY_START_MS + 10_000)), linkOf(out));
            assertTrue(linkOf(out).contains("endEpochMs=" + (PRIMARY_START_MS + 20_000)), linkOf(out));
            assertTrue(out.get("uiLinkNote").isNull(), out.toString());
        }

        /** Without a window the view would open on its first hour; the link names the whole primary. */
        @Test
        void carriesTheWholePrimaryWhenNoWindowWasGiven() {
            String uiLink = linkOf(conforming("movements",
                    tools().movements(BASELINE_ID, CPU_EVENT, null, null, null, null, null, null)));

            assertTrue(uiLink.contains("startEpochMs=" + PRIMARY_START_MS), uiLink);
            assertTrue(uiLink.contains("endEpochMs=" + (PRIMARY_START_MS + 60_000)), uiLink);
        }

        /**
         * The page applies the primary's window to both recordings, so a baseline this answer read on
         * past it cannot be shown; the note says how much of it the page leaves out.
         */
        @Test
        void saysWhenTheBaselineWasReadPastThePrimarysWindow() {
            baselineLasting(2 * ONE_MINUTE_SECONDS);

            JsonNode out = conforming("flamegraph", tools().flamegraph(BASELINE_ID, CPU_EVENT, null,
                    PRIMARY_START_MS + 10_000, null, null, null, null));

            String note = out.get("uiLinkNote").asString();
            assertTrue(note.contains("50000 ms"), note);
            assertTrue(note.contains("110000 ms"), note);
        }

        @Test
        void saysSoForTheWholeOfALongerBaselineToo() {
            baselineLasting(2 * ONE_MINUTE_SECONDS);

            JsonNode out = conforming("flamegraph",
                    tools().flamegraph(BASELINE_ID, CPU_EVENT, null, null, null, null, null, null));

            assertTrue(out.get("uiLinkNote").asString().contains("120000 ms"), out.toString());
        }

        /** A shorter baseline ends inside the primary's window on the page as it did here. */
        @Test
        void staysQuietWhenTheBaselineEndsInsideThePrimarysWindow() {
            baselineLasting(TWENTY_SECONDS);

            JsonNode out = conforming("flamegraph", tools().flamegraph(BASELINE_ID, CPU_EVENT, null,
                    PRIMARY_START_MS + 10_000, null, null, null, null));

            assertTrue(out.get("uiLinkNote").isNull(), out.toString());
        }

        /** The page draws the graph the ranking was read from, and the note says so. */
        @Test
        void aRankingSaysThePageIsItsGraph() {
            JsonNode out = conforming("movements",
                    tools().movements(BASELINE_ID, CPU_EVENT, null, null, null, null, null, null));

            assertTrue(out.get("uiLinkNote").asString().contains("ranking"), out.toString());
            assertFalse(out.get("uiLinkNote").asString().contains(BASELINE_ID + " there"), out.toString());
        }
    }

    @Nested
    class ListComparability {

        @Test
        void namesBothSidesAndWhatTheyHaveInCommon() {
            when(diffManager.eventSummaries())
                    .thenReturn(List.of(shared(CPU_EVENT, 4_200, 3_900, 0)));

            String out = tools().list(BASELINE_ID).text();

            assertTrue(out.contains("\"profileId\":\"" + PRIMARY_ID + "\""), out);
            assertTrue(out.contains("\"profileId\":\"" + BASELINE_ID + "\""), out);
            assertTrue(out.contains("\"eventType\":\"" + CPU_EVENT + "\""), out);
            assertTrue(out.contains("\"primarySamples\":4200"), out);
            assertTrue(out.contains("\"baselineSamples\":3900"), out);
        }

        /** Each side's own span, on the clock the window inputs take, and no ISO duration beside it. */
        @Test
        void givesEachSidesSpanInEpochMilliseconds() {
            when(diffManager.eventSummaries())
                    .thenReturn(List.of(shared(CPU_EVENT, 4_200, 3_900, 0)));

            JsonNode out = conforming("list", tools().list(BASELINE_ID));

            assertEquals("OK", out.get("status").asString());
            assertEquals(PRIMARY_START_MS, out.get("primary").get("startedAtEpochMs").asLong());
            assertEquals(PRIMARY_START_MS + 60_000, out.get("primary").get("finishedAtEpochMs").asLong());
            assertEquals(BASELINE_START_MS, out.get("baseline").get("startedAtEpochMs").asLong());
            assertEquals(60_000, out.get("baseline").get("durationMs").asLong());
            assertFalse(out.get("primary").has("duration"), out.toString());
        }

        /** The pair's first comparable type, ranked, and the evidence check that qualifies it. */
        @Test
        void namesTheRankingOfTheFirstComparableTypeAndTheQualityCheck() {
            when(diffManager.eventSummaries())
                    .thenReturn(List.of(shared(CPU_EVENT, 4_200, 3_900, 0)));

            JsonNode out = conforming("list", tools().list(BASELINE_ID));

            assertEquals(List.of("compare_quality", "compare_movements"), nextTools(out));
            JsonNode movements = out.get("followUp").get("nextTools").get(1).get("arguments");
            assertEquals(PRIMARY_ID, movements.get("profileId").asString());
            assertEquals(BASELINE_ID, movements.get("baselineProfileId").asString());
            assertEquals(CPU_EVENT, movements.get("eventType").asString());
            assertEquals(2, McpNextToolConformance.assertFollowable(out, reachable()));
        }

        /** The link names the baseline, so the differential grid opens on this very pair. */
        @Test
        void linksTheDifferentialGridOfThisPair() {
            JsonNode out = conforming("list", tools().list(BASELINE_ID));

            assertTrue(out.get("uiLink").asString().endsWith(
                    "/profiles/" + PRIMARY_ID + "/flamegraphs/differential?baseline=" + BASELINE_ID), out.toString());
            assertFalse(out.has("uiLinkNote"), out.toString());
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

            String out = conforming("list", tools().list(BASELINE_ID)).toString();

            assertTrue(out.contains("\"weightUnit\":\"bytes\""), out);
            assertTrue(out.contains("\"primaryWeight\":9000000"), out);
            assertTrue(out.contains("\"primaryWeight\":null"), out);
        }

        /** Nothing to compare is a status with a reason, not a sentence in front of the record. */
        @Test
        void saysSoWhenTheTwoProfilesShareNothingComparable() {
            JsonNode out = conforming("list", tools().list(BASELINE_ID));

            assertEquals("EMPTY", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("no event type in common"), out.toString());
            assertEquals(List.of("profiles_features", "profiles_features"), nextTools(out));
            assertEquals(PRIMARY_ID,
                    out.get("followUp").get("nextTools").get(0).get("arguments").get("profileId").asString());
            assertEquals(BASELINE_ID,
                    out.get("followUp").get("nextTools").get(1).get("arguments").get("profileId").asString());
            assertEquals(2, McpNextToolConformance.assertFollowable(out, reachable()));
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

            String out = tools().list(BASELINE_ID).text();

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

            assertTrue(tools().list(BASELINE_ID).text().contains("noticeably different length"));
        }

        /**
         * {@code ProfileInfo.duration()} dereferences both timestamps, so a recording without them
         * used to take the whole listing down with a NullPointerException. The length is unknown -
         * null, not zero - which is a note, not a crash.
         */
        @Test
        void survivesARecordingWithoutTimestamps() {
            when(diffManager.eventSummaries())
                    .thenReturn(List.of(shared(CPU_EVENT, 4_200, 3_900, 0)));
            when(baselineManager.info()).thenReturn(infoWithoutTimestamps(BASELINE_ID, BASELINE_NAME));

            JsonNode out = conforming("list", tools().list(BASELINE_ID));

            assertEquals(BASELINE_ID, out.get("baseline").get("profileId").asString());
            assertTrue(out.get("baseline").get("durationMs").isNull(), out.toString());
            assertTrue(out.get("baseline").get("startedAtEpochMs").isNull(), out.toString());
            assertTrue(out.toString().contains("noticeably different length"), out.toString());
        }

        /**
         * The differential tools compare a handful of types. One both runs recorded that is not
         * among them was reported as exclusive to each side at once, with a note asserting a
         * profiler-configuration difference that did not exist.
         */
        @Test
        void doesNotReportATypeRecordedByBothSidesAsExclusiveToEither() {
            when(diffManager.eventSummaries())
                    .thenReturn(List.of(shared(CPU_EVENT, 4_200, 3_900, 0)));
            when(primaryFlamegraphManager.eventSummaries()).thenReturn(List.of(
                    exclusive(CPU_EVENT, 4_200), exclusive(ONLY_IN_BASELINE_EVENT, 71)));
            when(baselineFlamegraphManager.eventSummaries()).thenReturn(List.of(
                    exclusive(CPU_EVENT, 3_900), exclusive(ONLY_IN_BASELINE_EVENT, 12)));

            String out = tools().list(BASELINE_ID).text();

            assertTrue(out.contains("\"onlyInPrimary\":[]"), out);
            assertTrue(out.contains("\"onlyInBaseline\":[]"), out);
            assertTrue(out.contains("\"recordedByBothNotComparable\":[\"" + ONLY_IN_BASELINE_EVENT + "\"]"), out);
            assertFalse(out.contains("profiler-configuration difference"), out);
            assertTrue(out.contains("neither a configuration difference nor a change"), out);
        }

        @Test
        void staysQuietAboutLengthWhenTheRecordingsAreComparable() {
            when(diffManager.eventSummaries())
                    .thenReturn(List.of(shared(CPU_EVENT, 4_200, 3_900, 0)));

            assertFalse(tools().list(BASELINE_ID).text().contains("noticeably different length"));
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

            assertTrue(tools().list("  " + BASELINE_ID + "  ").text().contains(BASELINE_ID));
        }
    }

    @Nested
    class Movements {

        /** The ranking stays the Markdown the agent reads; the record beside it names what it ranked. */
        @Test
        void ranksTheMovementsAndSaysWhereToDrillIn() {
            McpToolResult result = tools().movements(
                    BASELINE_ID, CPU_EVENT, null, null, null, null, null, null);
            JsonNode out = conforming("movements", result);

            assertTrue(result.text().startsWith(MOVEMENTS_MARKDOWN), result.text());
            MarkdownFooters.assertRenderedFrom(result.text(), out);
            assertTrue(result.text().contains("/profiles/" + PRIMARY_ID + "/flamegraph-view?"), result.text());
            assertFalse(result.text().contains("Where to go next"), result.text());
            assertEquals(PRIMARY_ID, out.get("profileId").asString());
            assertEquals(BASELINE_ID, out.get("baselineProfileId").asString());
            assertEquals(CPU_EVENT, out.get("eventType").asString());
            assertEquals(DEFAULT_MOVEMENT_LIMIT, out.get("limit").asInt());
            assertEquals(MOVEMENTS_MARKDOWN.length(), out.get("markdownChars").asInt());
            assertEquals(List.of("compare_quality", "compare_flamegraph"), nextTools(out));
            assertEquals(1, out.get("followUp").get("guidance").size());
            assertEquals(2, McpNextToolConformance.assertFollowable(out, reachable()));
        }

        /** The drill-down is the same comparison, window and filters, as a call tree. */
        @Test
        void theDrillDownKeepsTheWindowAndTheFilters() {
            JsonNode out = conforming("movements", tools().movements(BASELINE_ID, CPU_EVENT, null,
                    PRIMARY_START_MS + 10_000, PRIMARY_START_MS + 20_000, true, true, null));

            JsonNode drill = out.get("followUp").get("nextTools").get(1).get("arguments");
            assertEquals(CPU_EVENT, drill.get("eventType").asString());
            assertEquals(PRIMARY_START_MS + 10_000, drill.get("startEpochMs").asLong());
            assertEquals(PRIMARY_START_MS + 20_000, drill.get("endEpochMs").asLong());
            assertTrue(drill.get("useWeight").asBoolean());
            assertTrue(drill.get("excludeIdle").asBoolean());
            assertFalse(drill.has("excludeNonJava"), drill.toString());
        }

        @Test
        void fallsBackToTheDefaultLimitWhenNoneIsAsked() {
            tools().movements(BASELINE_ID, CPU_EVENT, null, null, null, null, null, null);

            verify(diffManager).rankedMovements(any(), eq(DEFAULT_MOVEMENT_LIMIT));
        }

        /**
         * The cap may be lowered but not raised: a model asking for everything is asking for its own
         * context to be spent on one ranking.
         */
        @Test
        void capsALimitTheCallerAskedToRaise() {
            JsonNode out = conforming("movements",
                    tools().movements(BASELINE_ID, CPU_EVENT, 5_000, null, null, null, null, null));

            verify(diffManager).rankedMovements(any(), eq(MAX_MOVEMENT_LIMIT));
            assertEquals(MAX_MOVEMENT_LIMIT, out.get("limit").asInt());
        }

        /**
         * Thread names differ between two runs - {@code pool-1-thread-7} is not the same worker
         * twice - so a per-thread tree would report every branch as appeared or vanished. A
         * comparison is always thread-aggregated.
         */
        @Test
        void comparesThreadAggregatedWhateverTheFiltersSay() {
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
    }

    @Nested
    class DifferentialFlamegraph {

        @Test
        void exportsTheMergedTreeAndSaysWhatItsAbsencesMean() {
            McpToolResult result = tools().flamegraph(
                    BASELINE_ID, CPU_EVENT, null, null, null, null, null, null);
            JsonNode out = conforming("flamegraph", result);

            assertTrue(result.text().startsWith(DIFF_EXPORT_MARKDOWN), result.text());
            MarkdownFooters.assertRenderedFrom(result.text(), out);
            assertTrue(result.text().contains("did not move"), "short guidance travels in the footer");
            assertTrue(out.get("followUp").get("guidance").toString().contains("did not move"), out.toString());
            assertTrue(out.get("uiLink").asString().contains("/profiles/" + PRIMARY_ID + "/flamegraph-view?"),
                    out.toString());
            assertTrue(out.get("uiLinkNote").isNull(), "the page draws exactly this comparison");
            assertFalse(result.text().contains("Link note:"), result.text());
            assertTrue(out.get("thresholdPct").isNull());
            assertEquals(DIFF_EXPORT_MARKDOWN.length(), out.get("markdownChars").asInt());
        }

        /**
         * What a frame costs on its own is each side's plain flamegraph, over the same window on that
         * side's clock.
         */
        @Test
        void namesEachSidesOwnFlamegraphOverItsOwnWindow() {
            JsonNode out = conforming("flamegraph", tools().flamegraph(BASELINE_ID, CPU_EVENT, 5.0,
                    PRIMARY_START_MS + 10_000, PRIMARY_START_MS + 20_000, null, null, null));

            assertEquals(List.of("compare_quality", "flamegraph_export", "flamegraph_export"), nextTools(out));
            JsonNode primary = out.get("followUp").get("nextTools").get(1).get("arguments");
            assertEquals(PRIMARY_ID, primary.get("profileId").asString());
            assertEquals(PRIMARY_START_MS + 10_000, primary.get("startEpochMs").asLong());
            JsonNode baseline = out.get("followUp").get("nextTools").get(2).get("arguments");
            assertEquals(BASELINE_ID, baseline.get("profileId").asString());
            assertEquals(BASELINE_START_MS + 10_000, baseline.get("startEpochMs").asLong());
            assertEquals(BASELINE_START_MS + 20_000, baseline.get("endEpochMs").asLong());
            assertEquals(5.0, out.get("thresholdPct").asDouble());
            assertEquals(3, McpNextToolConformance.assertFollowable(out, reachable()));
        }

        @Test
        void aCallToAWithheldFamilyIsLeftOut() {
            JsonNode out = tools(new AdvertisedFamilies(Set.of(AdvertisedFamilies.COMPARE)))
                    .flamegraph(BASELINE_ID, CPU_EVENT, null, null, null, null, null, null)
                    .structuredContent();

            assertEquals(List.of("compare_quality"), nextTools(out));
        }

        @Test
        void refusesAThresholdOutsideTheOpenZeroToHundredRange() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().flamegraph(
                            BASELINE_ID, CPU_EVENT, 0.0, null, null, null, null, null));

            assertTrue(thrown.getMessage().contains("thresholdPct"), thrown.getMessage());
        }

        /** The same open range the call enforces, advertised as JSON Schema 2020-12 spells it. */
        @Test
        void advertisesTheThresholdRangeAsOpenAtBothEnds() {
            JsonNode threshold = new ReflectiveToolset(tools(), "compare").specs().stream()
                    .filter(candidate -> candidate.name().equals("compare_flamegraph"))
                    .findFirst()
                    .orElseThrow()
                    .inputSchema().path("properties").path("thresholdPct");

            assertEquals(0.0, threshold.path("exclusiveMinimum").asDouble(), threshold.toString());
            assertEquals(100.0, threshold.path("exclusiveMaximum").asDouble(), threshold.toString());
            assertFalse(threshold.has("minimum"), threshold.toString());
            assertFalse(threshold.has("maximum"), threshold.toString());
        }
    }
}
