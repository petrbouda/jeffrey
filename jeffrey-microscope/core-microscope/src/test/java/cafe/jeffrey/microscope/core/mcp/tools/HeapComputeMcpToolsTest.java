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

import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReport;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskState;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.profile.common.operation.OperationState;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import tools.jackson.databind.JsonNode;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskStatus;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
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

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import cafe.jeffrey.shared.common.Json;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.NO_TASKS;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.SHORT_TASK_WAIT;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.TASKS;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.complete;
import static cafe.jeffrey.microscope.mcp.protocol.McpCallContext.RESOURCE_READ;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
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

    /** The tools over {@code operations}, answering a client that declared tasks after the standard wait. */
    private static HeapComputeMcpTools withStandardAnswers(ProfileManager profileManager,
            HeapDumpInitService initService, Supplier<? extends AutoCloseable> backgroundLease,
            McpOperationRegistry operations) {
        return new HeapComputeMcpTools(
                profileManager, initService, backgroundLease, operations, ToolFixtures.answers(), EVERY_FAMILY);
    }

    private HeapComputeMcpTools tools() {
        return withStandardAnswers(profileManager, initService, () -> () -> {}, new McpOperationRegistry(CLOCK));
    }

    @Test
    void preparationExposesAnAttemptIdentity() {
        when(heapDumpManager.heapDumpExists()).thenReturn(true);
        String result = complete(tools().prepare(HeapReport.LEAKS, true, RESOURCE_READ));
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
        HeapComputeMcpTools tools = withStandardAnswers(profileManager, initService, () -> {
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
        String firstId = Json.mapper().readTree(complete(tools.prepare(HeapReport.LEAKS, false, RESOURCE_READ)))
                .path("operationId").asString();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(OperationState.CANCEL_REQUESTED, operations.cancel(firstId, kind -> true).status());
            var joined = Json.mapper().readTree(complete(tools.prepare(HeapReport.DOMINATOR, true, RESOURCE_READ)));
            assertEquals(firstId, joined.path("operationId").asString());
            assertEquals("LEAKS", joined.path("computing").get(0).asString());
            assertEquals(1, leases.get());
            assertFalse(operations.status(firstId).retryable());
        } finally {
            release.countDown();
        }
        await().atMost(5, TimeUnit.SECONDS).until(() -> leases.get() == 0);
        await().atMost(5, TimeUnit.SECONDS).until(() -> operations.status(firstId).status() == OperationState.CANCELLED);
        String retained = Json.mapper().readTree(complete(tools.prepare(HeapReport.LEAKS, false, RESOURCE_READ)))
                .path("operationId").asString();
        assertEquals(firstId, retained);
        String retry = Json.mapper().readTree(complete(tools.prepare(HeapReport.LEAKS, true, RESOURCE_READ)))
                .path("operationId").asString();
        assertFalse(firstId.equals(retry));
        assertEquals(OperationState.CANCELLED, operations.cancel(firstId, kind -> true).status());
    }

    /**
     * "Omit to inspect it without restarting" has to hold for a preparation that completed, not only
     * for one that failed: the registry used to keep a failed run and replace a completed one, so an
     * inspection call rebuilt a dominator tree that was already there.
     */
    @Test
    void omittingRetryDoesNotRestartACompletedPreparation() throws Exception {
        McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
        HeapComputeMcpTools tools = withStandardAnswers(profileManager, initService, () -> () -> {}, operations);
        when(heapDumpManager.heapDumpExists()).thenReturn(true);
        String first = Json.mapper().readTree(complete(tools.prepare(HeapReport.LEAKS, false, RESOURCE_READ)))
                .path("operationId").asString();
        await().atMost(5, TimeUnit.SECONDS).until(() -> operations.status(first).status() == OperationState.COMPLETED);

        var inspected = Json.mapper().readTree(complete(tools.prepare(HeapReport.LEAKS, null, RESOURCE_READ)));

        assertFalse(inspected.path("started").asBoolean());
        assertEquals(first, inspected.path("operationId").asString());
        assertTrue(inspected.path("followUp").path("guidance").get(0).asString().contains("already completed"),
                inspected.toString());
        assertEquals("heap_getLeakSuspects",
                inspected.path("followUp").path("nextTools").get(0).path("tool").asString(), inspected.toString());
        assertNoRebuildOffered(inspected);
        assertTrue(inspected.path("followUp").path("guidance").toString().contains("retry=true"), inspected.toString());
        verify(heapDumpManager, times(1)).initialize(eq(null), any());

        String retried = Json.mapper().readTree(complete(tools.prepare(HeapReport.LEAKS, true, RESOURCE_READ)))
                .path("operationId").asString();
        assertFalse(first.equals(retried), "an explicit retry is what restarts a finished run");
    }

    /**
     * The registry is keyed by profile, not by report, so "the preparation already completed" must
     * mean the report the caller asked for. Read the other way, one finished report answered for
     * every other one and the reader was told a report was ready that had never been computed.
     */
    @Test
    void aReportTheLastRunNeverComputedStartsItsOwnPreparation() throws Exception {
        McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
        HeapComputeMcpTools tools = withStandardAnswers(profileManager, initService, () -> () -> {}, operations);
        when(heapDumpManager.heapDumpExists()).thenReturn(true);
        String first = Json.mapper().readTree(complete(tools.prepare(HeapReport.LEAKS, false, RESOURCE_READ)))
                .path("operationId").asString();
        await().atMost(5, TimeUnit.SECONDS).until(() -> operations.status(first).status() == OperationState.COMPLETED);

        var other = Json.mapper().readTree(complete(tools.prepare(HeapReport.STRINGS, null, RESOURCE_READ)));

        assertTrue(other.path("started").asBoolean(), other.toString());
        assertFalse(first.equals(other.path("operationId").asString()));
        assertEquals(List.of("STRINGS"), Json.mapper().convertValue(other.path("computing"), List.class),
                other.toString());
    }

    @Test
    void heapHistoryRemainsReadableAfterOperationRetentionExpires() {
        MutableClock clock = new MutableClock();
        HeapDumpInitService service = new HeapDumpInitService(clock);
        McpOperationRegistry operations = new McpOperationRegistry(clock);
        HeapComputeMcpTools tools = withStandardAnswers(profileManager, service, () -> () -> {}, operations);
        when(heapDumpManager.heapDumpExists()).thenReturn(true);
        when(heapDumpManager.initialize(eq(null), any())).thenThrow(new IllegalStateException("index failed"));
        String operationId = Json.mapper().readTree(complete(tools.prepare(HeapReport.LEAKS, false, RESOURCE_READ)))
                .path("operationId").asString();
        await().atMost(5, TimeUnit.SECONDS).until(() -> operations.status(operationId).finishedAtEpochMs() != null);
        clock.now = clock.now.plusSeconds(7200);
        String history = assertDoesNotThrow(() -> tools.status().text());
        assertTrue(history.contains("index failed"), history);
        JsonNode expired = Json.readTree(history);
        assertTrue(expired.path("operationExpired").asBoolean(), history);
        assertTrue(expired.path("operation").isNull(), history);
        assertTrue(expired.path("followUp").path("nextTools").get(0).path("arguments").path("retry").asBoolean(), history);
        String retained = assertDoesNotThrow(() -> complete(tools.prepare(HeapReport.LEAKS, false, RESOURCE_READ)));
        JsonNode retry = Json.readTree(retained).path("followUp").path("nextTools").get(0);
        assertEquals("heap_prepare", retry.path("tool").asString(), retained);
        assertTrue(retry.path("arguments").path("retry").asBoolean(), retained);
        verify(heapDumpManager, times(1)).initialize(eq(null), any());
    }

    /** A success is never offered as a rebuild: a retry call belongs to a failure alone. */
    private static void assertNoRebuildOffered(JsonNode answer) {
        for (JsonNode call : answer.path("followUp").path("nextTools")) {
            assertFalse(call.path("arguments").path("retry").asBoolean(), answer.toString());
        }
    }

    /**
     * A completed run whose operation expired is still a success: its history routes to the reports it
     * built, and nothing offers to build them again.
     */
    @Test
    void aCompletedRunThatExpiredRoutesToItsReportsRatherThanARebuild() {
        MutableClock clock = new MutableClock();
        HeapDumpInitService service = new HeapDumpInitService(clock);
        McpOperationRegistry operations = new McpOperationRegistry(clock);
        HeapComputeMcpTools tools = withStandardAnswers(profileManager, service, () -> () -> {}, operations);
        when(heapDumpManager.heapDumpExists()).thenReturn(true);
        String operationId = Json.readTree(complete(tools.prepare(HeapReport.LEAKS, false, RESOURCE_READ)))
                .path("operationId").asString();
        await().atMost(5, TimeUnit.SECONDS).until(() -> operations.status(operationId).status() == OperationState.COMPLETED);
        clock.now = clock.now.plusSeconds(7200);

        JsonNode status = Json.readTree(tools.status().text());
        JsonNode prepared = Json.readTree(complete(tools.prepare(HeapReport.LEAKS, false, RESOURCE_READ)));

        for (JsonNode answer : List.of(status, prepared)) {
            assertTrue(answer.path("operationExpired").asBoolean(), answer.toString());
            assertNoRebuildOffered(answer);
            assertEquals("heap_getLeakSuspects",
                    answer.path("followUp").path("nextTools").get(0).path("tool").asString(), answer.toString());
            assertTrue(answer.path("followUp").path("guidance").toString().contains("expired"), answer.toString());
        }
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
                    assertThrows(IllegalArgumentException.class,
                            () -> complete(tools().prepare(null, true, RESOURCE_READ)));

            assertTrue(e.getMessage().contains("no heap dump"));
            assertTrue(e.getMessage().contains("profiles_features"));
        }

        @Test
        void refusesAnUnknownReportNamingTheOnesItHas() {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);

            RuntimeException e = assertThrows(RuntimeException.class, () -> new ReflectiveToolset(tools(), "heap")
                    .call("heap_prepare", Json.createObject().put("report", "nonsense")));

            assertTrue(e.getMessage().contains("LEAKS"), e.getMessage());
        }

        /** The report an answer lists is a value the input takes back, in any case. */
        @Test
        void theReportedReportIsAcceptedAsInput() {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            ReflectiveToolset toolset = new ReflectiveToolset(tools(), "heap");

            JsonNode first = toolset.callResult("heap_prepare", Json.createObject().put("report", "leaks"))
                    .structuredContent();
            String report = first.path("computing").get(0).asString();
            JsonNode again = toolset.callResult("heap_prepare", Json.createObject().put("report", report))
                    .structuredContent();

            assertEquals("LEAKS", report);
            assertEquals("LEAKS", again.path("computing").get(0).asString());
        }

        /**
         * Returning immediately is the point: a dominator build over a large heap runs for minutes,
         * well past the point where a client abandons the call.
         */
        @Test
        void returnsTheStageListWithoutWaitingForTheWork() {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);

            String result = complete(tools().prepare(null, true, RESOURCE_READ));

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
        HeapComputeMcpTools tools = withStandardAnswers(profileManager, initService, () -> {
            active.incrementAndGet();
            return () -> active.decrementAndGet();
        }, new McpOperationRegistry(CLOCK));
        try {
            complete(tools.prepare(null, true, RESOURCE_READ));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(1, active.get());
            complete(tools.prepare(null, true, RESOURCE_READ));
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
        HeapComputeMcpTools tools = withStandardAnswers(profileManager, rejecting(), () -> {
            active.incrementAndGet();
            return () -> active.decrementAndGet();
        }, new McpOperationRegistry(CLOCK));
        assertThrows(IllegalArgumentException.class, () -> complete(tools.prepare(HeapReport.LEAKS, true, RESOURCE_READ)));
        assertEquals(0, active.get());
    }

    /** A preparation service that refuses every request, as it refuses a report it does not know. */
    private static HeapDumpInitService rejecting() {
        HeapDumpInitService service = mock(HeapDumpInitService.class);
        when(service.startPreparation(any(), any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Unknown report: unknown"));
        return service;
    }

    /**
     * A cleanup path must not become the failure it was cleaning up after. The background run calls
     * the release from the {@code finally} that follows storing its result, so a release that threw
     * would replace whatever that storage threw.
     */
    @Test
    void reportsRatherThanThrowsWhenABackgroundLeaseWillNotRelease() {
        when(heapDumpManager.heapDumpExists()).thenReturn(true);
        HeapComputeMcpTools tools = withStandardAnswers(profileManager, rejecting(), () -> () -> {
            throw new IllegalStateException("pool already closed");
        }, new McpOperationRegistry(CLOCK));

        // The report is rejected, so prepare() releases the lease it never handed over.
        IllegalArgumentException rejected =
                assertThrows(IllegalArgumentException.class,
                        () -> complete(tools.prepare(HeapReport.LEAKS, true, RESOURCE_READ)));

        assertTrue(rejected.getMessage().contains("unknown"),
                "the caller must still be told what it got wrong: " + rejected.getMessage());
    }

    /**
     * heap_prepare answers at once, so a client that declared the tasks extension is handed the
     * preparation as a task whenever it has not completed -- no wait at all -- and the task answers
     * with the stages once it has.
     */
    @Nested
    class TaskCapableClient {

        private final McpOperationRegistry operations = new McpOperationRegistry(CLOCK);

        private HeapComputeMcpTools tools() {
            return new HeapComputeMcpTools(profileManager, initService, () -> () -> {}, operations,
                    new OperationAnswers(SHORT_TASK_WAIT), EVERY_FAMILY);
        }

        @Test
        void handsBackARunningPreparationAsATaskAtOnce() throws Exception {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            CountDownLatch release = new CountDownLatch(1);
            when(heapDumpManager.initialize(eq(null), any())).thenAnswer(invocation -> {
                assertTrue(release.await(60, TimeUnit.SECONDS));
                return null;
            });
            HeapComputeMcpTools tools = tools();

            McpToolOutcome outcome;
            try {
                outcome = assertTimeout(BoundedJobs.TASK_WAIT_BUDGET.dividedBy(2),
                        () -> tools.prepare(HeapReport.LEAKS, false, TASKS));
            } catch (AssertionError e) {
                release.countDown();
                throw e;
            }

            String taskId = assertInstanceOf(McpToolOutcome.Deferred.class, outcome).taskId();
            assertEquals(OperationKind.HEAP_PREPARE, operations.status(taskId).kind());
            assertEquals(McpTaskStatus.WORKING, operations.task(taskId, kind -> true).status());

            release.countDown();
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(
                    McpTaskStatus.COMPLETED, operations.task(taskId, kind -> true).status()));
            var answer = Json.mapper().readTree(assertInstanceOf(McpTaskState.Completed.class,
                    operations.task(taskId, kind -> true).state()).result().text());
            assertTrue(answer.path("started").asBoolean(), answer.toString());
            assertEquals("LEAKS", answer.path("computing").get(0).asString(), answer.toString());
            assertTrue(answer.path("stages").size() > 0, answer.toString());
            assertEquals(taskId, answer.path("operationId").asString(), answer.toString());
            assertFalse(answer.path("followUp").toString().contains("Until it completes"), answer.toString());
        }

        @Test
        void answersACompletedPreparationDirectly() {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            HeapComputeMcpTools tools = tools();
            String first = Json.mapper().readTree(complete(tools.prepare(HeapReport.LEAKS, false, RESOURCE_READ)))
                    .path("operationId").asString();
            await().atMost(5, TimeUnit.SECONDS).until(() -> operations.status(first).status() == OperationState.COMPLETED);

            McpToolOutcome outcome = tools.prepare(HeapReport.LEAKS, null, TASKS);

            assertEquals(complete(tools.prepare(HeapReport.LEAKS, null, RESOURCE_READ)), assertInstanceOf(McpToolResult.class, outcome).text());
        }

        /**
         * A retained failure has nothing left to follow, so it is answered, with the guidance on how to
         * start again, rather than handed back as a task.
         */
        @Test
        void answersARetainedFailureWithTheRetryGuidance() {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.initialize(eq(null), any())).thenThrow(new IllegalStateException("index failed"));
            HeapComputeMcpTools tools = tools();
            String first = Json.mapper().readTree(complete(tools.prepare(HeapReport.LEAKS, false, RESOURCE_READ)))
                    .path("operationId").asString();
            await().atMost(5, TimeUnit.SECONDS).until(() -> operations.status(first).finishedAtEpochMs() != null);

            McpToolOutcome outcome = tools.prepare(HeapReport.LEAKS, null, TASKS);

            var answer = Json.mapper().readTree(assertInstanceOf(McpToolResult.class, outcome).text());
            assertEquals(first, answer.path("operationId").asString(), answer.toString());
            JsonNode retry = answer.path("followUp").path("nextTools").get(0);
            assertEquals("heap_prepare", retry.path("tool").asString(), answer.toString());
            assertTrue(retry.path("arguments").path("retry").asBoolean(), answer.toString());
            assertEquals("LEAKS", retry.path("arguments").path("report").asString(), answer.toString());
            JsonNode operationRetry = answer.path("operation").path("followUp").path("nextTools").get(0);
            assertEquals(retry, operationRetry, "the operation names the same typed retry");
            conforming("prepare", assertInstanceOf(McpToolResult.class, outcome));
        }

        @Test
        void aClientWithoutTheExtensionGetsTodaysAnswer() throws Exception {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            CountDownLatch release = new CountDownLatch(1);
            when(heapDumpManager.initialize(eq(null), any())).thenAnswer(invocation -> {
                assertTrue(release.await(60, TimeUnit.SECONDS));
                return null;
            });
            try {
                McpToolOutcome outcome = tools().prepare(HeapReport.LEAKS, false, NO_TASKS);

                var answer = Json.mapper().readTree(assertInstanceOf(McpToolResult.class, outcome).text());
                assertTrue(answer.path("started").asBoolean(), answer.toString());
                assertFalse(answer.path("operationId").asString().isBlank(), answer.toString());
            } finally {
                release.countDown();
            }
        }
    }

    @Nested
    class Status {

        @Test
        void reportsAnIdleProfileRatherThanFailing() {
            String result = tools().status().text();

            assertTrue(result.contains("\"running\":false"));
            assertTrue(result.contains("\"stages\""));
        }

        @Test
        void anIdleProfileIsSentToPrepareItsDump() {
            JsonNode out = conforming("status", tools().status());

            assertEquals("IDLE", out.path("state").asString());
            assertEquals(PROFILE_ID, call(out, "heap_prepare").path("profileId").asString());
            assertTrue(out.path("operationId").isNull());
        }

        @Test
        void aCompletedPreparationRoutesToTheReportsItBuilt() {
            McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
            HeapComputeMcpTools tools = withStandardAnswers(profileManager, initService, () -> () -> {}, operations);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            String first = Json.readTree(complete(tools.prepare(null, false, RESOURCE_READ))).path("operationId").asString();
            await().atMost(5, TimeUnit.SECONDS).until(() -> operations.status(first).status() == OperationState.COMPLETED);

            JsonNode out = conforming("status", tools.status());

            assertEquals("COMPLETED", out.path("state").asString());
            assertEquals(first, out.path("operationId").asString());
            assertEquals(Set.of("heap_getHeapSummary", "heap_getLeakSuspects", "heap_getDominatorTreeRoots"),
                    nextTools(out));
        }
    }

    /** Every answer is the tool's record: checked against its schema, its link and its next calls. */
    @Nested
    class StructuredAnswers {

        @Test
        void aStartedPreparationListsItsStagesAndThePollingCall() {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            CountDownLatch release = new CountDownLatch(1);
            when(heapDumpManager.initialize(eq(null), any())).thenAnswer(invocation -> {
                assertTrue(release.await(5, TimeUnit.SECONDS));
                return null;
            });
            try {
                JsonNode out = conforming("prepare", tools().prepare(HeapReport.LEAKS, false, NO_TASKS));

                assertTrue(out.path("started").asBoolean(), out.toString());
                assertEquals("LEAKS", out.path("computing").get(0).asString());
                assertEquals(PROFILE_ID, call(out, "heap_status").path("profileId").asString());
                assertEquals("HEAP_PREPARE", out.path("operation").path("kind").asString());
                assertTrue(out.path("uiLink").asString().endsWith("/heap-dump/overview"), out.toString());
                assertEquals("LEAKS", stage(out, "leaks").path("report").asString(), out.toString());
                assertTrue(stage(out, "load").path("report").isNull(), out.toString());
            } finally {
                release.countDown();
            }
        }

        /** A task that follows a run answers with the tool's own record once the run has finished. */
        @Test
        void aFinishedRunRoutesToTheReportItComputed() {
            McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
            HeapComputeMcpTools tools = withStandardAnswers(profileManager, initService, () -> () -> {}, operations);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            String first = Json.readTree(complete(tools.prepare(HeapReport.BIGGEST, false, RESOURCE_READ)))
                    .path("operationId").asString();
            await().atMost(5, TimeUnit.SECONDS).until(() -> operations.status(first).status() == OperationState.COMPLETED);

            JsonNode out = conforming("prepare", tools.prepare(HeapReport.BIGGEST, null, RESOURCE_READ));

            assertFalse(out.path("started").asBoolean());
            assertEquals("heap_getBiggestObjects", out.path("followUp").path("nextTools").get(0).path("tool").asString());
        }
    }

    /** The answer, checked against the schema the tool advertises, its link and its next calls. */
    private static JsonNode conforming(String method, McpToolOutcome outcome) {
        McpToolResult result = assertInstanceOf(McpToolResult.class, outcome);
        JsonNode structured = result.structuredContent();
        Method tool = Arrays.stream(HeapComputeMcpTools.class.getMethods())
                .filter(candidate -> candidate.getName().equals(method))
                .findFirst()
                .orElseThrow();
        McpSchemaConformance.assertConforms(structured,
                McpSchemaGenerator.schemaOf(tool.getAnnotation(McpOutputSchema.class).value()));
        UiLinkRoutes.assertResolves(structured.get("uiLink").asString());
        assertEquals(Json.toString(structured), result.text(), "the text is the record's own JSON");
        McpNextToolConformance.assertFollowable(structured, HeapDumpMcpToolsTest.reachable());
        return structured;
    }

    private static JsonNode stage(JsonNode structured, String id) {
        for (JsonNode stage : structured.get("stages")) {
            if (stage.get("id").asString().equals(id)) {
                return stage;
            }
        }
        throw new AssertionError("no stage " + id + " in " + structured);
    }

    private static Set<String> nextTools(JsonNode structured) {
        Set<String> tools = new TreeSet<>();
        structured.get("followUp").get("nextTools").forEach(call -> tools.add(call.get("tool").asString()));
        return tools;
    }

    private static JsonNode call(JsonNode structured, String tool) {
        for (JsonNode call : structured.get("followUp").get("nextTools")) {
            if (call.get("tool").asString().equals(tool)) {
                return call.get("arguments");
            }
        }
        throw new AssertionError("no call to " + tool + " in " + structured);
    }

    private static ProfileInfo profileInfo() {
        return new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Heap dump", RecordingEventSource.HEAP_DUMP,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1");
    }
}
