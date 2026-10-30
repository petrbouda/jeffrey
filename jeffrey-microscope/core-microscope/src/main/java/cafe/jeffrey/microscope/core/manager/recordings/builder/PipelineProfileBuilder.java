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

package cafe.jeffrey.microscope.core.manager.recordings.builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.ProfileRepository;
import cafe.jeffrey.microscope.core.manager.recordings.MicroscopeProfileCleanup;
import cafe.jeffrey.profile.ProfileInitializer;
import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.provider.profile.api.RecordingSources;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;
import cafe.jeffrey.storage.recording.api.file.FileCategory;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import cafe.jeffrey.storage.recording.api.file.RecordingStorageLayout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Builds a profile by parsing the recording's events through the profile-initialization pipeline: a
 * JFR recording, and the stack-sample imports (pprof, OpenTelemetry) that go through the same stages.
 * The request waits for the run, and the recordings list follows its stages meanwhile.
 */
public final class PipelineProfileBuilder implements ProfileBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineProfileBuilder.class);

    private final Clock clock;
    private final Path recordingsDir;
    private final RecordingInformationParser recordingInformationParser;
    private final ProfileInitializer profileInitializer;
    private final MicroscopeCoreRepositories localCoreRepositories;
    private final MicroscopeProfileCleanup profileCleanup;
    private final PipelineRunRegistry<String> runRegistry;

    public PipelineProfileBuilder(
            Clock clock,
            Path recordingsDir,
            RecordingInformationParser recordingInformationParser,
            ProfileInitializer profileInitializer,
            MicroscopeCoreRepositories localCoreRepositories,
            MicroscopeProfileCleanup profileCleanup,
            PipelineRunRegistry<String> runRegistry) {

        this.clock = clock;
        this.recordingsDir = recordingsDir;
        this.recordingInformationParser = recordingInformationParser;
        this.profileInitializer = profileInitializer;
        this.localCoreRepositories = localCoreRepositories;
        this.profileCleanup = profileCleanup;
        this.runRegistry = runRegistry;
    }

    @Override
    public Optional<String> reuse(Recording recording) {
        String profileId = recording.profileId();
        // A second request while initialization is active joins the attempt already represented
        // by this row. Deleting it would pull the database out from under the parser.
        if (runRegistry.isRunning(profileId)) {
            return Optional.of(profileId);
        }

        ProfileRepository existing = localCoreRepositories.newProfileRepository(profileId);
        if (existing.find().map(ProfileInfo::enabled).orElse(false)) {
            return Optional.of(profileId);
        }

        // Disabled with no live run means either a terminal failed attempt retained for diagnosis,
        // or work interrupted by a process restart. An explicit Analyze is the retry boundary:
        // remove that attempt here, then build a fresh profile id.
        profileCleanup.deleteProfile(profileId);
        return Optional.empty();
    }

    @Override
    public String build(Recording recording, String profileName) {
        // Every file of the recording, which for a session is every chunk it rolled: taking the first
        // of them would analyse a few minutes of a run that lasted hours, with nothing anywhere saying
        // the rest was dropped.
        List<RecordingFile> recordingFiles = recording.files().stream()
                .filter(file -> file.recordingFileType().fileCategory() == FileCategory.RECORDING)
                .toList();

        if (recordingFiles.isEmpty()) {
            throw Exceptions.internal("Recording has no recording files: %s".formatted(recording.id()));
        }

        List<Path> filePaths = recordingFiles.stream()
                .map(file -> RecordingStorageLayout.storagePath(recordingsDir, file.recordingId(), file.filename()))
                .toList();

        for (Path filePath : filePaths) {
            if (!Files.exists(filePath)) {
                throw Exceptions.internal("Recording file does not exist: %s".formatted(filePath));
            }
        }

        RecordingSources sources = new RecordingSources(filePaths);
        String profileId = IDGenerator.generate();
        Instant createdAt = clock.instant();

        RecordingInformation recordingInfo = resolveRecordingInformation(recording, sources);

        ProfileInfo profileInfo = new ProfileInfo(
                profileId, null, null, profileName,
                recordingInfo.eventSource(),
                recordingInfo.recordingStartedAt(),
                recordingInfo.recordingFinishedAt(),
                createdAt, false, false, recording.id());

        // Insert the profile row before initializing. The recordings list reaches a run's progress
        // through the recording's profile, so a row that appears only once the pipeline has finished
        // leaves the whole run invisible -- the card can say nothing but "Initializing..." for as
        // long as it takes.
        ProfileRepository profileRepository = localCoreRepositories.newProfileRepository(profileId);
        profileRepository.insert(new ProfileRepository.InsertProfile(
                profileName,
                recordingInfo.eventSource(), createdAt,
                recording.id(),
                recordingInfo.recordingStartedAt(),
                recordingInfo.recordingFinishedAt()));

        try {
            profileInitializer.initialize(profileInfo, sources);
        } catch (RuntimeException e) {
            // Keep the disabled row. It is the durable recording-to-profile link through which the
            // in-memory pipeline can report the failed attempt, and after a restart it is the evidence
            // that an initialization was interrupted rather than never requested. A later explicit
            // Analyze removes it before retrying.
            Notifications.of(NotificationType.PROFILE_ANALYSIS_FAILED)
                    .attribute("recordingId", recording.id())
                    .attribute("profileId", profileId)
                    .errorType(e)
                    .emit();

            throw e;
        }
        profileRepository.enableProfile(createdAt);

        LOG.debug("Profile built from recording: recordingId={} profileId={}", recording.id(), profileId);
        return profileId;
    }

    @Override
    public PipelineProgress progress(String profileId) {
        return runRegistry.progress(profileId);
    }

    /**
     * Prefers the recording metadata persisted at upload time (event source + profiling start/end)
     * and re-parses the JFR file only when any of them is missing — e.g. when the metadata parse
     * failed during the upload.
     */
    private RecordingInformation resolveRecordingInformation(Recording recording, RecordingSources sources) {
        boolean persistedInfoComplete = recording.eventSource() != null
                && recording.eventSource() != RecordingEventSource.UNKNOWN
                && recording.recordingStartedAt() != null
                && recording.recordingFinishedAt() != null;

        if (persistedInfoComplete) {
            return new RecordingInformation(
                    sources.files().stream().mapToLong(FileSystemUtils::size).sum(),
                    recording.eventSource(),
                    recording.recordingStartedAt(),
                    recording.recordingFinishedAt());
        }
        return recordingInformationParser.provide(sources);
    }
}
