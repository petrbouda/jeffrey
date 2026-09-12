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

import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRequest;
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.ProfileInitStages;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import cafe.jeffrey.shared.common.Json;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingsMcpToolsTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
    private static final String RECORDING_ID = "rec-1";
    private static final String PROFILE_ID = "prof-1";

    @Mock
    RecordingsManager recordingsManager;

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);

    private RecordingsMcpTools tools;
    private PipelineRunRegistry<String> runRegistry;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        runRegistry = new PipelineRunRegistry<>(
                ProfileInitStages.DEFINITION, PipelineRunOptions.unbounded(), CLOCK);
        tools = new RecordingsMcpTools(recordingsManager, runRegistry);
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
                RECORDING_ID, "app.jfr", null, null, RecordingEventSource.JDK,
                START, START, START.plusSeconds(60),
                hasProfile, hasProfile ? PROFILE_ID : null, hasProfile ? "app.jfr" : null, List.of());
    }

    @Test
    void importedProfileExposesItsOperationIdentityAndCanonicalStatus() throws IOException {
        Path file = recordingFile("operation.jfr");
        when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
        when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
        when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
        String result = tools.analyzeFile(file.toString(), null);
        assertTrue(result.contains("\"operationId\""), result);
        assertTrue(result.contains("\"operation\""), result);
        assertTrue(result.contains("\"status\":\"completed\""), result);
    }

    @Test
    void copyingIsBoundedAndCancellationPreventsAnalysisAfterTheCopyReturns() throws Exception {
        Path file = recordingFile("large.jfr");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        McpOperationRegistry operations = new McpOperationRegistry();
        RecordingsMcpTools bounded = new RecordingsMcpTools(recordingsManager, runRegistry,
                new BoundedJobs<>(Duration.ofMillis(50)), operations);
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
                () -> withRequestContext(() -> bounded.analyzeFile(file.toString(), null)));
        String operationId;
        try {
            assertTrue(entered.await(5, SECONDS));
            String response = call.get(1, SECONDS);
            operationId = Json.mapper().readTree(response).path("operationId").asString();
            assertFalse(operationId.isBlank());
            assertEquals("cancel_requested", operations.cancel(operationId, kind -> true).status());
        } finally {
            release.countDown();
        }
        await().atMost(5, SECONDS).until(() -> operations.status(operationId).status().equals("cancelled"));
        verify(recordingsManager, never()).analyzeRecording(anyString());
    }

    @Test
    void immediateImportFailureStillReturnsAnOperationThatCanBeInspected() throws IOException {
        Path file = recordingFile("broken.jfr");
        when(recordingsManager.importRecordingFromPath(file)).thenThrow(new IllegalStateException("copy failed"));
        var result = Json.mapper().readTree(tools.analyzeFile(file.toString(), null));
        assertFalse(result.path("operationId").asString().isBlank());
        assertEquals("failed", result.path("status").asString());
        assertEquals("copy failed", result.path("error").path("message").asString());
        verify(recordingsManager, never()).analyzeRecording(anyString());
    }

    @Test
    void cancellationAfterAnalysisSucceedsPreservesTheProfileAndAcceptedName() throws Exception {
        Path file = recordingFile("finishing.jfr");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        McpOperationRegistry operations = new McpOperationRegistry();
        RecordingsMcpTools bounded = new RecordingsMcpTools(recordingsManager, runRegistry,
                new BoundedJobs<>(Duration.ofMillis(50)), operations);
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
        String operationId = Json.mapper().readTree(bounded.analyzeFile(file.toString(), "Accepted name"))
                .path("operationId").asString();
        try {
            assertTrue(entered.await(5, SECONDS));
            assertEquals("cancel_requested", operations.cancel(operationId, kind -> true).status());
        } finally {
            release.countDown();
        }
        await().atMost(5, SECONDS).until(() -> operations.status(operationId).finishedAt() != null);
        assertEquals("completed", operations.status(operationId).status());
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
        assertTrue(tools.analyzeRecording(RECORDING_ID).contains(PROFILE_ID));
        when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(false)));
        ProfileManager replacement = mock(ProfileManager.class);
        when(replacement.info()).thenReturn(new ProfileInfo(
                "replacement-profile", null, null, "app.jfr", RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, RECORDING_ID));
        when(recordingsManager.profile("replacement-profile")).thenReturn(Optional.of(replacement));
        String response = tools.analyzeRecording(RECORDING_ID);
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

            String result = tools.analyzeFile(file.toString(), null);

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

            assertTrue(tools.analyzeFile(file.toString(), null).contains("\"profileId\":\"" + PROFILE_ID + "\""));
        }

        @Test
        void renamesTheProfileWhenAskedTo() throws IOException {
            Path file = recordingFile("app.jfr");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true, "Checkout run");

            String result = tools.analyzeFile(file.toString(), "Checkout run");

            verify(recordingsManager).updateProfileName(PROFILE_ID, "Checkout run");
            assertTrue(result.contains("Checkout run"));
        }

        @Test
        void joinedCallersSeeTheNameAppliedByTheAttemptTheyJoined() throws Exception {
            Path file = recordingFile("app.jfr");
            BoundedJobs<String, String> jobs = new BoundedJobs<>(Duration.ofSeconds(5));
            RecordingsMcpTools concurrent = new RecordingsMcpTools(recordingsManager, runRegistry, jobs);
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
                    () -> withRequestContext(() -> concurrent.analyzeFile(file.toString(), "First name")));
            assertTrue(analysisReached.await(5, SECONDS));
            CompletableFuture<String> second = CompletableFuture.supplyAsync(
                    () -> withRequestContext(() -> concurrent.analyzeFile(file.toString(), "Second name")));
            await().during(Duration.ofMillis(50)).atMost(5, SECONDS).until(() -> !second.isDone());
            releaseAnalysis.countDown();

            assertTrue(first.get(5, SECONDS).contains("\"name\":\"First name\""));
            assertTrue(second.get(5, SECONDS).contains("\"name\":\"First name\""));
            verify(recordingsManager).updateProfileName(PROFILE_ID, "First name");
            verify(recordingsManager, never()).updateProfileName(PROFILE_ID, "Second name");
        }

        @Test
        void leavesTheProfileNameAloneWhenNoneWasGiven() throws IOException {
            Path file = recordingFile("app.jfr");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            tools.analyzeFile(file.toString(), "   ");

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
                    () -> tools.analyzeFile("target/app.jfr", null));

            assertTrue(e.getMessage().contains("absolute"));
            verifyNoInteractions(recordingsManager);
        }

        @Test
        void rejectsAMissingFileAndSaysWhoseFilesystemItLookedOn() {
            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                    () -> tools.analyzeFile(tempDir.resolve("nowhere.jfr").toString(), null));

            assertTrue(e.getMessage().contains("Jeffrey"));
            verifyNoInteractions(recordingsManager);
        }

        @Test
        void rejectsAFileTypeJeffreyCannotParse() throws IOException {
            Path file = recordingFile("notes.txt");

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                    () -> tools.analyzeFile(file.toString(), null));

            assertTrue(e.getMessage().contains("Unsupported"));
            verifyNoInteractions(recordingsManager);
        }

        @Test
        void rejectsAnAbsentPath() {
            assertThrows(IllegalArgumentException.class, () -> tools.analyzeFile(null, null));
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

            assertTrue(tools.analyzeFile(file.toString(), null).contains(PROFILE_ID));
        }
    }

    @Nested
    class AnalyzeRecording {

        @Test
        void analyzesARecordingThatIsAlreadyStored() {
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            assertTrue(tools.analyzeRecording(RECORDING_ID).contains(PROFILE_ID));
            verify(recordingsManager, never()).importRecordingFromPath(any());
        }

        @Test
        void rejectsABlankRecordingId() {
            assertThrows(IllegalArgumentException.class, () -> tools.analyzeRecording("  "));
            verifyNoInteractions(recordingsManager);
        }
    }

    @Nested
    class ListRecordings {

        @Test
        void showsTheProfileOfAnAnalysedRecording() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(recording(true)));

            String result = tools.list();

            assertTrue(result.contains(RECORDING_ID));
            assertTrue(result.contains(PROFILE_ID));
        }

        /**
         * A recording uploaded through the UI but never analysed is exactly what this tool is for —
         * it is invisible to profiles_list until someone builds its profile.
         */
        @Test
        void leavesTheProfileColumnEmptyForAnUnanalysedRecording() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(recording(false)));

            String row = tools.list().lines()
                    .filter(line -> line.contains(RECORDING_ID))
                    .findFirst()
                    .orElseThrow();

            assertEquals("| " + RECORDING_ID + " | app.jfr | JDK | " + START + " |  |", row);
        }

        @Test
        void explainsAnEmptyStore() {
            when(recordingsManager.listRecordings()).thenReturn(List.of());

            assertTrue(tools.list().contains("empty"));
        }

        @Test
        void keepsAPipeInANameOffTheColumnBoundaries() {
            Recording piped = new Recording(
                    RECORDING_ID, "before|after", null, null, RecordingEventSource.JDK,
                    START, START, START, false, null, null, List.of());
            when(recordingsManager.listRecordings()).thenReturn(List.of(piped));

            String row = tools.list().lines()
                    .filter(line -> line.contains(RECORDING_ID))
                    .findFirst()
                    .orElseThrow();

            assertFalse(row.contains("before|after"));
            assertTrue(row.contains("before/after"));
        }
    }

    /**
     * Parsing a large recording outlasts the call, and what the caller does with that answer decides
     * whether they end up with one profile or two.
     */
    @Nested
    class SlowAnalysis {

        @Test
        void handsBackSomethingToPollRatherThanHangingOn() {
            // A budget short enough that the stubbed work cannot beat it, so the timeout path is the
            // one under test rather than a race.
            RecordingsMcpTools slow = new RecordingsMcpTools(
                    recordingsManager, runRegistry, new BoundedJobs<>(Duration.ofMillis(50)));
            // Held open by the test rather than by a sleep, so the work is still running when the
            // budget expires without leaving a thread asleep for five seconds after the assertions.
            CountDownLatch release = new CountDownLatch(1);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenAnswer(invocation -> {
                release.await();
                return PROFILE_ID;
            });

            try {
                String result = slow.analyzeRecording(RECORDING_ID);

                assertTrue(result.contains("\"status\":\"running\""));
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
            BoundedJobs<String, String> jobs = new BoundedJobs<>(Duration.ofMillis(50));
            RecordingsMcpTools slow = new RecordingsMcpTools(recordingsManager, runRegistry, jobs);
            CountDownLatch release = new CountDownLatch(1);
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenAnswer(invocation -> {
                release.await();
                return PROFILE_ID;
            });

            assertTrue(slow.analyzeFile(file.toString(), "Checkout run")
                    .contains("\"status\":\"running\""));
            release.countDown();

            await().atMost(5, SECONDS).untilAsserted(() ->
                    verify(recordingsManager).updateProfileName(PROFILE_ID, "Checkout run"));
        }

        @Test
        void reportsALateFailureWithItsReasonOnEveryPoll() {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(Duration.ofMillis(50));
            RecordingsMcpTools slow = new RecordingsMcpTools(recordingsManager, runRegistry, jobs);
            CountDownLatch release = new CountDownLatch(1);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenAnswer(invocation -> {
                release.await();
                throw new IllegalStateException("recording parser stopped");
            });
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(false)));

            assertTrue(slow.analyzeRecording(RECORDING_ID).contains("\"status\":\"running\""));
            release.countDown();
            await().atMost(5, SECONDS).until(() -> jobs.outcome(RECORDING_ID).isPresent());

            String first = slow.status(RECORDING_ID);
            String second = slow.status(RECORDING_ID);
            assertTrue(first.contains("\"status\":\"failed\""), first);
            assertTrue(first.contains("recording parser stopped"), first);
            assertTrue(second.contains("recording parser stopped"), second);
        }

        /**
         * The whole point of a status tool: a second analyze call would parse the file again.
         */
        @Test
        void reportsTheProfileOnceItIsReady() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true);

            String result = tools.status(RECORDING_ID);

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
                String result = tools.status(RECORDING_ID);

                assertTrue(result.contains("\"status\":\"running\""),
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
                String result = tools.status(RECORDING_ID);

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
            RecordingsMcpTools serial = new RecordingsMcpTools(recordingsManager, serialRegistry);
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
                String result = serial.status(RECORDING_ID);

                assertTrue(result.contains("\"status\":\"running\""), result);
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

            String result = tools.status(RECORDING_ID);

            assertTrue(result.contains("\"stages\":[]"), result);
            assertTrue(result.contains("\"status\":\"interrupted\""), result);
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

            String result = tools.status(RECORDING_ID);

            assertTrue(result.contains("\"status\":\"failed\""), result);
            assertTrue(result.contains("malformed chunk"), result);
            assertTrue(result.contains("\"id\":\"" + ProfileInitStages.PARSE + "\""), result);
        }

        @Test
        void saysNothingIsBuildingOneWhenNothingIs() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(false)));

            String result = tools.status(RECORDING_ID);

            assertTrue(result.contains("\"status\":\"not_started\""));
        }

        @Test
        void refusesAStatusCallForARecordingThatIsNotThere() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.empty());

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> tools.status(RECORDING_ID));

            assertTrue(e.getMessage().contains("No such recording"));
        }
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

}
