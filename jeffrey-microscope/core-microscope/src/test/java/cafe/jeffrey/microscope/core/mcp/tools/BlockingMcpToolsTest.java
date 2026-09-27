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

import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.manager.BlockingManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.blocking.BlockingOverview;
import cafe.jeffrey.profile.manager.model.blocking.ContentionStat;
import cafe.jeffrey.profile.manager.model.blocking.MonitorWaitStat;
import cafe.jeffrey.profile.manager.model.blocking.PinnedThreadEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlockingMcpToolsTest {

    private static final String PROFILE_ID = "p-1";
    private static final String BLOCKING_VIEW_LINK = "/profiles/p-1/blocking-operations";
    private static final String VIRTUAL_THREADS_VIEW_LINK = "/profiles/p-1/virtual-threads";
    private static final String MONITOR_CLASS = "java.util.concurrent.locks.ReentrantLock";
    private static final String WAIT_CLASS = "java.lang.Object";
    private static final String PINNED_THREAD = "virtual-worker-7";

    /** Above the tool's own row cap, so the head of a long list can be told from the whole of it. */
    private static final int MORE_ROWS_THAN_THE_CAP = 45;

    @Mock
    ProfileManager profileManager;

    @Mock
    BlockingManager blockingManager;

    /**
     * Every answer carries a link into the UI, and {@code UiLinks} reads the request bound to the
     * current thread to build it.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        when(profileManager.blockingManager()).thenReturn(blockingManager);
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private BlockingMcpTools tools() {
        return new BlockingMcpTools(profileManager, EVERY_FAMILY);
    }

    private static BlockingOverview overview(long pinnedCount) {
        return new BlockingOverview(
                3, 1_500_000L, 12, 8, 2, pinnedCount,
                true, true, true, true, pinnedCount > 0);
    }

    private static BlockingOverview nothingRecorded() {
        return new BlockingOverview(0, 0, 0, 0, 0, 0, false, false, false, false, false);
    }

    private static ContentionStat contention(String className) {
        return new ContentionStat(className, 42, 1_200_000L, 400_000L, 5);
    }

    private static MonitorWaitStat monitorWait(String className) {
        return new MonitorWaitStat(className, 9, 900_000L, 300_000L, 2, 1);
    }

    private static List<ContentionStat> manyMonitors() {
        List<ContentionStat> stats = new ArrayList<>();
        for (int i = 0; i < MORE_ROWS_THAN_THE_CAP; i++) {
            stats.add(contention("Lock-" + i));
        }
        return stats;
    }

    private static JsonNode answer(String method, McpToolResult result) {
        return StructuredAnswers.json(BlockingMcpTools.class, method, result);
    }

    private static List<PinnedThreadEntry> pinned(int count) {
        List<PinnedThreadEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(new PinnedThreadEntry("virtual-" + i, 1_000_000L - i));
        }
        return entries;
    }

    @Test
    void everyToolDeclaresAnOutputSchema() {
        assertEquals(List.of(), StructuredAnswers.unschematised(BlockingMcpTools.class));
    }

    @Nested
    class Overview {

        @Test
        void carriesTheHeadlineCountsAndTheLinkToTheBlockingPage() {
            when(blockingManager.overview()).thenReturn(overview(0));

            JsonNode out = answer("overview", tools().overview());

            assertEquals("OK", out.get("status").asString());
            assertEquals(3, out.get("overview").get("contendedMonitorCount").asLong());
            assertEquals(12, out.get("overview").get("waitCount").asLong());
            assertEquals(8, out.get("overview").get("parkCount").asLong());
            assertTrue(out.get("uiLink").asString().endsWith(BLOCKING_VIEW_LINK), out.get("uiLink").asString());
        }

        @Test
        void saysThereIsNoBlockingDataAsAStatusWhenNoBlockingEventTypeWasRecorded() {
            when(blockingManager.overview()).thenReturn(nothingRecorded());

            JsonNode out = answer("overview", tools().overview());

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("recorded no blocking events"), out.toString());
            assertTrue(out.get("overview").isNull());
            assertTrue(out.get("uiLink").asString().endsWith(BLOCKING_VIEW_LINK));
        }

        /**
         * The status turns on the presence flags rather than the counts. A recording that captured
         * the events and saw nothing block has been measured, and reporting it as "no data" would
         * hide the one finding it actually carries.
         */
        @Test
        void reportsAMeasuredZeroRatherThanCallingItNoData() {
            when(blockingManager.overview()).thenReturn(new BlockingOverview(
                    0, 0, 0, 0, 0, 0, true, true, false, false, false));

            JsonNode out = answer("overview", tools().overview());

            assertEquals("OK", out.get("status").asString());
            assertEquals(0, out.get("overview").get("contendedMonitorCount").asLong());
        }

        /**
         * Pinning is routed to only when it happened: a call offered on every profile would be noise
         * on the great majority that use no virtual threads at all.
         */
        @Test
        void namesThePinningTrailOnlyWhenSomethingWasPinned() {
            when(blockingManager.overview()).thenReturn(overview(0));
            assertFalse(StructuredAnswers.nextTools(answer("overview", tools().overview()))
                    .contains("blocking_pinnedThreads"));

            when(blockingManager.overview()).thenReturn(overview(4));
            assertTrue(StructuredAnswers.nextTools(answer("overview", tools().overview()))
                    .contains("blocking_pinnedThreads"));
        }

        @Test
        void routesTheCallPathsToTheWeighedMonitorFlamegraph() {
            when(blockingManager.overview()).thenReturn(overview(0));

            JsonNode call = StructuredAnswers.call(answer("overview", tools().overview()), "flamegraph_export");

            assertEquals("jdk.JavaMonitorEnter", call.get("eventType").asString());
            assertTrue(call.get("useWeight").asBoolean());
        }
    }

    @Nested
    class Monitors {

        @Test
        void namesTheContendedLockAndTheMonitorWaitedOn() {
            when(blockingManager.monitorContention()).thenReturn(List.of(contention(MONITOR_CLASS)));
            when(blockingManager.monitorWaits()).thenReturn(List.of(monitorWait(WAIT_CLASS)));

            JsonNode out = answer("monitors", tools().monitors());

            assertEquals(MONITOR_CLASS, out.get("contention").get(0).get("className").asString());
            assertEquals(WAIT_CLASS, out.get("waits").get(0).get("className").asString());
            assertEquals(1, out.get("waits").get(0).get("timedOutCount").asLong());
            assertTrue(out.get("uiLink").asString().endsWith(BLOCKING_VIEW_LINK));
        }

        /**
         * The builders file a monitor whose event named no class under their own unknown-class label,
         * so a class name is never null and the schema promises it.
         */
        @Test
        void aMonitorsClassIsNeverNull() {
            for (String list : List.of("contention", "waits")) {
                assertEquals("string", StructuredAnswers.schemaTypeOf(
                        BlockingMcpTools.class, "monitors", list, "className").asString(), list);
            }
        }

        @Test
        void saysThereIsNoPerMonitorDataWhenNeitherEventTypeWasRecorded() {
            when(blockingManager.monitorContention()).thenReturn(List.of());
            when(blockingManager.monitorWaits()).thenReturn(List.of());

            JsonNode out = answer("monitors", tools().monitors());

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("no jdk.JavaMonitorEnter or jdk.JavaMonitorWait events"),
                    out.toString());
            assertEquals(List.of("blocking_overview"), StructuredAnswers.nextTools(out));
            assertTrue(out.get("omittedContention").isNull());
        }

        /**
         * Monitor classes are unbounded in principle - a lock per cache entry produces a row per
         * entry - so the head of the ranking is kept and the tail is counted.
         */
        @Test
        void keepsTheHeadOfALongContentionRankingAndCountsTheRest() {
            when(blockingManager.monitorContention()).thenReturn(manyMonitors());
            when(blockingManager.monitorWaits()).thenReturn(List.of());

            JsonNode out = answer("monitors", tools().monitors());

            assertEquals(40, out.get("contention").size());
            assertEquals("Lock-39", out.get("contention").get(39).get("className").asString());
            assertEquals(MORE_ROWS_THAN_THE_CAP - 40, out.get("omittedContention").asInt());
            assertEquals(0, out.get("omittedWaits").asInt());
        }
    }

    @Nested
    class PinnedThreads {

        @Test
        void namesEachPinnedThreadAndHowLongItStayedPinned() {
            when(blockingManager.pinnedThreads())
                    .thenReturn(List.of(new PinnedThreadEntry(PINNED_THREAD, 250_000_000L)));

            JsonNode out = answer("pinnedThreads", tools().pinnedThreads());

            assertEquals(PINNED_THREAD, out.get("pinnedThreads").get(0).get("thread").asString());
            assertEquals(250_000_000L, out.get("pinnedThreads").get(0).get("durationNanos").asLong());
            assertEquals(0, out.get("omittedPinnedThreads").asInt());
            assertTrue(out.get("uiLink").asString().endsWith(VIRTUAL_THREADS_VIEW_LINK));
        }

        @Test
        void aPinWhoseThreadWasNotNamedConformsAsNull() {
            when(blockingManager.pinnedThreads()).thenReturn(List.of(new PinnedThreadEntry(null, 250_000_000L)));

            JsonNode out = answer("pinnedThreads", tools().pinnedThreads());

            assertTrue(out.get("pinnedThreads").get(0).get("thread").isNull());
        }

        /** The manager keeps the longest pins up to its own cap, so a list that reached it cannot count the rest. */
        @Test
        void cannotCountWhatTheManagersCapLeftOut() {
            when(blockingManager.pinnedThreads()).thenReturn(pinned(BlockingManager.PINNED_THREADS_KEPT));

            JsonNode out = answer("pinnedThreads", tools().pinnedThreads());

            assertEquals(40, out.get("pinnedThreads").size());
            assertTrue(out.get("omittedPinnedThreads").isNull(), out.get("omittedPinnedThreads").toString());
        }

        @Test
        void countsTheCutExactlyBelowTheManagersCap() {
            when(blockingManager.pinnedThreads()).thenReturn(pinned(45));

            assertEquals(5, answer("pinnedThreads", tools().pinnedThreads()).get("omittedPinnedThreads").asInt());
        }

        /**
         * "No virtual threads" and "no virtual thread ever pinned" are different findings and the
         * counts cannot separate them, so the answer routes to the tool whose flags can.
         */
        @Test
        void sendsAnEmptyPinningListToTheOverviewToTellTheTwoAbsencesApart() {
            when(blockingManager.pinnedThreads()).thenReturn(List.of());

            JsonNode out = answer("pinnedThreads", tools().pinnedThreads());

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("no jdk.VirtualThreadPinned events"), out.toString());
            assertEquals(List.of("blocking_overview"), StructuredAnswers.nextTools(out));
        }
    }
}
