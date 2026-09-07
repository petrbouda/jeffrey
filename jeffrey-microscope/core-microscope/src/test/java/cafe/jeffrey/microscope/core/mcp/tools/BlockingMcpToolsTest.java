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

import cafe.jeffrey.profile.manager.BlockingManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.blocking.BlockingOverview;
import cafe.jeffrey.profile.manager.model.blocking.ContentionStat;
import cafe.jeffrey.profile.manager.model.blocking.MonitorWaitStat;
import cafe.jeffrey.profile.manager.model.blocking.PinnedThreadEntry;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        return new BlockingMcpTools(profileManager);
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

    @Nested
    class Overview {

        @Test
        void carriesTheHeadlineCountsAndTheLinkToTheBlockingPage() {
            when(blockingManager.overview()).thenReturn(overview(0));

            String out = tools().overview();

            assertTrue(out.contains("\"contendedMonitorCount\":3"), out);
            assertTrue(out.contains("\"waitCount\":12"), out);
            assertTrue(out.contains("\"parkCount\":8"), out);
            assertTrue(out.contains(BLOCKING_VIEW_LINK), out);
        }

        @Test
        void saysThereIsNoBlockingDataWhenNoBlockingEventTypeWasRecorded() {
            when(blockingManager.overview()).thenReturn(nothingRecorded());

            String out = tools().overview();

            assertTrue(out.contains("recorded no blocking events"), out);
            assertFalse(out.contains("contendedMonitorCount"), out);
        }

        /**
         * The refusal turns on the presence flags rather than the counts. A recording that captured
         * the events and saw nothing block has been measured, and reporting it as "no data" would
         * hide the one finding it actually carries.
         */
        @Test
        void reportsAMeasuredZeroRatherThanCallingItNoData() {
            when(blockingManager.overview()).thenReturn(new BlockingOverview(
                    0, 0, 0, 0, 0, 0, true, true, false, false, false));

            String out = tools().overview();

            assertTrue(out.contains("\"contendedMonitorCount\":0"), out);
            assertFalse(out.contains("recorded no blocking events"), out);
        }

        /**
         * Pinning is routed to only when it happened: a pointer that appeared on every profile would
         * be noise on the great majority that use no virtual threads at all.
         */
        @Test
        void namesThePinningTrailOnlyWhenSomethingWasPinned() {
            when(blockingManager.overview()).thenReturn(overview(0));
            assertFalse(tools().overview().contains("blocking_pinnedThreads"));

            when(blockingManager.overview()).thenReturn(overview(4));
            assertTrue(tools().overview().contains("blocking_pinnedThreads"));
        }
    }

    @Nested
    class Monitors {

        @Test
        void namesTheContendedLockAndTheMonitorWaitedOn() {
            when(blockingManager.monitorContention()).thenReturn(List.of(contention(MONITOR_CLASS)));
            when(blockingManager.monitorWaits()).thenReturn(List.of(monitorWait(WAIT_CLASS)));

            String out = tools().monitors();

            assertTrue(out.contains(MONITOR_CLASS), out);
            assertTrue(out.contains(WAIT_CLASS), out);
            assertTrue(out.contains("\"timedOutCount\":1"), out);
            assertTrue(out.contains(BLOCKING_VIEW_LINK), out);
        }

        @Test
        void saysThereIsNoPerMonitorDataWhenNeitherEventTypeWasRecorded() {
            when(blockingManager.monitorContention()).thenReturn(List.of());
            when(blockingManager.monitorWaits()).thenReturn(List.of());

            String out = tools().monitors();

            assertTrue(out.contains("no jdk.JavaMonitorEnter or jdk.JavaMonitorWait events"), out);
            assertFalse(out.contains("\"contention\""), out);
        }

        /**
         * Monitor classes are unbounded in principle - a lock per cache entry produces a row per
         * entry - so the head of the ranking is rendered and the tail is left out.
         */
        @Test
        void rendersOnlyTheHeadOfALongContentionRanking() {
            when(blockingManager.monitorContention()).thenReturn(manyMonitors());
            when(blockingManager.monitorWaits()).thenReturn(List.of());

            String out = tools().monitors();

            assertTrue(out.contains("\"className\":\"Lock-39\""), out);
            assertFalse(out.contains("\"className\":\"Lock-40\""), out);
        }
    }

    @Nested
    class PinnedThreads {

        @Test
        void namesEachPinnedThreadAndHowLongItStayedPinned() {
            when(blockingManager.pinnedThreads())
                    .thenReturn(List.of(new PinnedThreadEntry(PINNED_THREAD, 250_000_000L)));

            String out = tools().pinnedThreads();

            assertTrue(out.contains(PINNED_THREAD), out);
            assertTrue(out.contains("\"durationNanos\":250000000"), out);
            assertTrue(out.contains(VIRTUAL_THREADS_VIEW_LINK), out);
        }

        /**
         * "No virtual threads" and "no virtual thread ever pinned" are different findings and the
         * counts cannot separate them, so the answer points at the tool whose flags can.
         */
        @Test
        void sendsAnEmptyPinningListToTheOverviewToTellTheTwoAbsencesApart() {
            when(blockingManager.pinnedThreads()).thenReturn(List.of());

            String out = tools().pinnedThreads();

            assertTrue(out.contains("no jdk.VirtualThreadPinned events"), out);
            assertTrue(out.contains("blocking_overview"), out);
        }
    }
}
