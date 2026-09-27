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

import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.ProfileInitStages;
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRequest;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.JeffreyMcpServer;
import cafe.jeffrey.storage.recording.api.file.Recording;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import cafe.jeffrey.microscope.mcp.protocol.McpCursor;
import cafe.jeffrey.microscope.mcp.protocol.McpInputResponse;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskState;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskStatus;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.profile.common.operation.OperationState;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.function.ThrowingSupplier;
import tools.jackson.databind.JsonNode;

import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.ELICITING;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.NO_TASKS;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.answering;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.SHORT_TASK_WAIT;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.TASKS;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.complete;
import static cafe.jeffrey.microscope.mcp.protocol.McpCallContext.RESOURCE_READ;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingsMcpToolsTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
    private static final String RECORDING_ID = "rec-1";
    private static final String PROFILE_ID = "prof-1";
    /** The retry a failed or interrupted analysis hands back, as it appears in the answer's JSON. */
    private static final String RETRY_CALL = "\"tool\":\"recordings_analyzeRecording\",\"arguments\":{\"recordingId\":\""
            + RECORDING_ID + "\",\"retry\":true}";

    @Mock
    RecordingsManager recordingsManager;

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);

    private RecordingsMcpTools tools;
    private PipelineRunRegistry<String> runRegistry;
    private McpOperationRegistry operations;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        runRegistry = new PipelineRunRegistry<>(
                ProfileInitStages.DEFINITION, PipelineRunOptions.unbounded(), CLOCK);
        operations = new McpOperationRegistry(CLOCK);
        tools = RecordingsMcpToolsFixture.of(recordingsManager, runRegistry, operations, CLOCK).build();
        // The tools build a UI link off the incoming request, the way ProfileMcpTools#link does.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private Path recordingFile(String filename) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, "not really a recording, but a real file");
        return file;
    }

    /**
     * Whether the profile behind the recording is finished and usable.
     */
    private void profileIs(boolean enabled) {
        profileIs(enabled, "app.jfr");
    }

    private void profileIs(boolean enabled, String name) {
        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, null, null, name, RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, enabled, false,
                RECORDING_ID));
        when(recordingsManager.profile(PROFILE_ID)).thenReturn(Optional.of(profileManager));
    }

    private static Recording recording(boolean hasProfile) {
        return new Recording(
                RECORDING_ID, "app.jfr", null, RecordingEventSource.JDK,
                START, START, START.plusSeconds(60),
                hasProfile, hasProfile ? PROFILE_ID : null, hasProfile ? "app.jfr" : null, List.of());
    }

    @Test
    void importedProfileExposesItsOperationIdentityAndCanonicalStatus() throws IOException {
        Path file = recordingFile("operation.jfr");
        when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
        when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
        when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
        String result = complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ));
        assertTrue(result.contains("\"operationId\""), result);
        assertTrue(result.contains("\"operation\""), result);
        assertTrue(result.contains("\"status\":\"COMPLETED\""), result);
    }

    @Test
    void copyingIsBoundedAndCancellationPreventsAnalysisAfterTheCopyReturns() throws Exception {
        Path file = recordingFile("large.jfr");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
        RecordingsMcpTools bounded = RecordingsMcpToolsFixture.of(recordingsManager, runRegistry, operations, CLOCK)
                .withJobs(ToolFixtures.jobs(Duration.ofMillis(50))).build();
        when(recordingsManager.importRecordingFromPath(file)).thenAnswer(invocation -> {
            entered.countDown();
            while (release.getCount() != 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    // Simulate a copy implementation that finishes its current write first.
                }
            }
            return RECORDING_ID;
        });
        CompletableFuture<String> call = CompletableFuture.supplyAsync(
                () -> withRequestContext(
                        () -> complete(bounded.analyzeFile(file.toString(), null, null, RESOURCE_READ))));
        String operationId;
        try {
            assertTrue(entered.await(5, SECONDS));
            String response = call.get(1, SECONDS);
            operationId = Json.mapper().readTree(response).path("operationId").asString();
            assertFalse(operationId.isBlank());
            assertEquals(OperationState.CANCEL_REQUESTED, operations.cancel(operationId, kind -> true).status());
        } finally {
            release.countDown();
        }
        await().atMost(5, SECONDS).until(() -> operations.status(operationId).status() == OperationState.CANCELLED);
        verify(recordingsManager, never()).analyzeRecording(anyString());
    }

    /**
     * A failed import is a failed call, not an answer: it surfaces as a tool error so the envelope
     * marks it isError, and it still names the operation so operations_status can be asked about it.
     */
    @Test
    void immediateImportFailureIsAToolErrorThatNamesTheOperation() throws IOException {
        Path file = recordingFile("broken.jfr");
        when(recordingsManager.importRecordingFromPath(file)).thenThrow(new IllegalStateException("copy failed"));

        ToolExecutionException e = assertThrows(ToolExecutionException.class,
                () -> complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ)));

        assertTrue(e.getMessage().contains("copy failed"), e.getMessage());
        assertTrue(e.getMessage().contains("\"status\":\"FAILED\""), e.getMessage());
        String operationId = Json.mapper().readTree(e.getMessage().substring(e.getMessage().indexOf('{')))
                .path("operationId").asString();
        assertEquals(OperationState.FAILED, operations.status(operationId).status());
        verify(recordingsManager, never()).analyzeRecording(anyString());
    }

    @Test
    void cancellationAfterAnalysisSucceedsPreservesTheProfileAndAcceptedName() throws Exception {
        Path file = recordingFile("finishing.jfr");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
        RecordingsMcpTools bounded = RecordingsMcpToolsFixture.of(recordingsManager, runRegistry, operations, CLOCK)
                .withJobs(ToolFixtures.jobs(Duration.ofMillis(50))).build();
        when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
        when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
        when(recordingsManager.analyzeRecording(RECORDING_ID)).thenAnswer(invocation -> {
            entered.countDown();
            while (release.getCount() != 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    // Analysis cannot abort its final durable write and returns a usable profile.
                }
            }
            return PROFILE_ID;
        });
        String operationId =
                Json.mapper().readTree(complete(bounded.analyzeFile(file.toString(), "Accepted name", null, RESOURCE_READ)))
                .path("operationId").asString();
        try {
            assertTrue(entered.await(5, SECONDS));
            assertEquals(OperationState.CANCEL_REQUESTED, operations.cancel(operationId, kind -> true).status());
        } finally {
            release.countDown();
        }
        await().atMost(5, SECONDS).until(() -> operations.status(operationId).finishedAtEpochMs() != null);
        assertEquals(OperationState.COMPLETED, operations.status(operationId).status());
        assertTrue(operations.status(operationId).cancellationRequested());
        assertEquals(PROFILE_ID, Json.mapper().valueToTree(operations.status(operationId).result())
                .path("profileId").asString());
        verify(recordingsManager).updateProfileName(PROFILE_ID, "Accepted name");
    }

    @Test
    void aDeletedProfileIsReanalyzedInsteadOfReturningItsRetainedId() {
        when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID, "replacement-profile");
        when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
        profileIs(true);
        assertTrue(complete(tools.analyzeRecording(RECORDING_ID, true, RESOURCE_READ)).contains(PROFILE_ID));
        when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(false)));
        ProfileManager replacement = mock(ProfileManager.class);
        when(replacement.info()).thenReturn(new ProfileInfo(
                "replacement-profile", null, null, "app.jfr", RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, RECORDING_ID));
        when(recordingsManager.profile("replacement-profile")).thenReturn(Optional.of(replacement));
        String response = complete(tools.analyzeRecording(RECORDING_ID, true, RESOURCE_READ));
        assertTrue(response.contains("replacement-profile"), response);
        verify(recordingsManager, times(2)).analyzeRecording(RECORDING_ID);
    }

    @Nested
    class AnalyzeFile {

        @Test
        void importsTheFileAndReturnsTheProfileItBuilt() throws IOException {
            Path file = recordingFile("app.jfr");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            String result = complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ));

            assertTrue(result.contains(PROFILE_ID));
            assertTrue(result.contains(RECORDING_ID));
        }

        /**
         * The profile id is what every other family takes, so it has to come back under a name the
         * model can pick out of the JSON rather than buried in prose.
         */
        @Test
        void namesTheProfileIdInTheResult() throws IOException {
            Path file = recordingFile("app.jfr");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            assertTrue(complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ))
                    .contains("\"profileId\":\"" + PROFILE_ID + "\""));
        }

        @Test
        void renamesTheProfileWhenAskedTo() throws IOException {
            Path file = recordingFile("app.jfr");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true, "Checkout run");

            String result = complete(tools.analyzeFile(file.toString(), "Checkout run", null, RESOURCE_READ));

            verify(recordingsManager).updateProfileName(PROFILE_ID, "Checkout run");
            assertTrue(result.contains("Checkout run"));
        }

        @Test
        void joinedCallersSeeTheNameAppliedByTheAttemptTheyJoined() throws Exception {
            Path file = recordingFile("app.jfr");
            BoundedJobs<String, String> jobs = ToolFixtures.jobs(Duration.ofSeconds(5));
            RecordingsMcpTools concurrent = RecordingsMcpToolsFixture.of(recordingsManager, runRegistry, operations, CLOCK)
                    .withJobs(jobs).build();
            CountDownLatch analysisReached = new CountDownLatch(1);
            CountDownLatch releaseAnalysis = new CountDownLatch(1);
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenAnswer(invocation -> {
                analysisReached.countDown();
                releaseAnalysis.await();
                return PROFILE_ID;
            });
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true, "First name");

            CompletableFuture<String> first = CompletableFuture.supplyAsync(
                    () -> withRequestContext(
                            () -> complete(concurrent.analyzeFile(file.toString(), "First name", null, RESOURCE_READ))));
            assertTrue(analysisReached.await(5, SECONDS));
            CompletableFuture<String> second = CompletableFuture.supplyAsync(
                    () -> withRequestContext(
                            () -> complete(concurrent.analyzeFile(file.toString(), "Second name", null, RESOURCE_READ))));
            await().during(Duration.ofMillis(50)).atMost(5, SECONDS).until(() -> !second.isDone());
            releaseAnalysis.countDown();

            assertTrue(first.get(5, SECONDS).contains("\"name\":\"First name\""));
            assertTrue(second.get(5, SECONDS).contains("\"name\":\"First name\""));
            verify(recordingsManager).updateProfileName(PROFILE_ID, "First name");
            verify(recordingsManager, never()).updateProfileName(PROFILE_ID, "Second name");
        }

        /**
         * A file with the same name and size as one already stored is answered with the profile the
         * first call built, rather than a second copy and a second profile of one recording.
         */
        @Test
        void returnsTheExistingProfileForAnUnchangedFile() throws IOException {
            Path file = recordingFile("app.jfr");
            when(recordingsManager.findByFileNameAndSize("app.jfr", Files.size(file)))
                    .thenReturn(Optional.of(recording(true)));
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true);

            var result =
                    Json.mapper().readTree(complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ)));

            assertEquals(PROFILE_ID, result.path("profileId").asString(), result.toString());
            assertEquals(RECORDING_ID, result.path("recordingId").asString(), result.toString());
            assertTrue(result.path("reused").asBoolean(), result.toString());
            verify(recordingsManager, never()).importRecordingFromPath(any());
            verify(recordingsManager, never()).analyzeRecording(anyString());
        }

        /**
         * A stored file of the same name and size whose analysis never produced a profile is analysed where it is,
         * the way recordings_analyzeRecording would, instead of being copied in again.
         */
        @Test
        void analysesTheStoredRecordingWhenTheUnchangedFileHasNoProfileYet() throws IOException {
            Path file = recordingFile("app.jfr");
            when(recordingsManager.findByFileNameAndSize("app.jfr", Files.size(file)))
                    .thenReturn(Optional.of(recording(false)));
            when(recordingsManager.findRecording(RECORDING_ID))
                    .thenReturn(Optional.of(recording(false)), Optional.of(recording(true)));
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);

            String result = complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ));

            assertTrue(result.contains("\"profileId\":\"" + PROFILE_ID + "\""), result);
            verify(recordingsManager, never()).importRecordingFromPath(any());
            verify(recordingsManager).analyzeRecording(RECORDING_ID);
        }

        @Test
        void forceImportsTheFileAgainEvenWhenItIsUnchanged() throws IOException {
            Path file = recordingFile("app.jfr");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn("rec-2");
            when(recordingsManager.analyzeRecording("rec-2")).thenReturn("prof-2");
            when(recordingsManager.findRecording("rec-2")).thenReturn(Optional.of(new Recording(
                    "rec-2", "app.jfr", null, RecordingEventSource.JDK, START, START, START.plusSeconds(60),
                    true, "prof-2", "app.jfr", List.of())));

            var result =
                    Json.mapper().readTree(complete(tools.analyzeFile(file.toString(), null, true, RESOURCE_READ)));

            assertEquals("prof-2", result.path("profileId").asString(), result.toString());
            assertFalse(result.path("reused").asBoolean(), result.toString());
            verify(recordingsManager, never()).findByFileNameAndSize(anyString(), anyLong());
            verify(recordingsManager).importRecordingFromPath(file);
        }

        /**
         * A file written to since it was imported — a re-run of the benchmark into the same path —
         * has a different size, so it is a different recording and is imported rather than answered
         * with the old profile.
         */
        @Test
        void importsAFileWhoseSizeChangedSinceItWasImported() throws IOException {
            Path file = recordingFile("app.jfr");
            long importedSize = Files.size(file);
            lenient().when(recordingsManager.findByFileNameAndSize("app.jfr", importedSize))
                    .thenReturn(Optional.of(recording(true)));
            Files.writeString(file, "a longer recording written by the next run of the benchmark");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            String result = complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ));

            assertTrue(result.contains("\"reused\":false"), result);
            verify(recordingsManager).findByFileNameAndSize("app.jfr", Files.size(file));
            verify(recordingsManager).importRecordingFromPath(file);
        }

        /**
         * The match is by the file's own name, not its path: the same file under another directory
         * is still recognised.
         */
        @Test
        void matchesByTheFileNameWhateverDirectoryItIsIn() throws IOException {
            Path file = Files.createDirectories(tempDir.resolve("elsewhere")).resolve("app.jfr");
            Files.writeString(file, "JFR");
            when(recordingsManager.findByFileNameAndSize("app.jfr", Files.size(file)))
                    .thenReturn(Optional.of(recording(true)));
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true);

            var result =
                    Json.mapper().readTree(complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ)));

            assertTrue(result.path("reused").asBoolean(), result.toString());
            verify(recordingsManager, never()).importRecordingFromPath(any());
        }

        @Test
        void leavesTheProfileNameAloneWhenNoneWasGiven() throws IOException {
            Path file = recordingFile("app.jfr");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            complete(tools.analyzeFile(file.toString(), "   ", null, RESOURCE_READ));

            verify(recordingsManager, never()).updateProfileName(anyString(), anyString());
        }

        /**
         * A relative path is the mistake worth catching by hand: it would resolve against Jeffrey's
         * working directory rather than the caller's repository, so it either finds nothing or — worse —
         * finds a different file with the same name.
         */
        @Test
        void rejectsARelativePathBeforeTouchingTheStore() {
            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                    () -> complete(tools.analyzeFile("target/app.jfr", null, null, RESOURCE_READ)));

            assertTrue(e.getMessage().contains("absolute"));
            verifyNoInteractions(recordingsManager);
        }

        @Test
        void rejectsAMissingFileAndSaysWhoseFilesystemItLookedOn() {
            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                    () -> complete(tools.analyzeFile(tempDir.resolve("nowhere.jfr").toString(), null, null, RESOURCE_READ)));

            assertTrue(e.getMessage().contains("Jeffrey"));
            verifyNoInteractions(recordingsManager);
        }

        @Test
        void rejectsAFileTypeJeffreyCannotParse() throws IOException {
            Path file = recordingFile("notes.txt");

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                    () -> complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ)));

            assertTrue(e.getMessage().contains("Unsupported"));
            verifyNoInteractions(recordingsManager);
        }

        @Test
        void rejectsAnAbsentPath() {
            assertThrows(IllegalArgumentException.class,
                    () -> complete(tools.analyzeFile(null, null, null, RESOURCE_READ)));
            verifyNoInteractions(recordingsManager);
        }

        /**
         * A heap dump goes down a different path inside the manager, but the tool treats it the same:
         * both end as a profile the read-only families can answer about.
         */
        @Test
        void acceptsAHeapDumpToo() throws IOException {
            Path file = recordingFile("heap.hprof");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            assertTrue(complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ)).contains(PROFILE_ID));
        }
    }

    /**
     * Two calls for one file that is not in the store yet. The reuse check cannot see an import still
     * copying the file in, so the in-flight import is keyed on the same identity the check uses --
     * the file's name and size -- and the second call joins the first instead of importing it again.
     */
    @Nested
    class ConcurrentImports {

        private static final String SECOND_RECORDING_ID = "rec-2";
        private static final String SECOND_PROFILE_ID = "prof-2";

        private RecordingsMcpTools concurrent(Duration waitBudget) {
            return RecordingsMcpToolsFixture.of(recordingsManager, runRegistry, operations, CLOCK)
                    .withJobs(ToolFixtures.jobs(waitBudget)).build();
        }

        private Recording stored(String recordingId, String profileId) {
            return new Recording(recordingId, "app.jfr", null, RecordingEventSource.JDK,
                    START, START, START.plusSeconds(60), true, profileId, "app.jfr", List.of());
        }

        @Test
        void aSecondCallForTheSameNewFileJoinsTheImportInFlight() throws Exception {
            Path file = recordingFile("app.jfr");
            RecordingsMcpTools tools = concurrent(Duration.ofSeconds(5));
            CountDownLatch importing = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            when(recordingsManager.importRecordingFromPath(file)).thenAnswer(invocation -> {
                importing.countDown();
                awaitQuietly(release);
                return RECORDING_ID;
            });
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            CompletableFuture<String> first = CompletableFuture.supplyAsync(
                    () -> withRequestContext(
                            () -> complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ))));
            CompletableFuture<String> second;
            try {
                assertTrue(importing.await(5, SECONDS));
                second = CompletableFuture.supplyAsync(
                        () -> withRequestContext(
                                () -> complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ))));
                // Past the reuse check, which found nothing because the first copy has not landed.
                verify(recordingsManager, timeout(5_000).times(2)).findByFileNameAndSize("app.jfr", Files.size(file));
                await().during(Duration.ofMillis(100)).atMost(5, SECONDS).until(() -> !second.isDone());
            } finally {
                release.countDown();
            }

            var firstAnswer = Json.mapper().readTree(first.get(5, SECONDS));
            var secondAnswer = Json.mapper().readTree(second.get(5, SECONDS));
            assertEquals(firstAnswer.path("operationId").asString(), secondAnswer.path("operationId").asString());
            assertEquals(PROFILE_ID, firstAnswer.path("profileId").asString(), firstAnswer.toString());
            assertEquals(PROFILE_ID, secondAnswer.path("profileId").asString(), secondAnswer.toString());
            verify(recordingsManager, times(1)).importRecordingFromPath(file);
            verify(recordingsManager, times(1)).analyzeRecording(RECORDING_ID);
        }

        @Test
        void aForcedCallDuringAnImportInFlightStartsItsOwn() throws Exception {
            Path file = recordingFile("app.jfr");
            RecordingsMcpTools tools = concurrent(Duration.ofMillis(50));
            CountDownLatch importing = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger imports = new AtomicInteger();
            when(recordingsManager.importRecordingFromPath(file)).thenAnswer(invocation -> {
                if (imports.incrementAndGet() > 1) {
                    return SECOND_RECORDING_ID;
                }
                importing.countDown();
                awaitQuietly(release);
                return RECORDING_ID;
            });
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.analyzeRecording(SECOND_RECORDING_ID)).thenReturn(SECOND_PROFILE_ID);
            lenient().when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            lenient().when(recordingsManager.findRecording(SECOND_RECORDING_ID))
                    .thenReturn(Optional.of(stored(SECOND_RECORDING_ID, SECOND_PROFILE_ID)));

            String firstOperation;
            String forcedOperation;
            try {
                CompletableFuture<String> first = CompletableFuture.supplyAsync(
                        () -> withRequestContext(
                                () -> complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ))));
                assertTrue(importing.await(5, SECONDS));
                firstOperation = Json.mapper().readTree(first.get(5, SECONDS)).path("operationId").asString();
                forcedOperation = Json.mapper().readTree(withRequestContext(
                        () -> complete(tools.analyzeFile(file.toString(), null, true, RESOURCE_READ))))
                        .path("operationId").asString();

                assertFalse(firstOperation.equals(forcedOperation), forcedOperation);
                await().atMost(5, SECONDS).until(() -> operations.status(forcedOperation).finishedAtEpochMs() != null);
                assertEquals(OperationState.COMPLETED, operations.status(forcedOperation).status());
                assertEquals(SECOND_PROFILE_ID, Json.mapper().valueToTree(operations.status(forcedOperation).result())
                        .path("profileId").asString());
            } finally {
                release.countDown();
            }

            await().atMost(5, SECONDS).until(() -> operations.status(firstOperation).finishedAtEpochMs() != null);
            assertEquals(PROFILE_ID, Json.mapper().valueToTree(operations.status(firstOperation).result())
                    .path("profileId").asString());
            verify(recordingsManager, times(2)).importRecordingFromPath(file);
        }

        @Test
        void aCallAfterTheImportFinishedTakesTheReusePath() throws IOException {
            Path file = recordingFile("app.jfr");
            long size = Files.size(file);
            when(recordingsManager.findByFileNameAndSize("app.jfr", size))
                    .thenReturn(Optional.empty(), Optional.of(recording(true)));
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true);

            var imported =
                    Json.mapper().readTree(complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ)));
            var reused =
                    Json.mapper().readTree(complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ)));

            assertEquals(PROFILE_ID, imported.path("profileId").asString(), imported.toString());
            assertFalse(imported.path("reused").asBoolean(), imported.toString());
            assertEquals(PROFILE_ID, reused.path("profileId").asString(), reused.toString());
            assertTrue(reused.path("reused").asBoolean(), reused.toString());
            assertTrue(reused.path("operationId").isNull(), reused.toString());
            verify(recordingsManager, times(1)).importRecordingFromPath(file);
        }

        /**
         * The finished import is retained under the file's identity for an hour. Once its recording
         * has been deleted the reuse check finds nothing, and the file is imported again rather than
         * answered with the profile the retained attempt built.
         */
        @Test
        void aFileWhoseImportedRecordingWasDeletedIsImportedAgain() throws IOException {
            Path file = recordingFile("app.jfr");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID, SECOND_RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.analyzeRecording(SECOND_RECORDING_ID)).thenReturn(SECOND_PROFILE_ID);
            AtomicBoolean deleted = new AtomicBoolean();
            when(recordingsManager.findRecording(RECORDING_ID)).thenAnswer(
                    invocation -> deleted.get() ? Optional.empty() : Optional.of(recording(true)));
            when(recordingsManager.findRecording(SECOND_RECORDING_ID))
                    .thenReturn(Optional.of(stored(SECOND_RECORDING_ID, SECOND_PROFILE_ID)));

            complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ));
            deleted.set(true);
            var again = Json.mapper().readTree(complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ)));

            assertEquals(SECOND_PROFILE_ID, again.path("profileId").asString(), again.toString());
            verify(recordingsManager, times(2)).importRecordingFromPath(file);
        }

        /**
         * A failed import is not held against the file: the next call imports it again, as each call
         * did when every import had a key of its own. Only a call that arrives while the failing
         * attempt is still running shares its outcome.
         */
        @Test
        void aFailedImportIsRetriedByTheNextCall() throws IOException {
            Path file = recordingFile("app.jfr");
            when(recordingsManager.importRecordingFromPath(file))
                    .thenThrow(new IllegalStateException("copy failed"))
                    .thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            assertThrows(ToolExecutionException.class,
                    () -> complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ)));
            var retried =
                    Json.mapper().readTree(complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ)));

            assertEquals(PROFILE_ID, retried.path("profileId").asString(), retried.toString());
            verify(recordingsManager, times(2)).importRecordingFromPath(file);
        }
    }

    @Nested
    class AnalyzeRecording {

        @Test
        void analyzesARecordingThatIsAlreadyStored() {
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            assertTrue(complete(tools.analyzeRecording(RECORDING_ID, true, RESOURCE_READ)).contains(PROFILE_ID));
            verify(recordingsManager, never()).importRecordingFromPath(any());
        }

        @Test
        void rejectsABlankRecordingId() {
            assertThrows(IllegalArgumentException.class,
                    () -> complete(tools.analyzeRecording("  ", null, RESOURCE_READ)));
            verifyNoInteractions(recordingsManager);
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesTheRecordingAndReportsTheProfileThatWentWithIt() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            String result = complete(tools.delete(RECORDING_ID, RESOURCE_READ));

            verify(recordingsManager).deleteRecording(RECORDING_ID);
            assertTrue(result.contains("\"recordingId\":\"" + RECORDING_ID + "\""), result);
            assertTrue(result.contains("\"profileId\":\"" + PROFILE_ID + "\""), result);
            JsonNode answer = Json.readTree(result);
            McpSchemaConformance.assertConforms(answer,
                    McpSchemaGenerator.schemaOf(RecordingsMcpTools.RecordingDeletion.class));
            assertEquals("DELETED", answer.path("status").asString());
            assertTrue(answer.path("reason").isNull());
            UiLinkRoutes.assertResolves(answer.path("uiLink").asString());
            assertTrue(answer.path("uiLink").asString().endsWith("/recordings"));
        }

        @Test
        void aRecordingWithoutAProfileIsDeletedTheSameWay() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(false)));

            String result = complete(tools.delete(RECORDING_ID, RESOURCE_READ));

            verify(recordingsManager).deleteRecording(RECORDING_ID);
            assertTrue(result.contains("\"profileId\":null"), result);
        }

        @Test
        void anUnknownRecordingIsRefusedInASentence() {
            when(recordingsManager.findRecording("missing")).thenReturn(Optional.empty());

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> complete(tools.delete("missing", RESOURCE_READ)));

            assertTrue(e.getMessage().contains("missing"), e.getMessage());
            verify(recordingsManager, never()).deleteRecording(any());
        }

        /**
         * The parse writes into the profile's own storage. Removing it underneath leaves the run
         * writing into a directory that is no longer there, which is why analyzeRecording refuses
         * to disturb a live run for the same reason.
         */
        @Test
        void aRecordingWhoseProfileIsStillBeingBuiltIsRefused() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            CountDownLatch release = new CountDownLatch(1);
            runRegistry.start(PipelineRunRequest.of(PROFILE_ID, run -> {
                run.beginStage(ProfileInitStages.PARSE);
                awaitQuietly(release);
            }));
            await().atMost(5, SECONDS).until(() -> runRegistry.isRunning(PROFILE_ID));

            try {
                IllegalArgumentException e = assertThrows(
                        IllegalArgumentException.class, () -> complete(tools.delete(RECORDING_ID, RESOURCE_READ)));

                assertTrue(e.getMessage().contains("operations_cancel"), e.getMessage());
                verify(recordingsManager, never()).deleteRecording(any());
            } finally {
                release.countDown();
            }
        }

        @Test
        void aBlankIdIsRefused() {
            assertThrows(IllegalArgumentException.class, () -> complete(tools.delete(" ", RESOURCE_READ)));
            verify(recordingsManager, never()).deleteRecording(any());
        }
    }

    /**
     * A client that renders forms is asked to confirm before anything is removed; one that does not
     * is not asked, and its host still sees the destructive hint.
     */
    @Nested
    class DeleteConfirmation {

        private static final String KEY = "confirmDeletion";
        private static final String NOT_CONFIRMED = "The user did not confirm; nothing was deleted.";
        private static final JsonNode SCHEMA = McpSchemaGenerator.schemaOf(RecordingsMcpTools.RecordingDeletion.class);

        /** A withheld deletion is a status with its reason, in the same record a deletion answers with. */
        private void assertNotConfirmed(McpToolOutcome outcome) {
            JsonNode answer = assertInstanceOf(McpToolResult.class, outcome).structuredContent();
            McpSchemaConformance.assertConforms(answer, SCHEMA);
            assertEquals("NOT_CONFIRMED", answer.path("status").asString());
            assertEquals(NOT_CONFIRMED, answer.path("reason").asString());
            assertEquals(RECORDING_ID, answer.path("recordingId").asString());
            UiLinkRoutes.assertResolves(answer.path("uiLink").asString());
        }

        private McpToolOutcome retried(McpInputResponse.Action action, String contentJson) {
            return tools.delete(RECORDING_ID, answering(ELICITING, KEY, action, contentJson));
        }

        private JsonNode question(McpToolOutcome outcome) {
            McpToolOutcome.InputRequired asked = assertInstanceOf(McpToolOutcome.InputRequired.class, outcome);
            assertEquals(Set.of(KEY), asked.requests().keySet());
            return asked.requests().get(KEY).toJson();
        }

        @Test
        void asksAClientThatCanAnswerBeforeDeletingAnything() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            JsonNode request = question(tools.delete(RECORDING_ID, ELICITING));

            assertEquals("elicitation/create", request.path("method").asString());
            assertEquals("form", request.path("params").path("mode").asString());
            verify(recordingsManager, never()).deleteRecording(any());
        }

        @Test
        void theQuestionNamesTheRecordingItsIdAndItsProfileAndSparesTheHubCopy() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            String message = question(tools.delete(RECORDING_ID, ELICITING)).path("params").path("message").asString();

            assertTrue(message.contains("app.jfr"), message);
            assertTrue(message.contains(RECORDING_ID), message);
            assertTrue(message.contains(PROFILE_ID), message);
            assertTrue(message.contains("hub"), message);
            assertTrue(message.contains("untouched"), message);
        }

        @Test
        void theQuestionSaysWhenThereIsNoProfileToLose() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(false)));

            String message = question(tools.delete(RECORDING_ID, ELICITING)).path("params").path("message").asString();

            assertTrue(message.contains("no profile"), message);
        }

        @Test
        void asksForOneRequiredBoxThatStartsUnchecked() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            JsonNode schema = question(tools.delete(RECORDING_ID, ELICITING)).path("params").path("requestedSchema");

            JsonNode confirm = schema.path("properties").path("confirm");
            assertEquals("boolean", confirm.path("type").asString());
            assertFalse(confirm.path("default").asBoolean(true));
            assertEquals("confirm", schema.path("required").get(0).asString());
            assertEquals(1, schema.path("properties").size());
        }

        @Test
        void deletesOnceTheUserConfirms() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            McpToolOutcome outcome = retried(McpInputResponse.Action.ACCEPT, "{\"confirm\":true}");

            String result = assertInstanceOf(McpToolResult.class, outcome).text();
            verify(recordingsManager).deleteRecording(RECORDING_ID);
            assertTrue(result.contains("\"recordingId\":\"" + RECORDING_ID + "\""), result);
        }

        @Test
        void anUncheckedBoxDeletesNothing() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            McpToolOutcome outcome = retried(McpInputResponse.Action.ACCEPT, "{\"confirm\":false}");

            assertNotConfirmed(outcome);
            verify(recordingsManager, never()).deleteRecording(any());
        }

        @Test
        void aDeclineDeletesNothing() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            McpToolOutcome outcome = retried(McpInputResponse.Action.DECLINE, null);

            assertNotConfirmed(outcome);
            verify(recordingsManager, never()).deleteRecording(any());
        }

        @Test
        void aCancelDeletesNothing() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            McpToolOutcome outcome = retried(McpInputResponse.Action.CANCEL, null);

            assertNotConfirmed(outcome);
            verify(recordingsManager, never()).deleteRecording(any());
        }

        /** The specification: missing information is asked for again rather than treated as an error. */
        @Test
        void anAnswerWithoutTheBoxIsAskedAgain() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            question(retried(McpInputResponse.Action.ACCEPT, "{}"));
            question(retried(McpInputResponse.Action.ACCEPT, "{\"confirm\":\"yes\"}"));
            question(retried(McpInputResponse.Action.ACCEPT, null));
            question(retried(McpInputResponse.Action.ACCEPT, "{\"confirm\":{}}"));
            question(retried(McpInputResponse.Action.ACCEPT, "{\"confirm\":1}"));

            verify(recordingsManager, never()).deleteRecording(any());
        }

        /** Nothing that would be refused is put to the user first. */
        @Test
        void anUnknownRecordingIsRefusedWithoutAsking() {
            when(recordingsManager.findRecording("missing")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> tools.delete("missing", ELICITING));
        }

        @Test
        void aBlankIdIsRefusedWithoutAsking() {
            assertThrows(IllegalArgumentException.class, () -> tools.delete(" ", ELICITING));
            verifyNoInteractions(recordingsManager);
        }

        /** The retry is judged afresh: a parse that started after the question still blocks the delete. */
        @Test
        void theRetryIsCheckedAgainBeforeDeleting() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            question(tools.delete(RECORDING_ID, ELICITING));
            CountDownLatch release = new CountDownLatch(1);
            runRegistry.start(PipelineRunRequest.of(PROFILE_ID, run -> {
                run.beginStage(ProfileInitStages.PARSE);
                awaitQuietly(release);
            }));
            await().atMost(5, SECONDS).until(() -> runRegistry.isRunning(PROFILE_ID));

            try {
                assertThrows(IllegalArgumentException.class,
                        () -> retried(McpInputResponse.Action.ACCEPT, "{\"confirm\":true}"));
                verify(recordingsManager, never()).deleteRecording(any());
            } finally {
                release.countDown();
            }
        }

        /** A client that never declared elicitation cannot withhold consent it was never asked for. */
        @Test
        void aForgedDeclineFromAClientThatCannotBeAskedStillDeletes() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            McpToolOutcome outcome = tools.delete(RECORDING_ID,
                    answering(TASKS, KEY, McpInputResponse.Action.DECLINE, null));

            assertInstanceOf(McpToolResult.class, outcome);
            verify(recordingsManager).deleteRecording(RECORDING_ID);
        }

        @Test
        void aClientThatCannotAnswerIsNotAsked() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            McpToolOutcome outcome = tools.delete(RECORDING_ID, TASKS);

            assertInstanceOf(McpToolResult.class, outcome);
            verify(recordingsManager).deleteRecording(RECORDING_ID);
        }
    }

    @Nested
    class ListRecordings {

        private static final JsonNode SCHEMA = McpSchemaGenerator.schemaOf(RecordingsMcpTools.RecordingPage.class);

        private String text(Integer limit, String cursor) {
            return tools.list(limit, cursor).text();
        }

        private JsonNode page(Integer limit, String cursor) {
            JsonNode page = tools.list(limit, cursor).structuredContent();
            McpSchemaConformance.assertConforms(page, SCHEMA);
            UiLinkRoutes.assertResolves(page.path("uiLink").asString());
            return page;
        }

        @Test
        void showsTheProfileOfAnAnalysedRecording() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(recording(true)));

            String result = text(null, null);

            assertTrue(result.contains(RECORDING_ID));
            assertTrue(result.contains(PROFILE_ID));
            assertEquals(PROFILE_ID, page(null, null).path("recordings").get(0).path("profileId").asString());
        }

        /**
         * A recording uploaded through the UI but never analysed is exactly what this tool is for —
         * it is invisible to profiles_list until someone builds its profile.
         */
        @Test
        void leavesTheProfileColumnEmptyForAnUnanalysedRecording() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(recording(false)));

            String row = text(null, null).lines()
                    .filter(line -> line.contains(RECORDING_ID))
                    .findFirst()
                    .orElseThrow();

            assertEquals("| " + RECORDING_ID + " | app.jfr | JDK | " + START + " |  |", row);
            JsonNode structured = page(null, null).path("recordings").get(0);
            assertTrue(structured.path("profileId").isNull());
            assertEquals(START.toEpochMilli(), structured.path("recordedEpochMs").asLong());
            assertEquals("JDK", structured.path("eventSource").asString());
        }

        /** An unanalysed recording is followed by the call that analyses it, ready to pass on. */
        @Test
        void handsBackTheCallThatAnalysesAnUnanalysedRecording() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(recording(false)));

            JsonNode page = page(null, null);

            JsonNode analyse = page.path("followUp").path("nextTools").get(0);
            assertEquals("recordings_analyzeRecording", analyse.path("tool").asString());
            assertEquals(RECORDING_ID, analyse.path("arguments").path("recordingId").asString());
            assertEquals(1, McpNextToolConformance.assertFollowable(page, recordingsSpecs()));
        }

        @Test
        void saysWhereTheNextPageStartsInsteadOfCuttingSilently() {
            when(recordingsManager.listRecordings()).thenReturn(recordings(5));

            JsonNode page = page(2, null);

            assertEquals(2, page.path("returned").asInt());
            assertEquals(5, page.path("total").asInt());
            assertTrue(page.path("hasMore").asBoolean());
            JsonNode next = page.path("followUp").path("nextTools").get(0);
            assertEquals("recordings_list", next.path("tool").asString());
            assertEquals(page.path("nextCursor").asString(), next.path("arguments").path("cursor").asString());
            assertEquals(2, next.path("arguments").path("limit").asInt());
            assertEquals(2, tableRows(text(2, null)));
            // The next page is named once, by the footer's Next: line, never again in prose.
            String text = text(2, null);
            assertEquals(1, occurrences(text, page.path("nextCursor").asString()), text);
            assertTrue(text.contains("- recordings_list {"), text);
            assertFalse(text.contains("Call recordings_list with nextCursor"), text);
            assertEquals(2, McpNextToolConformance.assertFollowable(page, recordingsSpecs()));
        }

        /** Following nextCursor walks the whole store once, newest first, and ends with hasMore=false. */
        @Test
        void followsTheCursorToTheLastPage() {
            when(recordingsManager.listRecordings()).thenReturn(recordings(5));

            List<String> ids = new ArrayList<>();
            String cursor = null;
            JsonNode page;
            do {
                page = page(2, cursor);
                page.path("recordings").forEach(row -> ids.add(row.path("recordingId").asString()));
                cursor = page.path("nextCursor").isNull() ? null : page.path("nextCursor").asString();
            } while (page.path("hasMore").asBoolean());

            assertEquals(List.of("rec-4", "rec-3", "rec-2", "rec-1", "rec-0"), ids);
            assertTrue(page.path("nextCursor").isNull());
        }

        @Test
        void listsNewestFirstSoPagesAreStable() {
            when(recordingsManager.listRecordings()).thenReturn(recordings(3));

            List<String> ids = text(null, null).lines()
                    .filter(line -> line.startsWith("| rec-"))
                    .map(line -> line.substring(2, line.indexOf(' ', 2)))
                    .toList();

            assertEquals(List.of("rec-2", "rec-1", "rec-0"), ids);
        }

        @Test
        void showsAHundredRowsByDefault() {
            when(recordingsManager.listRecordings()).thenReturn(recordings(150));

            JsonNode page = page(null, null);

            assertEquals(100, page.path("returned").asInt());
            assertTrue(page.path("hasMore").asBoolean());
            assertEquals(100, tableRows(text(null, null)));
        }

        /** A cursor from another tool, or one hand-edited, is refused rather than read as an offset. */
        @Test
        void refusesACursorItDidNotHandOut() {
            String foreign = JeffreyMcpServer.CURSOR.encode(McpCursor.Filters.of("profiles_list", ""), new McpCursor.Offset(1));

            assertThrows(IllegalArgumentException.class, () -> tools.list(null, foreign));
            assertThrows(IllegalArgumentException.class, () -> tools.list(null, "not-a-cursor"));
        }

        /**
         * The cursor continues after the last row it returned, not at a position: a recording deleted
         * from an earlier page between two calls cannot push a row the caller has not seen yet onto
         * that earlier page, where an offset would skip it.
         */
        @Test
        void aDeletionBetweenPagesSkipsNoRow() {
            List<Recording> store = new ArrayList<>(recordings(5));
            when(recordingsManager.listRecordings()).thenAnswer(invocation -> List.copyOf(store));

            JsonNode first = page(2, null);
            store.removeIf(recording -> recording.id().equals("rec-4"));
            JsonNode second = page(2, first.path("nextCursor").asString());

            assertEquals(List.of("rec-4", "rec-3"), ids(first));
            assertEquals(List.of("rec-2", "rec-1"), ids(second));
        }

        /** Two recordings created in the same instant are told apart by their id, so neither is lost. */
        @Test
        void aTieOnTheCreationTimeIsBrokenByTheId() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(
                    new Recording("rec-b", "b.jfr", null, RecordingEventSource.JDK, START, START, START, false, null,
                            null, List.of()),
                    new Recording("rec-a", "a.jfr", null, RecordingEventSource.JDK, START, START, START, false, null,
                            null, List.of()),
                    new Recording("rec-n", "n.jfr", null, RecordingEventSource.JDK, null, START, START, false, null,
                            null, List.of())));

            JsonNode first = page(1, null);
            JsonNode second = page(1, first.path("nextCursor").asString());
            JsonNode third = page(1, second.path("nextCursor").asString());

            assertEquals(List.of("rec-a"), ids(first));
            assertEquals(List.of("rec-b"), ids(second));
            assertEquals(List.of("rec-n"), ids(third));
            assertFalse(third.path("hasMore").asBoolean());
        }

        private List<String> ids(JsonNode page) {
            List<String> ids = new ArrayList<>();
            page.path("recordings").forEach(row -> ids.add(row.path("recordingId").asString()));
            return ids;
        }

        /**
         * One clamp convention across every tool: zero or below is the default, above the cap is the
         * cap. This tool used to read zero as one row.
         */
        @Test
        void aNonPositiveLimitTakesTheDefaultAndALargeOneTheCap() {
            when(recordingsManager.listRecordings()).thenReturn(recordings(1_500));

            assertEquals(100, page(0, null).path("returned").asInt());
            assertEquals(100, page(-4, null).path("returned").asInt());
            assertEquals(1_000, page(5_000, null).path("returned").asInt());
            assertEquals(1_000, tableRows(text(5_000, null)));
        }

        /** The Markdown table ends with the footer rendered from the same record, next calls included. */
        @Test
        void theTableEndsWithTheFooterOfItsRecord() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(recording(false)));

            McpToolResult result = tools.list(null, null);

            assertEquals(1, result.structuredContent().path("followUp").path("nextTools").size());
            MarkdownFooters.assertRenderedFrom(result.text(), result.structuredContent());
        }

        @Test
        void anEmptyStoreEndsWithTheFooterToo() {
            when(recordingsManager.listRecordings()).thenReturn(List.of());

            McpToolResult result = tools.list(null, null);

            MarkdownFooters.assertRenderedFrom(result.text(), result.structuredContent());
        }

        /** An empty store is a status with a reason, the same record with no rows. */
        @Test
        void explainsAnEmptyStore() {
            when(recordingsManager.listRecordings()).thenReturn(List.of());

            JsonNode page = page(null, null);

            assertEquals("EMPTY", page.path("status").asString());
            assertTrue(page.path("reason").asString().contains("empty"));
            assertEquals(0, page.path("recordings").size());
            assertTrue(text(null, null).contains("empty"));
        }

        @Test
        void keepsAPipeInANameOffTheColumnBoundaries() {
            Recording piped = new Recording(
                    RECORDING_ID, "before|after", null, RecordingEventSource.JDK,
                    START, START, START, false, null, null, List.of());
            when(recordingsManager.listRecordings()).thenReturn(List.of(piped));

            String row = text(null, null).lines()
                    .filter(line -> line.contains(RECORDING_ID))
                    .findFirst()
                    .orElseThrow();

            assertFalse(row.contains("before|after"));
            assertTrue(row.contains("before\\|after"));
        }
    }

    /**
     * The three analysis tools answer with one record in every state, which fits the schema they
     * advertise, links the page the user would look at, and hands back the next call ready to pass on.
     */
    @Nested
    class StructuredAnswers {

        private static final JsonNode SCHEMA = McpSchemaGenerator.schemaOf(RecordingsMcpTools.RecordingAnalysis.class);

        private JsonNode conforming(String text) {
            JsonNode answer = Json.readTree(text);
            McpSchemaConformance.assertConforms(answer, SCHEMA);
            UiLinkRoutes.assertResolves(answer.path("uiLink").asString());
            McpNextToolConformance.assertFollowable(answer, recordingsSpecs());
            return answer;
        }

        @Test
        void aFinishedImportIsReadyWithItsOperationAndTheProfilesPage() throws IOException {
            Path file = recordingFile("ready.jfr");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            JsonNode answer = conforming(complete(tools.analyzeFile(file.toString(), null, null, RESOURCE_READ)));

            assertEquals("READY", answer.path("status").asString());
            assertEquals(PROFILE_ID, answer.path("profileId").asString());
            assertTrue(answer.path("uiLink").asString().endsWith("/profiles/" + PROFILE_ID), answer.toString());
            assertEquals("RECORDING_IMPORT", answer.path("operation").path("kind").asString());
            assertEquals("COMPLETED", answer.path("operation").path("status").asString());
            assertEquals(answer.path("operationId").asString(), answer.path("operation").path("operationId").asString());
            JsonNode next = answer.path("followUp").path("nextTools").get(0);
            assertEquals("profiles_summary", next.path("tool").asString());
            assertEquals(PROFILE_ID, next.path("arguments").path("profileId").asString());
        }

        @Test
        void aStillRunningAnalysisPointsAtTheRecordingsPageAndTheStatusCall() {
            BoundedJobs<String, String> jobs = ToolFixtures.jobs(Duration.ofMillis(50));
            RecordingsMcpTools slow = RecordingsMcpToolsFixture.of(recordingsManager, runRegistry, operations, CLOCK)
                    .withJobs(jobs).build();
            CountDownLatch release = new CountDownLatch(1);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenAnswer(invocation -> {
                release.await();
                return PROFILE_ID;
            });
            try {
                JsonNode answer = conforming(complete(slow.analyzeRecording(RECORDING_ID, true, RESOURCE_READ)));

                assertEquals("RUNNING", answer.path("status").asString());
                assertTrue(answer.path("uiLink").asString().endsWith("/recordings"), answer.toString());
                assertEquals("RUNNING", answer.path("operation").path("status").asString());
                JsonNode poll = answer.path("followUp").path("nextTools").get(0);
                assertEquals("recordings_status", poll.path("tool").asString());
                assertEquals(RECORDING_ID, poll.path("arguments").path("recordingId").asString());
            } finally {
                release.countDown();
            }
        }

        @Test
        void aRecordingNobodyAnalysedNamesTheCallThatAnalysesIt() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(false)));

            JsonNode answer = conforming(tools.status(RECORDING_ID).text());

            assertEquals("NOT_STARTED", answer.path("status").asString());
            assertTrue(answer.path("operation").isNull());
            JsonNode analyse = answer.path("followUp").path("nextTools").get(0);
            assertEquals("recordings_analyzeRecording", analyse.path("tool").asString());
            assertFalse(analyse.path("arguments").has("retry"), analyse.toString());
        }

        @Test
        void anInterruptedParseNamesTheRetry() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(false);

            JsonNode answer = conforming(tools.status(RECORDING_ID).text());

            assertEquals("INTERRUPTED", answer.path("status").asString());
            assertEquals(PROFILE_ID, answer.path("profileId").asString());
            assertTrue(answer.path("uiLink").asString().endsWith("/recordings"), answer.toString());
            JsonNode retry = answer.path("followUp").path("nextTools").get(0);
            assertEquals("recordings_analyzeRecording", retry.path("tool").asString());
            assertTrue(retry.path("arguments").path("retry").asBoolean());
        }

        /**
         * A failure this process retains is reported with the attempt behind it and the retry to
         * make, on every poll, until retry=true starts a new attempt.
         */
        @Test
        void aRetainedFailureNamesTheRetryAndCarriesTheFailedAttempt() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(false)));
            when(recordingsManager.analyzeRecording(RECORDING_ID))
                    .thenThrow(new IllegalStateException("recording parser stopped"));
            JsonNode attempted = conforming(complete(tools.analyzeRecording(RECORDING_ID, true, RESOURCE_READ)));
            assertEquals("FAILED", attempted.path("status").asString());

            JsonNode answer = conforming(tools.status(RECORDING_ID).text());

            assertEquals("FAILED", answer.path("status").asString());
            assertEquals("recording parser stopped", answer.path("errorMessage").asString());
            assertEquals("FAILED", answer.path("operation").path("status").asString());
            assertEquals(attempted.path("operationId").asString(), answer.path("operationId").asString());
            assertTrue(answer.path("uiLink").asString().endsWith("/recordings"), answer.toString());
            JsonNode retry = answer.path("followUp").path("nextTools").get(0);
            assertEquals("recordings_analyzeRecording", retry.path("tool").asString());
            assertEquals(RECORDING_ID, retry.path("arguments").path("recordingId").asString());
            assertTrue(retry.path("arguments").path("retry").asBoolean());
            assertEquals(2, McpNextToolConformance.assertFollowable(answer, recordingsSpecs()),
                    "the answer's retry and the operation's own retry: " + answer);
        }

        @Test
        void aReadyProfileIsReportedWithoutAnOperationWhenNoneIsRetained() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true);

            JsonNode answer = conforming(tools.status(RECORDING_ID).text());

            assertEquals("READY", answer.path("status").asString());
            assertTrue(answer.path("operationId").isNull());
            assertTrue(answer.path("operation").isNull());
        }

        /** The next calls go where the installation serves them: without profiles, no summary call. */
        @Test
        void dropsTheSummaryCallWhereTheProfilesFamilyIsNotServed() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true);
            RecordingsMcpTools trimmed = RecordingsMcpToolsFixture.of(recordingsManager, runRegistry, operations, CLOCK)
                    .withAdvertised(new AdvertisedFamilies(Set.of(AdvertisedFamilies.RECORDINGS))).build();

            JsonNode answer = trimmed.status(RECORDING_ID).structuredContent();

            assertEquals(0, answer.path("followUp").path("nextTools").size());
        }
    }

    /**
     * Parsing a large recording outlasts the call, and what the caller does with that answer decides
     * whether they end up with one profile or two.
     */
    @Nested
    class SlowAnalysis {

        /**
         * The manager answers with the profile id the moment it finds a run already in flight for
         * it -- the UI's own Analyze, say. That answer is a promise of a profile, not a profile, and
         * the attempt has to wait for the run rather than hand out a link to a half-parsed one.
         */
        @Test
        void joinsARunAlreadyInFlightRatherThanReportingItDone() throws Exception {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            runRegistry.start(PipelineRunRequest.of(PROFILE_ID, run -> run.runStage(ProfileInitStages.PARSE, () -> {
                entered.countDown();
                awaitIgnoringInterrupts(release);
            })));
            assertTrue(entered.await(5, SECONDS));
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            // The link builder reads the request bound to the calling thread, so the worker binds one
            // the way the fixture does for the test thread.
            CompletableFuture<String> answer = CompletableFuture.supplyAsync(() -> {
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
                try {
                    return complete(tools.analyzeRecording(RECORDING_ID, true, RESOURCE_READ));
                } finally {
                    RequestContextHolder.resetRequestAttributes();
                }
            });
            try {
                assertThrows(TimeoutException.class, () -> answer.get(300, MILLISECONDS),
                        "the attempt must not complete while the run it joined is still parsing");
            } finally {
                release.countDown();
            }
            String result = answer.get(5, SECONDS);
            assertTrue(result.contains("\"profileId\":\"" + PROFILE_ID + "\""), result);
            assertFalse(runRegistry.isRunning(PROFILE_ID));
        }

        @Test
        void handsBackSomethingToPollRatherThanHangingOn() {
            // A budget short enough that the stubbed work cannot beat it, so the timeout path is the
            // one under test rather than a race.
            RecordingsMcpTools slow = RecordingsMcpToolsFixture.of(recordingsManager, runRegistry, operations, CLOCK)
                    .withJobs(ToolFixtures.jobs(Duration.ofMillis(50))).build();
            // Held open by the test rather than by a sleep, so the work is still running when the
            // budget expires without leaving a thread asleep for five seconds after the assertions.
            CountDownLatch release = new CountDownLatch(1);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenAnswer(invocation -> {
                release.await();
                return PROFILE_ID;
            });

            try {
                String result = complete(slow.analyzeRecording(RECORDING_ID, true, RESOURCE_READ));

                assertTrue(result.contains("\"status\":\"RUNNING\""));
                assertTrue(result.contains(RECORDING_ID));
                // Explicitly null rather than absent: the field is always there, and its emptiness is
                // what says there is no profile to reach for yet.
                assertTrue(result.contains("\"profileId\":null"), result);
            } finally {
                release.countDown();
            }
        }

        @Test
        void appliesTheRequestedNameAfterAWaitTimeout() throws IOException {
            Path file = recordingFile("app.jfr");
            BoundedJobs<String, String> jobs = ToolFixtures.jobs(Duration.ofMillis(50));
            RecordingsMcpTools slow = RecordingsMcpToolsFixture.of(recordingsManager, runRegistry, operations, CLOCK)
                    .withJobs(jobs).build();
            CountDownLatch release = new CountDownLatch(1);
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenAnswer(invocation -> {
                release.await();
                return PROFILE_ID;
            });

            assertTrue(complete(slow.analyzeFile(file.toString(), "Checkout run", null, RESOURCE_READ))
                    .contains("\"status\":\"RUNNING\""));
            release.countDown();

            await().atMost(5, SECONDS).untilAsserted(() ->
                    verify(recordingsManager).updateProfileName(PROFILE_ID, "Checkout run"));
        }

        @Test
        void reportsALateFailureWithItsReasonOnEveryPoll() {
            BoundedJobs<String, String> jobs = ToolFixtures.jobs(Duration.ofMillis(50));
            RecordingsMcpTools slow = RecordingsMcpToolsFixture.of(recordingsManager, runRegistry, operations, CLOCK)
                    .withJobs(jobs).build();
            CountDownLatch release = new CountDownLatch(1);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenAnswer(invocation -> {
                release.await();
                throw new IllegalStateException("recording parser stopped");
            });
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(false)));

            assertTrue(complete(slow.analyzeRecording(RECORDING_ID, true, RESOURCE_READ))
                    .contains("\"status\":\"RUNNING\""));
            release.countDown();
            await().atMost(5, SECONDS).until(() -> jobs.outcome(RECORDING_ID).isPresent());

            String first = slow.status(RECORDING_ID).text();
            String second = slow.status(RECORDING_ID).text();
            assertTrue(first.contains("\"status\":\"FAILED\""), first);
            assertTrue(first.contains("recording parser stopped"), first);
            assertTrue(first.contains(RETRY_CALL), first);
            assertTrue(second.contains("recording parser stopped"), second);
        }

        /**
         * The whole point of a status tool: a second analyze call would parse the file again.
         */
        @Test
        void reportsTheProfileOnceItIsReady() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true);

            String result = tools.status(RECORDING_ID).text();

            assertTrue(result.contains(PROFILE_ID));
            verify(recordingsManager, never()).analyzeRecording(RECORDING_ID);
        }

        /**
         * The profile row is inserted before the parse starts, so a recording "having" a profile says
         * nothing about whether that profile works yet. Reporting it as finished hands back an id whose
         * events are still being written -- which reads as success, and is the one answer worse than
         * "not yet".
         */
        @Test
        void reportsAnActiveProfileThatIsNotEnabledYetAsRunning() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            CountDownLatch release = new CountDownLatch(1);
            runRegistry.start(PipelineRunRequest.of(PROFILE_ID, run -> {
                run.beginStage(ProfileInitStages.PARSE);
                awaitQuietly(release);
            }));
            await().atMost(5, SECONDS).until(() -> runRegistry.isRunning(PROFILE_ID));

            try {
                String result = tools.status(RECORDING_ID).text();

                assertTrue(result.contains("\"status\":\"RUNNING\""),
                        "an active profile is still being built: " + result);
                assertTrue(result.contains("still being written"));
            } finally {
                release.countDown();
            }
        }

        /**
         * A boolean cannot say whether a parse is a minute in or nearly done; the stages can.
         */
        @Test
        void carriesTheStagesTheRegistryKnowsAbout() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            CountDownLatch release = new CountDownLatch(1);
            runRegistry.start(PipelineRunRequest.of(PROFILE_ID, run -> {
                run.beginStage(ProfileInitStages.PARSE);
                awaitQuietly(release);
            }));
            await().atMost(5, SECONDS).until(() -> runRegistry.isRunning(PROFILE_ID));

            try {
                String result = tools.status(RECORDING_ID).text();

                assertTrue(result.contains("\"stages\""));
                assertTrue(result.contains(ProfileInitStages.PARSE), result);
            } finally {
                release.countDown();
            }
        }

        @Test
        void keepsAProfileQueuedForAPipelineSlotInTheRunningLifecycle() {
            PipelineRunRegistry<String> serialRegistry = new PipelineRunRegistry<>(
                    ProfileInitStages.DEFINITION,
                    PipelineRunOptions.bounded(1, null),
                    CLOCK);
            RecordingsMcpTools serial =
                    RecordingsMcpToolsFixture.of(recordingsManager, serialRegistry, operations, CLOCK).build();
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            AtomicBoolean queuedWorkStarted = new AtomicBoolean();
            serialRegistry.start(PipelineRunRequest.of("first-profile", run -> {
                firstStarted.countDown();
                awaitQuietly(releaseFirst);
            }));
            await().atMost(5, SECONDS).until(() -> firstStarted.getCount() == 0);
            serialRegistry.start(PipelineRunRequest.of(PROFILE_ID, run -> queuedWorkStarted.set(true)));
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            try {
                String result = serial.status(RECORDING_ID).text();

                assertTrue(result.contains("\"status\":\"RUNNING\""), result);
                assertFalse(queuedWorkStarted.get(), "the profile should still be waiting for the pipeline slot");
            } finally {
                releaseFirst.countDown();
            }
        }

        /**
         * A parse started by a process that has since restarted leaves nothing to report but the state
         * of the profile itself, which is honest rather than empty.
         */
        @Test
        void reportsAnInterruptedAttemptWhenNoRunIsTrackedAfterRestart() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(false);

            String result = tools.status(RECORDING_ID).text();

            assertTrue(result.contains("\"stages\":[]"), result);
            assertTrue(result.contains("\"status\":\"INTERRUPTED\""), result);
            assertTrue(result.contains(RETRY_CALL), result);
        }

        @Test
        void reportsThePipelineFailureForARetainedAttempt() {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> runRegistry.runInline(PipelineRunRequest.of(
                            PROFILE_ID,
                            run -> run.runStage(ProfileInitStages.PARSE, () -> {
                                throw new IllegalStateException("malformed chunk");
                            }))));
            assertEquals("malformed chunk", failure.getMessage());
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            String result = tools.status(RECORDING_ID).text();

            assertTrue(result.contains("\"status\":\"FAILED\""), result);
            assertTrue(result.contains("malformed chunk"), result);
            assertTrue(result.contains("\"id\":\"" + ProfileInitStages.PARSE + "\""), result);
        }

        /**
         * The failure this process remembers is about an attempt it made; the profile can have been
         * built since by another one. Reporting the stale failure over a profile every other tool
         * answers from sends the reader off to build a third copy.
         */
        @Test
        void reportsALiveProfileOverAStaleRetainedFailure() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(false)));
            when(recordingsManager.analyzeRecording(RECORDING_ID))
                    .thenThrow(new IllegalStateException("recording parser stopped"));
            String failure = complete(tools.analyzeRecording(RECORDING_ID, true, RESOURCE_READ));
            assertTrue(failure.contains("recording parser stopped"), failure);

            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true);

            var result = Json.mapper().readTree(tools.status(RECORDING_ID).text());

            // The profile leads; the retained attempt still travels as the operation, where a reader
            // who wants to know what went wrong last time can find it.
            assertEquals(PROFILE_ID, result.path("profileId").asString(), result.toString());
            assertEquals("READY", result.path("status").asString(), "no top-level failure: " + result);
            assertEquals("FAILED", result.path("operation").path("status").asString());
        }

        /**
         * The same precedence, applied to the run this attempt joins rather than to the outcome it
         * retains: a pipeline that failed under this profile's key is not this call's answer while
         * the profile it names is enabled and every other tool reads from it.
         */
        @Test
        void joiningARetainedPipelineFailureStillReturnsAProfileThatWorks() {
            assertThrows(IllegalStateException.class,
                    () -> runRegistry.runInline(PipelineRunRequest.of(
                            PROFILE_ID,
                            run -> run.runStage(ProfileInitStages.PARSE, () -> {
                                throw new IllegalStateException("malformed chunk");
                            }))));
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            profileIs(true);

            String result = complete(tools.analyzeRecording(RECORDING_ID, true, RESOURCE_READ));

            assertTrue(result.contains("\"profileId\":\"" + PROFILE_ID + "\""), result);
        }

        @Test
        void analyzeWithoutRetryReturnsTheLiveProfileInsteadOfTheStaleFailure() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(false)));
            when(recordingsManager.analyzeRecording(RECORDING_ID))
                    .thenThrow(new IllegalStateException("recording parser stopped"));
            complete(tools.analyzeRecording(RECORDING_ID, true, RESOURCE_READ));

            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true);

            var result = Json.mapper().readTree(complete(tools.analyzeRecording(RECORDING_ID, null, RESOURCE_READ)));

            assertEquals(PROFILE_ID, result.path("profileId").asString(), result.toString());
            assertEquals("READY", result.path("status").asString(), "no failure status: " + result);
            assertTrue(result.path("operation").isNull(), "the stale attempt is set aside: " + result);
            verify(recordingsManager, times(1)).analyzeRecording(RECORDING_ID);
        }

        @Test
        void saysNothingIsBuildingOneWhenNothingIs() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(false)));

            String result = tools.status(RECORDING_ID).text();

            assertTrue(result.contains("\"status\":\"NOT_STARTED\""));
        }

        @Test
        void refusesAStatusCallForARecordingThatIsNotThere() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.empty());

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> tools.status(RECORDING_ID).text());

            assertTrue(e.getMessage().contains("No such recording"));
        }
    }

    /**
     * A client that declared the tasks extension is handed a long import or analysis as a task after
     * the task budget instead of waiting out the standard 45 s, and the task answers with the profile
     * a caller that waited would have been given.
     */
    @Nested
    class TaskCapableClient {

        /** The standard 45 s budget, and a task-capable client handed the task after a tenth of a second. */
        private RecordingsMcpTools shortTaskWait() {
            return RecordingsMcpToolsFixture.of(recordingsManager, runRegistry, operations, CLOCK)
                    .withAnswers(new OperationAnswers(SHORT_TASK_WAIT)).build();
        }

        private McpToolOutcome withinTwentySeconds(ThrowingSupplier<McpToolOutcome> call, CountDownLatch release) {
            try {
                return assertTimeout(Duration.ofSeconds(20), call);
            } catch (AssertionError e) {
                release.countDown();
                throw e;
            }
        }

        private String completedAnswer(String taskId) {
            // Polled on this thread: the answer links to the profile, which is built off the bound request.
            await().pollInSameThread().atMost(5, SECONDS).untilAsserted(() -> assertEquals(
                    McpTaskStatus.COMPLETED, operations.task(taskId, kind -> true).status()));
            return assertInstanceOf(McpTaskState.Completed.class,
                    operations.task(taskId, kind -> true).state()).result().text();
        }

        @Test
        void handsBackARunningImportAsATaskWellBeforeTheStandardWait() throws Exception {
            Path file = recordingFile("large.jfr");
            CountDownLatch release = new CountDownLatch(1);
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenAnswer(invocation -> {
                assertTrue(release.await(60, SECONDS));
                return PROFILE_ID;
            });
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true);

            RecordingsMcpTools tools = shortTaskWait();
            McpToolOutcome outcome = withinTwentySeconds(
                    () -> tools.analyzeFile(file.toString(), null, null, TASKS), release);

            String taskId = assertInstanceOf(McpToolOutcome.Deferred.class, outcome).taskId();
            assertEquals(OperationKind.RECORDING_IMPORT, operations.status(taskId).kind());
            assertEquals(McpTaskStatus.WORKING, operations.task(taskId, kind -> true).status());

            release.countDown();
            JsonNode answer = Json.readTree(completedAnswer(taskId));
            assertEquals(PROFILE_ID, answer.path("profileId").asString(), answer.toString());
            assertEquals(RECORDING_ID, answer.path("recordingId").asString(), answer.toString());
            assertEquals(taskId, answer.path("operationId").asString(), answer.toString());
        }

        @Test
        void handsBackARunningAnalysisAsATaskWellBeforeTheStandardWait() {
            CountDownLatch release = new CountDownLatch(1);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(false)));
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenAnswer(invocation -> {
                assertTrue(release.await(60, SECONDS));
                return PROFILE_ID;
            });
            profileIs(true);

            RecordingsMcpTools tools = shortTaskWait();
            McpToolOutcome outcome = withinTwentySeconds(
                    () -> tools.analyzeRecording(RECORDING_ID, null, TASKS), release);

            String taskId = assertInstanceOf(McpToolOutcome.Deferred.class, outcome).taskId();
            assertEquals(OperationKind.RECORDING_ANALYSIS, operations.status(taskId).kind());

            release.countDown();
            JsonNode answer = Json.readTree(completedAnswer(taskId));
            assertEquals(PROFILE_ID, answer.path("profileId").asString(), answer.toString());
            assertEquals(taskId, answer.path("operationId").asString(), answer.toString());
        }

        /**
         * The answer is rendered wherever the finished operation is first seen, which need not be a
         * request thread: the link is built from the base read when the call registered it.
         */
        @Test
        void completesWithTheLinkOfTheCallingRequestWhenNoRequestIsBoundLater() throws Exception {
            Path file = recordingFile("unbound.jfr");
            CountDownLatch release = new CountDownLatch(1);
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenAnswer(invocation -> {
                assertTrue(release.await(60, SECONDS));
                return PROFILE_ID;
            });
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true);
            String expectedLink = UiLinks.profile(PROFILE_ID);
            RecordingsMcpTools tools = shortTaskWait();
            String taskId = assertInstanceOf(McpToolOutcome.Deferred.class, withinTwentySeconds(
                    () -> tools.analyzeFile(file.toString(), null, null, TASKS), release)).taskId();

            RequestContextHolder.resetRequestAttributes();
            release.countDown();

            await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(
                    McpTaskStatus.COMPLETED, operations.task(taskId, kind -> true).status()));
            McpTaskState.Completed completed = assertInstanceOf(McpTaskState.Completed.class,
                    operations.task(taskId, kind -> true).state());
            assertEquals(expectedLink, Json.readTree(completed.result().text()).path("uiLink").asString());
        }

        /**
         * An import that lands inside the task budget is answered at once, and what the task holds
         * for that operation is the very same text.
         */
        @Test
        void answersAnImportThatLandsInsideTheTaskBudgetWithTheTasksOwnAnswer() throws IOException {
            Path file = recordingFile("small.jfr");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true);

            McpToolOutcome outcome = tools.analyzeFile(file.toString(), null, null, TASKS);

            String answer = assertInstanceOf(McpToolResult.class, outcome).text();
            String taskId = Json.readTree(answer).path("operationId").asString();
            assertEquals(answer, completedAnswer(taskId));
        }

        /** Without the extension the call still waits, and still answers with the operation to poll. */
        @Test
        void aClientWithoutTheExtensionStillGetsTheOperationToPoll() {
            RecordingsMcpTools bounded = RecordingsMcpToolsFixture.of(recordingsManager, runRegistry, operations, CLOCK)
                    .withJobs(ToolFixtures.jobs(Duration.ofMillis(50))).build();
            CountDownLatch release = new CountDownLatch(1);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(false)));
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenAnswer(invocation -> {
                assertTrue(release.await(60, SECONDS));
                return PROFILE_ID;
            });

            try {
                McpToolOutcome outcome = bounded.analyzeRecording(RECORDING_ID, null, NO_TASKS);

                JsonNode answer = Json.readTree(assertInstanceOf(McpToolResult.class, outcome).text());
                assertEquals("RUNNING", answer.path("status").asString(), answer.toString());
                assertFalse(answer.path("operationId").asString().isBlank(), answer.toString());
            } finally {
                release.countDown();
            }
        }
    }

    /** What the recordings, operations and profiles families advertise, for following a next call. */
    private List<McpToolSpec> recordingsSpecs() {
        return CatalogueSpecs.of(
                CatalogueSpecs.served(tools, "recordings"),
                CatalogueSpecs.served(new OperationsMcpTools(operations, kind -> true), "operations"),
                CatalogueSpecs.profileScoped(ProfileMcpTools.class, "profiles"));
    }

    private static List<Recording> recordings(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new Recording("rec-" + i, "app-" + i + ".jfr", null, RecordingEventSource.JDK,
                        START.plusSeconds(i), START, START, false, null, null, List.of()))
                .toList();
    }

    private static long tableRows(String table) {
        return table.lines().filter(line -> line.startsWith("| rec-")).count();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String withRequestContext(Supplier<String> call) {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
        try {
            return call.get();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        while (latch.getCount() != 0) {
            try {
                latch.await();
            } catch (InterruptedException ignored) {
                // The run stands in for a parser that does not stop on a cancellation request.
            }
        }
    }


    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}
