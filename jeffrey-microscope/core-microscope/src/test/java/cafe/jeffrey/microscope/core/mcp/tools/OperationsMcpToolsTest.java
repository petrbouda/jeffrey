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

import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.profile.common.operation.OperationHandle;
import cafe.jeffrey.profile.common.operation.OperationState;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationsMcpToolsTest {

    /**
     * The description names the tools whose operationIds it reads, and only kinds that exist: a
     * reader told it could poll a "Hub activity scan" went looking for a tool that starts none.
     */
    @Test
    void statusNamesEveryToolThatStartsAnOperationAndNothingElse() {
        String description =
                new ReflectiveToolset(new OperationsMcpTools(new McpOperationRegistry(Clock.systemUTC()), kind -> true), "operations")
                .specs().stream()
                .filter(spec -> spec.name().equals("operations_status"))
                .findFirst()
                .orElseThrow()
                .description();

        assertFalse(description.contains("Hub activity"), description);
        for (String starter : List.of("recordings_analyzeFile", "recordings_analyzeRecording", "hubs_download",
                "hubs_fetchFile", "heap_prepare", "heap_oql", "jvm_autoAnalysis")) {
            assertTrue(description.contains(starter), starter + " missing from: " + description);
        }
    }

    @Test
    void cancellationReachesTheWorkerWhileItsProgressReadIsBlocked() throws Exception {
        BoundedJobs<String, String> jobs = ToolFixtures.jobs(Duration.ofMillis(10));
        McpOperationRegistry registry = new McpOperationRegistry(Clock.systemUTC());
        CountDownLatch workerEntered = new CountDownLatch(1);
        CountDownLatch progressEntered = new CountDownLatch(1);
        CountDownLatch releaseProgress = new CountDownLatch(1);
        CountDownLatch workerInterrupted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        OperationHandle<String> operation = jobs.startOrJoin("blocked-progress", false, value -> true, control -> {
            control.progressFrom(() -> {
                progressEntered.countDown();
                while (releaseProgress.getCount() != 0) {
                    try {
                        releaseProgress.await();
                    } catch (InterruptedException ignored) {
                        // Simulates a status query which cannot be interrupted immediately.
                    }
                }
                return OperationDetails.recording("recording-1", List.of());
            });
            workerEntered.countDown();
            try {
                releaseWorker.await();
            } catch (InterruptedException e) {
                workerInterrupted.countDown();
                control.checkCancellation();
            }
            return "result";
        });
        String id = registry.register(OperationKind.RECORDING_ANALYSIS, operation, OperationResults.Value::new);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            assertTrue(workerEntered.await(5, TimeUnit.SECONDS));
            Future<?> status = callers.submit(() -> registry.status(id));
            assertTrue(progressEntered.await(5, TimeUnit.SECONDS));
            Future<?> cancellation = callers.submit(() -> registry.cancel(id, kind -> true));
            assertTrue(workerInterrupted.await(1, TimeUnit.SECONDS), "slow progress must not hold the cancellation lock");
            cancellation.get(1, TimeUnit.SECONDS);
            releaseProgress.countDown();
            status.get(5, TimeUnit.SECONDS);
        } finally {
            releaseProgress.countDown();
            releaseWorker.countDown();
            callers.shutdownNow();
            assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void pendingCancellationKeepsTheExactAttemptUntilItFinishesAndNeverCancelsARetry() throws Exception {
        BoundedJobs<String, String> jobs = ToolFixtures.jobs(Duration.ofMillis(10));
        McpOperationRegistry registry = new McpOperationRegistry(Clock.systemUTC());
        OperationsMcpTools tools = new OperationsMcpTools(registry, kind -> true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger workCount = new AtomicInteger();
        OperationHandle<String> first = jobs.startOrJoin("session", false, value -> true, control -> {
            workCount.incrementAndGet();
            entered.countDown();
            while (release.getCount() != 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    // Simulates an operation that cannot stop until the current stage finishes.
                }
            }
            control.checkCancellation();
            return "copy";
        });
        String firstId = registry.register(OperationKind.HUB_DOWNLOAD, first, OperationResults.Recording::new);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            JsonNode cancelled = tools.cancel(firstId).structuredContent();
            assertConforms(cancelled);
            assertEquals("CANCEL_REQUESTED", cancelled.path("status").asString());
            assertTrue(cancelled.path("finishedAtEpochMs").isNull());
            assertFalse(cancelled.path("retryable").asBoolean());
            assertEquals(firstId, jobs.startOrJoin("session", true, value -> true, () -> "rival")
                    .snapshot().operationId());
            assertEquals("CANCEL_REQUESTED", tools.status(firstId).structuredContent().path("status").asString());
            assertEquals(1, workCount.get());
        } finally {
            release.countDown();
        }
        await().atMost(5, TimeUnit.SECONDS).until(() -> first.snapshot().state().terminal());
        OperationHandle<String> retry = jobs.startOrJoin("session", true, value -> true, () -> "new-copy");
        String retryId = registry.register(OperationKind.HUB_DOWNLOAD, retry, OperationResults.Recording::new);
        assertFalse(firstId.equals(retryId));
        JsonNode finishedCancel = tools.cancel(firstId).structuredContent();
        assertConforms(finishedCancel);
        assertEquals("CANCELLED", finishedCancel.path("status").asString());
        assertEquals("CANCELLED", finishedCancel.path("error").path("code").asString());
        await().atMost(5, TimeUnit.SECONDS).until(() -> retry.snapshot().state().terminal());
        assertEquals(OperationState.COMPLETED, registry.status(retryId).status());
        assertEquals("new-copy", Json.mapper().readTree(tools.status(retryId).text()).path("result").path("recordingId").asString());
    }

    /**
     * The snapshot is a typed record end to end: the kind, state and phase are enum names, time is UTC
     * epoch milliseconds, progress is the one details shape, and only the result is left open.
     */
    @Test
    void answersWithATypedSnapshotThatFitsTheGeneratedSchema() {
        MutableClock clock = new MutableClock();
        BoundedJobs<String, String> jobs = ToolFixtures.jobs(BoundedJobs.WAIT_BUDGET, Duration.ofHours(1), clock);
        McpOperationRegistry registry = new McpOperationRegistry(clock);
        OperationsMcpTools tools = new OperationsMcpTools(registry, kind -> true);
        String id = registry.register(OperationKind.RECORDING_ANALYSIS, jobs.rememberCompleted("r", "p"),
                OperationResults.Profile::new, () -> "r");

        JsonNode snapshot = tools.status(id).structuredContent();

        assertConforms(snapshot);
        assertEquals("RECORDING_ANALYSIS", snapshot.path("kind").asString());
        assertEquals("COMPLETED", snapshot.path("status").asString());
        assertEquals(clock.now.toEpochMilli(), snapshot.path("startedAtEpochMs").asLong());
        assertEquals(clock.now.toEpochMilli(), snapshot.path("finishedAtEpochMs").asLong());
        assertEquals("ALREADY_AVAILABLE", snapshot.path("progress").path("phase").asString());
        assertTrue(snapshot.path("progress").path("details").path("stages").isEmpty());
        assertEquals("p", snapshot.path("result").path("profileId").asString());
        assertTrue(snapshot.path("error").isNull());
        assertFalse(snapshot.has("nextSteps"));
        assertTrue(snapshot.path("followUp").path("nextTools").isEmpty(), snapshot.toString());
        assertFalse(snapshot.path("followUp").path("guidance").isEmpty());
    }

    /** A running attempt names the call that polls it, with its operationId, ready to pass on. */
    @Test
    void aRunningOperationHandsBackThePollCall() throws Exception {
        BoundedJobs<String, String> jobs = ToolFixtures.jobs(Duration.ofMillis(10));
        McpOperationRegistry registry = new McpOperationRegistry(Clock.systemUTC());
        OperationsMcpTools tools = new OperationsMcpTools(registry, kind -> true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        OperationHandle<String> running = jobs.startOrJoin("running", false, value -> true, control -> {
            control.phase(OperationPhase.DOWNLOADING);
            control.progress(OperationDetails.hubDownload("ref-1", "session-1", 2048));
            entered.countDown();
            await().atMost(5, TimeUnit.SECONDS).until(() -> release.getCount() == 0);
            return "recording-1";
        });
        String id = registry.register(OperationKind.HUB_DOWNLOAD, running, OperationResults.Recording::new);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            JsonNode snapshot = tools.status(id).structuredContent();

            assertConforms(snapshot);
            assertEquals("RUNNING", snapshot.path("status").asString());
            assertTrue(snapshot.path("finishedAtEpochMs").isNull());
            assertEquals("DOWNLOADING", snapshot.path("progress").path("phase").asString());
            assertEquals("ref-1", snapshot.path("progress").path("details").path("sessionRef").asString());
            assertEquals(2048, snapshot.path("progress").path("details").path("sizeBytes").asLong());
            assertTrue(snapshot.path("result").isNull());
            JsonNode poll = snapshot.path("followUp").path("nextTools").get(0);
            assertEquals("operations_status", poll.path("tool").asString());
            assertEquals(id, poll.path("arguments").path("operationId").asString());
            assertEquals(1, McpNextToolConformance.assertFollowable(snapshot, operationsSpecs()));
        } finally {
            release.countDown();
        }
    }

    /**
     * A failed recording analysis names the retry with the recording it was for: a call ready to pass
     * on, not a sentence to turn into one.
     */
    @Test
    void aFailedRecordingAnalysisHandsBackTheRetryCall() {
        BoundedJobs<String, String> jobs = ToolFixtures.jobs(BoundedJobs.WAIT_BUDGET);
        McpOperationRegistry registry = new McpOperationRegistry(Clock.systemUTC());
        OperationsMcpTools tools = new OperationsMcpTools(registry, kind -> true);
        OperationHandle<String> failed = jobs.startOrJoin("rec-9", false, value -> true, () -> {
            throw new IllegalStateException("parser stopped");
        });
        String id = registry.register(OperationKind.RECORDING_ANALYSIS, failed, OperationResults.Profile::new,
                () -> "rec-9");
        await().atMost(5, TimeUnit.SECONDS).until(() -> failed.snapshot().state().terminal());

        JsonNode snapshot = tools.status(id).structuredContent();

        assertConforms(snapshot);
        assertEquals("FAILED", snapshot.path("status").asString());
        assertEquals("OPERATION_FAILED", snapshot.path("error").path("code").asString());
        assertTrue(snapshot.path("retryable").asBoolean());
        JsonNode retry = snapshot.path("followUp").path("nextTools").get(0);
        assertEquals("recordings_analyzeRecording", retry.path("tool").asString());
        assertEquals("rec-9", retry.path("arguments").path("recordingId").asString());
        assertTrue(retry.path("arguments").path("retry").asBoolean());
        assertEquals(1, McpNextToolConformance.assertFollowable(snapshot, CatalogueSpecs.of(operationsSpecs(),
                CatalogueSpecs.served(RecordingsMcpToolsFixture.of(null, null, registry, Clock.systemUTC()).build(),
                        "recordings"))));
    }

    /**
     * A kind registered with its retry call hands that call back when an attempt fails, with the
     * arguments the attempt ran with, rather than the kind's retry sentence.
     */
    @Test
    void aFailedOperationRegisteredWithItsRetryHandsBackThatCall() {
        BoundedJobs<String, String> jobs = ToolFixtures.jobs(BoundedJobs.WAIT_BUDGET);
        McpOperationRegistry registry = new McpOperationRegistry(Clock.systemUTC());
        OperationHandle<String> failed = jobs.startOrJoin("heap", false, value -> true, () -> {
            throw new IllegalStateException("index failed");
        });
        McpNextTool retry = NextCalls.to("heap_prepare").with("profileId", "p-1").with("retry", true)
                .why("starts a new attempt");
        String id = registry.register(OperationKind.HEAP_PREPARE, failed, OperationResults.Value::new,
                value -> McpToolResult.text(value), retry);
        await().atMost(5, TimeUnit.SECONDS).until(() -> failed.snapshot().state().terminal());

        JsonNode snapshot = new OperationsMcpTools(registry, kind -> true).status(id).structuredContent();

        assertConforms(snapshot);
        assertEquals("heap_prepare", snapshot.path("followUp").path("nextTools").get(0).path("tool").asString());
        assertTrue(snapshot.path("followUp").path("nextTools").get(0).path("arguments").path("retry").asBoolean());
        assertFalse(snapshot.path("followUp").toString().contains(OperationKind.HEAP_PREPARE.retryInstruction()));
    }

    /** A completed attempt names no retry, whatever it was registered with. */
    @Test
    void aCompletedOperationRegisteredWithItsRetryDoesNotOfferIt() {
        BoundedJobs<String, String> jobs = ToolFixtures.jobs(BoundedJobs.WAIT_BUDGET);
        McpOperationRegistry registry = new McpOperationRegistry(Clock.systemUTC());
        McpNextTool retry = NextCalls.to("heap_prepare").with("profileId", "p-1").with("retry", true)
                .why("starts a new attempt");
        String id = registry.register(OperationKind.HEAP_PREPARE, jobs.rememberCompleted("heap", "done"),
                OperationResults.Value::new, value -> McpToolResult.text(value), retry);

        JsonNode snapshot = new OperationsMcpTools(registry, kind -> true).status(id).structuredContent();

        assertTrue(snapshot.path("followUp").path("nextTools").isEmpty(), snapshot.toString());
    }

    /** A kind that is not tied to a recording keeps its retry as advice: its arguments are not known here. */
    @Test
    void aFailedHubDownloadKeepsItsRetryAsGuidance() {
        BoundedJobs<String, String> jobs = ToolFixtures.jobs(BoundedJobs.WAIT_BUDGET);
        McpOperationRegistry registry = new McpOperationRegistry(Clock.systemUTC());
        OperationHandle<String> failed = jobs.startOrJoin("session", false, value -> true, () -> {
            throw new IllegalStateException("hub unreachable");
        });
        String id = registry.register(OperationKind.HUB_DOWNLOAD, failed, OperationResults.Recording::new);
        await().atMost(5, TimeUnit.SECONDS).until(() -> failed.snapshot().state().terminal());

        JsonNode snapshot = new OperationsMcpTools(registry, kind -> true).status(id).structuredContent();

        assertConforms(snapshot);
        assertTrue(snapshot.path("followUp").path("nextTools").isEmpty());
        assertEquals(OperationKind.HUB_DOWNLOAD.retryInstruction(),
                snapshot.path("followUp").path("guidance").get(0).asString());
    }

    private static List<McpToolSpec> operationsSpecs() {
        return CatalogueSpecs.served(new OperationsMcpTools(new McpOperationRegistry(Clock.systemUTC()), kind -> true),
                "operations");
    }

    private static void assertConforms(JsonNode snapshot) {
        McpSchemaConformance.assertConforms(snapshot, McpSchemaGenerator.schemaOf(McpOperationRegistry.Snapshot.class));
    }

    @Test
    void appliesOriginatingFamilyGatesToBothStatusAndCancellation() {
        BoundedJobs<String, String> jobs = ToolFixtures.jobs(BoundedJobs.WAIT_BUDGET);
        McpOperationRegistry registry = new McpOperationRegistry(Clock.systemUTC());
        OperationHandle<String> finished = jobs.rememberCompleted("s", "r");
        String id = registry.register(OperationKind.HUB_DOWNLOAD, finished, OperationResults.Value::new);
        OperationsMcpTools hidden = new OperationsMcpTools(registry, kind -> kind != OperationKind.HUB_DOWNLOAD);
        assertThrows(IllegalArgumentException.class, () -> hidden.status(id));
        assertThrows(IllegalArgumentException.class, () -> hidden.cancel(id));
        assertEquals(OperationState.COMPLETED, registry.status(id).status());
    }

    @Test
    void expiredIdsNeverStartReplacementWork() {
        MutableClock clock = new MutableClock();
        BoundedJobs<String, String> jobs = ToolFixtures.jobs(Duration.ofSeconds(1), Duration.ofHours(1), clock);
        McpOperationRegistry registry = new McpOperationRegistry(clock);
        String id = registry.register(OperationKind.RECORDING_ANALYSIS, jobs.rememberCompleted("r", "p"), OperationResults.Value::new);
        clock.now = clock.now.plus(Duration.ofHours(2));
        assertThrows(IllegalArgumentException.class, () -> registry.status(id));
        assertThrows(IllegalArgumentException.class, () -> registry.cancel(id, kind -> true));
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
}
