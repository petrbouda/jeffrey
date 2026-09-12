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

package cafe.jeffrey.microscope.core.manager.recordings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.ProfileRepository;
import cafe.jeffrey.microscope.persistence.api.RecordingRepository;
import cafe.jeffrey.profile.ProfileInitializer;
import cafe.jeffrey.profile.ProfileInitStages;
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRequest;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.RecordingFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

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
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The ordering that decides whether the recordings list can show an initialization while it runs:
 * the list reaches a run's progress through the recording's profile, so the profile row has to
 * exist before the pipeline starts, not after it finishes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileRecordingsManagerAnalyzeTest {

    private static final Instant NOW = Instant.parse("2026-05-23T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String RECORDING_ID = "recording-1";
    private static final String FILENAME = "app.jfr";

    @Mock
    private RecordingsCoreManager core;
    @Mock
    private MicroscopeJeffreyDirs jeffreyDirs;
    @Mock
    private RecordingInformationParser recordingInformationParser;
    @Mock
    private ProfileInitializer profileInitializer;
    @Mock
    private ProfileManager.Factory profileManagerFactory;
    @Mock
    private MicroscopeCoreRepositories localCoreRepositories;
    @Mock
    private MicroscopeProfileCleanup profileCleanup;
    @Mock
    private RecordingRepository recordingRepository;
    @Mock
    private ProfileRepository profileRepository;

    private PipelineRunRegistry<String> runRegistry;

    @TempDir
    private Path recordingsDir;

    private ProfileRecordingsManager manager;

    @BeforeEach
    void setUp() throws IOException {
        Files.createFile(recordingsDir.resolve(RECORDING_ID + "-" + FILENAME));

        when(localCoreRepositories.newRecordingRepository(null)).thenReturn(recordingRepository);
        when(localCoreRepositories.newProfileRepository(any())).thenReturn(profileRepository);
        when(recordingRepository.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording()));
        runRegistry = new PipelineRunRegistry<>(
                ProfileInitStages.DEFINITION, PipelineRunOptions.unbounded(), FIXED_CLOCK);

        manager = new ProfileRecordingsManager(
                core,
                FIXED_CLOCK,
                jeffreyDirs,
                recordingsDir,
                recordingInformationParser,
                profileInitializer,
                profileManagerFactory,
                localCoreRepositories,
                profileCleanup,
                runRegistry);
    }

    private static Recording recording() {
        RecordingFile file = new RecordingFile(
                "file-1", RECORDING_ID, FILENAME, SupportedRecordingFile.JFR, NOW, 1024L);

        return new Recording(
                RECORDING_ID, FILENAME, null, null,
                RecordingEventSource.JDK,
                NOW,
                NOW.minusSeconds(60), NOW,
                false, null, null,
                List.of(file));
    }

    @Nested
    class SuccessfulAnalysis {

        @Test
        void insertsTheProfileRowBeforeInitializingIt() {
            manager.analyzeRecording(RECORDING_ID);

            var order = inOrder(profileRepository, profileInitializer);
            order.verify(profileRepository).insert(any());
            order.verify(profileInitializer).initialize(any(), any(), any());
            order.verify(profileRepository).enableProfile(any());
        }

        @Test
        void returnsTheProfileTheRowWasCreatedFor() {
            String profileId = manager.analyzeRecording(RECORDING_ID);

            verify(localCoreRepositories).newProfileRepository(profileId);
        }

        @Test
        void returnsAnExistingEnabledProfileWithoutReplacingIt() {
            String existingProfileId = "existing-profile";
            when(recordingRepository.findRecording(RECORDING_ID))
                    .thenReturn(Optional.of(recordingWithProfile(existingProfileId)));
            when(profileRepository.find()).thenReturn(Optional.of(profileInfo(existingProfileId, true)));

            assertEquals(existingProfileId, manager.analyzeRecording(RECORDING_ID));
            verify(profileCleanup, never()).deleteProfile(existingProfileId);
            verify(profileInitializer, never()).initialize(any(), any(), any());
        }

        @Test
        void joinsAConcurrentRequestWhileTheProfileRowIsBeingInserted() throws Exception {
            CountDownLatch rowInsertReached = new CountDownLatch(1);
            CountDownLatch releaseRowInsert = new CountDownLatch(1);
            doAnswer(invocation -> {
                rowInsertReached.countDown();
                awaitQuietly(releaseRowInsert);
                return null;
            }).when(profileRepository).insert(any());
            when(recordingRepository.findRecording(RECORDING_ID))
                    .thenReturn(Optional.of(recording()), Optional.of(recording()));

            CompletableFuture<String> first = CompletableFuture.supplyAsync(
                    () -> manager.analyzeRecording(RECORDING_ID));
            assertTrue(rowInsertReached.await(5, SECONDS));
            CompletableFuture<String> second = concurrentAnalysis();
            awaitUntilWaiting(second);
            releaseRowInsert.countDown();

            assertEquals(first.get(5, SECONDS), second.get(5, SECONDS));
            verify(recordingRepository, times(1)).findRecording(RECORDING_ID);
            verify(profileInitializer).initialize(any(), any(), any());
        }

        @Test
        void joinsAConcurrentRequestWhileTheProfileIsBeingEnabled() throws Exception {
            CountDownLatch enableReached = new CountDownLatch(1);
            CountDownLatch releaseEnable = new CountDownLatch(1);
            doAnswer(invocation -> {
                enableReached.countDown();
                awaitQuietly(releaseEnable);
                return null;
            }).when(profileRepository).enableProfile(any());
            when(recordingRepository.findRecording(RECORDING_ID)).thenReturn(
                    Optional.of(recording()),
                    Optional.of(recordingWithProfile("profile-being-enabled")));
            when(profileRepository.find())
                    .thenReturn(Optional.of(profileInfo("profile-being-enabled", false)));

            CompletableFuture<String> first = CompletableFuture.supplyAsync(
                    () -> manager.analyzeRecording(RECORDING_ID));
            assertTrue(enableReached.await(5, SECONDS));
            CompletableFuture<String> second = concurrentAnalysis();
            awaitUntilWaiting(second);
            releaseEnable.countDown();

            assertEquals(first.get(5, SECONDS), second.get(5, SECONDS));
            verify(recordingRepository, times(1)).findRecording(RECORDING_ID);
            verify(profileCleanup, never()).deleteProfile(any());
            verify(profileInitializer).initialize(any(), any(), any());
        }
    }

    @Nested
    class FailedAnalysis {

        @Test
        void retainsTheDisabledProfileRowSoTheFailedAttemptCanBeInspected() {
            RuntimeException failure = new RuntimeException("parse failed");
            when(profileInitializer.initialize(any(), any(), any())).thenThrow(failure);

            RuntimeException thrown = assertThrows(
                    RuntimeException.class, () -> manager.analyzeRecording(RECORDING_ID));

            assertEquals(failure, thrown);
            verify(profileRepository, never()).delete();
            verify(profileRepository, never()).enableProfile(any());
        }

        @Test
        void removesAFailedAttemptBeforeRetrying() {
            String failedProfileId = "failed-profile";
            Recording failedRecording = recordingWithProfile(failedProfileId);
            when(recordingRepository.findRecording(RECORDING_ID))
                    .thenReturn(Optional.of(failedRecording), Optional.of(recording()));
            assertThrows(IllegalStateException.class,
                    () -> runRegistry.runInline(PipelineRunRequest.of(failedProfileId,
                            run -> {
                                throw new IllegalStateException("parse failed");
                            })));

            String retriedProfileId = manager.analyzeRecording(RECORDING_ID);

            verify(profileCleanup).deleteProfile(failedProfileId);
            verify(localCoreRepositories).newProfileRepository(retriedProfileId);
        }

        @Test
        void removesAnOrphanedDisabledAttemptAfterRestartBeforeRetrying() {
            String orphanedProfileId = "orphaned-profile";
            when(recordingRepository.findRecording(RECORDING_ID))
                    .thenReturn(Optional.of(recordingWithProfile(orphanedProfileId)), Optional.of(recording()));
            when(profileRepository.find()).thenReturn(Optional.of(profileInfo(orphanedProfileId, false)));

            String retriedProfileId = manager.analyzeRecording(RECORDING_ID);

            verify(profileCleanup).deleteProfile(orphanedProfileId);
            verify(localCoreRepositories).newProfileRepository(retriedProfileId);
        }

        @Test
        void doesNotDeleteAnAttemptWhosePipelineIsStillActive() {
            String activeProfileId = "active-profile";
            when(recordingRepository.findRecording(RECORDING_ID))
                    .thenReturn(Optional.of(recordingWithProfile(activeProfileId)));
            CountDownLatch release = new CountDownLatch(1);
            runRegistry.start(PipelineRunRequest.of(activeProfileId, run -> awaitQuietly(release)));
            await().atMost(5, SECONDS).until(() -> runRegistry.isRunning(activeProfileId));

            try {
                assertEquals(activeProfileId, manager.analyzeRecording(RECORDING_ID));
                verify(profileCleanup, never()).deleteProfile(activeProfileId);
            } finally {
                release.countDown();
            }
        }
    }

    private static Recording recordingWithProfile(String profileId) {
        Recording base = recording();
        return new Recording(
                base.id(), base.recordingName(), base.projectId(), base.groupId(), base.eventSource(),
                base.createdAt(), base.recordingStartedAt(), base.recordingFinishedAt(),
                true, profileId, base.recordingName(), base.files());
    }

    private static ProfileInfo profileInfo(String profileId, boolean enabled) {
        return new ProfileInfo(
                profileId, null, null, FILENAME, RecordingEventSource.JDK,
                NOW.minusSeconds(60), NOW, NOW, enabled, false, RECORDING_ID);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private CompletableFuture<String> concurrentAnalysis() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            worker.set(Thread.currentThread());
            started.countDown();
            return manager.analyzeRecording(RECORDING_ID);
        });
        assertTrue(started.await(5, SECONDS));
        await().atMost(5, SECONDS).until(() -> worker.get().getState() == Thread.State.WAITING);
        return future;
    }

    private static void awaitUntilWaiting(CompletableFuture<String> future) {
        await().during(Duration.ofMillis(50)).atMost(5, SECONDS).until(() -> !future.isDone());
    }
}
