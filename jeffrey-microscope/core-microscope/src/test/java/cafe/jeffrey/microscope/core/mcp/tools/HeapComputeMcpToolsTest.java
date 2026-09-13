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

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import cafe.jeffrey.shared.common.Json;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HeapComputeMcpToolsTest {

    private static final String PROFILE_ID = "profile-1";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    ProfileManager profileManager;

    @Mock
    HeapDumpManager heapDumpManager;

    private HeapDumpInitService initService;

    @BeforeEach
    void setUp() {
        initService = new HeapDumpInitService(CLOCK);
        when(profileManager.info()).thenReturn(profileInfo());
        when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
        // UiLinks builds from the current servlet request, so the tools need one bound.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private HeapComputeMcpTools tools() {
        return new HeapComputeMcpTools(profileManager, initService);
    }

    @Test
    void preparationExposesAnAttemptIdentity() {
        when(heapDumpManager.heapDumpExists()).thenReturn(true);
        String result = tools().prepare("leaks");
        assertTrue(result.contains("\"operationId\""), result);
        assertTrue(result.contains("\"operation\""), result);
    }

    @Test
    void cancelledPreparationRetainsItsLeaseAndReportsTheWorkActuallyJoined() throws Exception {
        when(heapDumpManager.heapDumpExists()).thenReturn(true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger leases = new AtomicInteger();
        McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
        HeapComputeMcpTools tools = new HeapComputeMcpTools(profileManager, initService, () -> {
            leases.incrementAndGet();
            return leases::decrementAndGet;
        }, operations);
        when(heapDumpManager.initialize(eq(null), any())).thenAnswer(invocation -> {
            entered.countDown();
            while (release.getCount() != 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    // A native index operation may finish its current work before observing cancellation.
                }
            }
            return null;
        });
        String firstId = Json.mapper().readTree(tools.prepare("leaks", false)).path("operationId").asString();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals("cancel_requested", operations.cancel(firstId, kind -> true).status());
            var joined = Json.mapper().readTree(tools.prepare("dominator", true));
            assertEquals(firstId, joined.path("operationId").asString());
            assertEquals("leaks", joined.path("computing").get(0).asString());
            assertEquals(1, leases.get());
            assertFalse(operations.status(firstId).retryable());
        } finally {
            release.countDown();
        }
        await().atMost(5, TimeUnit.SECONDS).until(() -> leases.get() == 0);
        await().atMost(5, TimeUnit.SECONDS).until(() -> operations.status(firstId).status().equals("cancelled"));
        String retained = Json.mapper().readTree(tools.prepare("leaks", false)).path("operationId").asString();
        assertEquals(firstId, retained);
        String retry = Json.mapper().readTree(tools.prepare("leaks", true)).path("operationId").asString();
        assertFalse(firstId.equals(retry));
        assertEquals("cancelled", operations.cancel(firstId, kind -> true).status());
    }

    @Test
    void heapHistoryRemainsReadableAfterOperationRetentionExpires() {
        MutableClock clock = new MutableClock();
        HeapDumpInitService service = new HeapDumpInitService(clock);
        McpOperationRegistry operations = new McpOperationRegistry(clock);
        HeapComputeMcpTools tools = new HeapComputeMcpTools(profileManager, service, () -> () -> {}, operations);
        when(heapDumpManager.heapDumpExists()).thenReturn(true);
        when(heapDumpManager.initialize(eq(null), any())).thenThrow(new IllegalStateException("index failed"));
        String operationId = Json.mapper().readTree(tools.prepare("leaks", false)).path("operationId").asString();
        await().atMost(5, TimeUnit.SECONDS).until(() -> operations.status(operationId).finishedAt() != null);
        clock.now = clock.now.plusSeconds(7200);
        String history = assertDoesNotThrow(tools::status);
        assertTrue(history.contains("index failed"), history);
        String retained = assertDoesNotThrow(() -> tools.prepare("leaks", false));
        assertTrue(retained.contains("retry=true"), retained);
        verify(heapDumpManager, times(1)).initialize(eq(null), any());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-12T12:00:00Z");
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
        @Override
        public Instant instant() {
            return now;
        }
    }

    @Nested
    class Prepare {

        /**
         * A JFR recording asked to prepare a heap dump must be told it asked the wrong family, rather
         * than starting a run that fails on its first stage and reads like a broken dump.
         */
        @Test
        void refusesAProfileWithNoHeapDump() {
            when(heapDumpManager.heapDumpExists()).thenReturn(false);

            IllegalArgumentException e =
                    assertThrows(IllegalArgumentException.class, () -> tools().prepare(null));

            assertTrue(e.getMessage().contains("no heap dump"));
            assertTrue(e.getMessage().contains("profiles_features"));
        }

        @Test
        void refusesAnUnknownReportNamingTheOnesItHas() {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> tools().prepare("nonsense"));

            assertTrue(e.getMessage().contains("Unknown report"));
            assertTrue(e.getMessage().contains("leaks"));
        }

        /**
         * Returning immediately is the point: a dominator build over a large heap runs for minutes,
         * well past the point where a client abandons the call.
         */
        @Test
        void returnsTheStageListWithoutWaitingForTheWork() {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);

            String result = tools().prepare(null);

            assertTrue(result.contains("\"stages\""));
            assertTrue(result.contains("dominator"));
            assertTrue(result.contains("heap_status"));
        }
    }

    @Test
    void holdsABackgroundLeaseUntilPreparationFinishesAndReleasesJoinedCalls() throws Exception {
        when(heapDumpManager.heapDumpExists()).thenReturn(true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        when(heapDumpManager.initialize(eq(null), any())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(finish.await(5, TimeUnit.SECONDS));
            return null;
        });
        HeapComputeMcpTools tools = new HeapComputeMcpTools(profileManager, initService, () -> {
            active.incrementAndGet();
            return () -> active.decrementAndGet();
        });
        try {
            tools.prepare(null);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(1, active.get());
            tools.prepare(null);
            assertEquals(1, active.get(), "a joined call must release its unused background lease");
        } finally {
            finish.countDown();
        }
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(0, active.get()));
    }

    @Test
    void releasesABackgroundLeaseWhenTheReportIsRejected() {
        when(heapDumpManager.heapDumpExists()).thenReturn(true);
        AtomicInteger active = new AtomicInteger();
        HeapComputeMcpTools tools = new HeapComputeMcpTools(profileManager, initService, () -> {
            active.incrementAndGet();
            return () -> active.decrementAndGet();
        });
        assertThrows(IllegalArgumentException.class, () -> tools.prepare("unknown"));
        assertEquals(0, active.get());
    }

    /**
     * A cleanup path must not become the failure it was cleaning up after. The background run calls
     * the release from the {@code finally} that follows storing its result, so a release that threw
     * would replace whatever that storage threw.
     */
    @Test
    void reportsRatherThanThrowsWhenABackgroundLeaseWillNotRelease() {
        when(heapDumpManager.heapDumpExists()).thenReturn(true);
        HeapComputeMcpTools tools = new HeapComputeMcpTools(profileManager, initService, () -> () -> {
            throw new IllegalStateException("pool already closed");
        });

        // The report is rejected, so prepare() releases the lease it never handed over.
        IllegalArgumentException rejected =
                assertThrows(IllegalArgumentException.class, () -> tools.prepare("unknown"));

        assertTrue(rejected.getMessage().contains("unknown"),
                "the caller must still be told what it got wrong: " + rejected.getMessage());
    }

    @Nested
    class Status {

        @Test
        void reportsAnIdleProfileRatherThanFailing() {
            String result = tools().status();

            assertTrue(result.contains("\"running\":false"));
            assertTrue(result.contains("\"stages\""));
        }
    }

    private static ProfileInfo profileInfo() {
        return new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Heap dump", RecordingEventSource.HEAP_DUMP,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1");
    }
}
