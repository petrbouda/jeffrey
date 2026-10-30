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

import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.core.manager.recordings.MicroscopeProfileCleanup;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.ProfileRepository;
import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.common.pipeline.PipelineState;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpFiles;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import cafe.jeffrey.storage.recording.api.file.RecordingStorageLayout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Builds a heap dump's profile: copies the dump into the profile's directory, enables the profile at
 * once, and starts the heap-dump pipeline in the background. The request returns as soon as the run
 * is registered; the recordings list polls its stages.
 */
public final class HeapDumpProfileBuilder implements ProfileBuilder {

    private final Clock clock;
    private final MicroscopeJeffreyDirs jeffreyDirs;
    private final Path recordingsDir;
    private final MicroscopeCoreRepositories localCoreRepositories;
    private final ProfileManager.Factory profileManagerFactory;
    private final HeapDumpInitService heapDumpInitService;
    private final MicroscopeProfileCleanup profileCleanup;

    public HeapDumpProfileBuilder(
            Clock clock,
            MicroscopeJeffreyDirs jeffreyDirs,
            Path recordingsDir,
            MicroscopeCoreRepositories localCoreRepositories,
            ProfileManager.Factory profileManagerFactory,
            HeapDumpInitService heapDumpInitService,
            MicroscopeProfileCleanup profileCleanup) {

        this.clock = clock;
        this.jeffreyDirs = jeffreyDirs;
        this.recordingsDir = recordingsDir;
        this.localCoreRepositories = localCoreRepositories;
        this.profileManagerFactory = profileManagerFactory;
        this.heapDumpInitService = heapDumpInitService;
        this.profileCleanup = profileCleanup;
    }

    /**
     * The profile is kept whenever its copy of the dump is still there: a run in flight is joined, a
     * fully built dump is left alone, and anything else -- a failed run (even one that got past the
     * index build and failed in a report), or one a restart interrupted -- is started again on the same
     * copy. Only a profile that lost its dump is dropped, so a fresh one is built from the recording.
     */
    @Override
    public Optional<String> reuse(Recording recording) {
        String profileId = recording.profileId();
        if (heapDumpInitService.isRunning(profileId)) {
            return Optional.of(profileId);
        }

        Optional<HeapDumpManager> heapDumpManager = localCoreRepositories.newProfileRepository(profileId).find()
                .map(profileManagerFactory)
                .map(ProfileManager::heapDumpManager);
        if (heapDumpManager.isPresent() && heapDumpManager.get().heapDumpExists()) {
            boolean lastRunFailed = heapDumpInitService.progress(profileId).state() == PipelineState.FAILED;
            if (lastRunFailed || !heapDumpManager.get().isCacheReady()) {
                heapDumpInitService.start(profileId, heapDumpManager.get(), null);
            }
            return Optional.of(profileId);
        }

        profileCleanup.deleteProfile(profileId);
        return Optional.empty();
    }

    @Override
    public String build(Recording recording, String profileName) {
        RecordingFile file = recording.files().getFirst();
        String profileId = IDGenerator.generate();
        Instant createdAt = clock.instant();

        Path heapDumpAnalysisPath = HeapDumpFiles.inProfileDir(jeffreyDirs.profileDir(profileId)).analysisDir();
        FileSystemUtils.createDirectories(heapDumpAnalysisPath);

        Path sourcePath = RecordingStorageLayout.storagePath(recordingsDir, file.recordingId(), file.filename());
        Path targetPath = heapDumpAnalysisPath.resolve(file.filename());

        try {
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy heap dump to analysis directory", e);
        }

        ProfileInfo profileInfo = new ProfileInfo(
                profileId, null, null, profileName,
                RecordingEventSource.HEAP_DUMP,
                createdAt, createdAt, createdAt, true, false, recording.id());

        ProfileRepository profileRepository = localCoreRepositories.newProfileRepository(profileId);
        profileRepository.insert(new ProfileRepository.InsertProfile(
                profileName,
                RecordingEventSource.HEAP_DUMP, createdAt,
                recording.id(),
                createdAt, createdAt));
        profileRepository.enableProfile(createdAt);

        // Compressed oops are always auto-detected here; forcing them is a re-initialization from the
        // heap dump's overview.
        HeapDumpManager heapDumpManager = profileManagerFactory.apply(profileInfo).heapDumpManager();
        heapDumpInitService.start(profileId, heapDumpManager, null);

        return profileId;
    }

    @Override
    public PipelineProgress progress(String profileId) {
        return heapDumpInitService.progress(profileId);
    }
}
