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
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.core.manager.recordings.MicroscopeProfileCleanup;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.ProfileRepository;
import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.perfcounters.PerfCountersOverview;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import cafe.jeffrey.storage.recording.api.file.RecordingStorageLayout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Builds a perf-counters snapshot's profile. Reading a snapshot takes milliseconds, so it is done
 * within the request and has no stages to follow: the profile appears enabled, or not at all. Its
 * window is the JVM's run as the counters tell it -- from the JVM's start to its last safepoint.
 */
public final class PerfCountersProfileBuilder implements ProfileBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(PerfCountersProfileBuilder.class);

    private static final String PIPELINE_ID = "perf-counters";

    private final Clock clock;
    private final MicroscopeJeffreyDirs jeffreyDirs;
    private final Path recordingsDir;
    private final MicroscopeCoreRepositories localCoreRepositories;
    private final ProfileManager.Factory profileManagerFactory;
    private final MicroscopeProfileCleanup profileCleanup;

    public PerfCountersProfileBuilder(
            Clock clock,
            MicroscopeJeffreyDirs jeffreyDirs,
            Path recordingsDir,
            MicroscopeCoreRepositories localCoreRepositories,
            ProfileManager.Factory profileManagerFactory,
            MicroscopeProfileCleanup profileCleanup) {

        this.clock = clock;
        this.jeffreyDirs = jeffreyDirs;
        this.recordingsDir = recordingsDir;
        this.localCoreRepositories = localCoreRepositories;
        this.profileManagerFactory = profileManagerFactory;
        this.profileCleanup = profileCleanup;
    }

    /** Kept while it still holds its counters; otherwise dropped, so the snapshot is read again. */
    @Override
    public Optional<String> reuse(Recording recording) {
        String profileId = recording.profileId();
        boolean holdsCounters = localCoreRepositories.newProfileRepository(profileId).find()
                .filter(ProfileInfo::enabled)
                .map(profileManagerFactory)
                .map(manager -> manager.perfCountersManager().exists())
                .orElse(false);
        if (holdsCounters) {
            return Optional.of(profileId);
        }

        profileCleanup.deleteProfile(profileId);
        return Optional.empty();
    }

    /**
     * The counters are read into the profile's database before its row exists, since the window the
     * row records comes from them; a snapshot that cannot be read leaves nothing behind.
     */
    @Override
    public String build(Recording recording) {
        RecordingFile file = recording.files().getFirst();
        Path snapshot = RecordingStorageLayout.storagePath(recordingsDir, file.recordingId(), file.filename());
        String profileId = IDGenerator.generate();
        Instant createdAt = clock.instant();
        String profileName = recording.recordingName();

        ProfileInfo profileInfo = new ProfileInfo(
                profileId, null, null, profileName, RecordingEventSource.PERF_COUNTERS,
                null, null, createdAt, false, false, recording.id());

        PerfCountersOverview overview;
        try {
            overview = profileManagerFactory.apply(profileInfo).perfCountersManager().ingest(snapshot);
        } catch (RuntimeException e) {
            // No row was written yet, so there is no profile to delete: only the database it started.
            Path profileDir = jeffreyDirs.profileDir(profileId);
            if (Files.exists(profileDir)) {
                FileSystemUtils.removeDirectory(profileDir);
            }
            Notifications.of(NotificationType.PROFILE_ANALYSIS_FAILED)
                    .attribute("recordingId", recording.id())
                    .attribute("profileId", profileId)
                    .errorType(e)
                    .emit();
            throw e;
        }

        ProfileRepository profileRepository = localCoreRepositories.newProfileRepository(profileId);
        profileRepository.insert(new ProfileRepository.InsertProfile(
                profileName,
                RecordingEventSource.PERF_COUNTERS,
                createdAt,
                recording.id(),
                epochMillis(overview.runtime().startedAtEpochMs()),
                epochMillis(overview.runtime().endedAtEpochMs())));
        profileRepository.enableProfile(createdAt);

        LOG.info("Perf-counters profile built: recordingId={} profileId={}", recording.id(), profileId);
        return profileId;
    }

    @Override
    public PipelineProgress progress(String profileId) {
        return PipelineProgress.idle(PIPELINE_ID);
    }

    private static Instant epochMillis(Long epochMillis) {
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis);
    }
}
