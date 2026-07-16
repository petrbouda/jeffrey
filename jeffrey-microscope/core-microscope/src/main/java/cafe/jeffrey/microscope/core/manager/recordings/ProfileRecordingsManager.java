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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.ProfileRepository;
import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.microscope.persistence.api.RecordingRepository;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.profile.ProfileInitializer;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.RecordingFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Microscope's profile-aware {@link RecordingsManager}. Delegates all deployment-agnostic recording
 * store operations to the shared {@link RecordingsCoreManager} and adds the profile-creation /
 * profile-lifecycle operations that are specific to the full microscope deployment.
 */
public class ProfileRecordingsManager implements RecordingsManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileRecordingsManager.class);

    private final RecordingsCoreManager core;
    private final Clock clock;
    private final MicroscopeJeffreyDirs jeffreyDirs;
    private final Path recordingsDir;
    private final RecordingInformationParser recordingInformationParser;
    private final ProfileInitializer profileInitializer;
    private final ProfileManager.Factory profileManagerFactory;
    private final MicroscopeCoreRepositories localCoreRepositories;
    private final MicroscopeProfileCleanup profileCleanup;
    private final RecordingRepository recordingRepository;

    public ProfileRecordingsManager(
            RecordingsCoreManager core,
            Clock clock,
            MicroscopeJeffreyDirs jeffreyDirs,
            Path recordingsDir,
            RecordingInformationParser recordingInformationParser,
            ProfileInitializer profileInitializer,
            ProfileManager.Factory profileManagerFactory,
            MicroscopeCoreRepositories localCoreRepositories,
            MicroscopeProfileCleanup profileCleanup) {

        this.core = core;
        this.clock = clock;
        this.jeffreyDirs = jeffreyDirs;
        this.recordingsDir = recordingsDir;
        this.recordingInformationParser = recordingInformationParser;
        this.profileInitializer = profileInitializer;
        this.profileManagerFactory = profileManagerFactory;
        this.localCoreRepositories = localCoreRepositories;
        this.profileCleanup = profileCleanup;
        this.recordingRepository = localCoreRepositories.newRecordingRepository(null);
    }

    // --- Delegated store operations (deployment-agnostic) ---

    @Override
    public String createGroup(String groupName) {
        return core.createGroup(groupName);
    }

    @Override
    public List<RecordingGroup> listGroups() {
        return core.listGroups();
    }

    @Override
    public void deleteGroup(String groupId) {
        core.deleteGroup(groupId);
    }

    @Override
    public String uploadRecording(String filename, InputStream inputStream, String groupId) {
        return core.uploadRecording(filename, inputStream, groupId);
    }

    @Override
    public String importRecordingFromPath(Path path) {
        return core.importRecordingFromPath(path);
    }

    @Override
    public String createDownloadedRecording(
            String recordingName,
            Path mergedRecordingFile,
            List<Path> artifactFiles,
            Map<String, String> originTags) {
        return core.createDownloadedRecording(recordingName, mergedRecordingFile, artifactFiles, originTags);
    }

    @Override
    public void moveRecordingToGroup(String recordingId, String groupId) {
        core.moveRecordingToGroup(recordingId, groupId);
    }

    @Override
    public List<Recording> listRecordings() {
        return core.listRecordings();
    }

    @Override
    public Optional<Recording> findRecording(String recordingId) {
        return core.findRecording(recordingId);
    }

    @Override
    public Map<String, List<RecordingTag>> tagsForRecordings(Collection<String> recordingIds) {
        return core.tagsForRecordings(recordingIds);
    }

    @Override
    public void deleteRecording(String recordingId) {
        core.deleteRecording(recordingId);
    }

    @Override
    public Optional<Path> findRecordingFile(String recordingId, String fileId) {
        return core.findRecordingFile(recordingId, fileId);
    }

    // --- Profile creation (microscope-specific) ---

    @Override
    public String analyzeRecording(String recordingId) {
        Recording recording = recordingRepository.findRecording(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Recording not found: " + recordingId));

        if (recording.hasProfile()) {
            return recording.profileId();
        }

        RecordingFile file = recording.files().getFirst();
        String profileId;
        if (recording.eventSource() == RecordingEventSource.HEAP_DUMP) {
            profileId = analyzeHeapDump(recording, file);
        } else {
            profileId = analyzeEvents(recording, file);
        }

        LOG.info("Quick analysis recording analyzed: recordingId={} profileId={}", recordingId, profileId);
        return profileId;
    }

    /**
     * Analysis path for every event-based recording (JFR, async-profiler, OpenTelemetry profiles):
     * the concrete parser is resolved from the recording's event source inside the profile
     * initializer.
     */
    private String analyzeEvents(Recording recording, RecordingFile file) {
        Path filePath = resolveRecordingFilePath(file);
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("Recording file does not exist: " + filePath);
        }

        String profileId = IDGenerator.generate();
        Instant createdAt = clock.instant();

        RecordingInformation recordingInfo = resolveRecordingInformation(recording, filePath);

        ProfileInfo profileInfo = new ProfileInfo(
                profileId, null, null, file.filename(),
                recordingInfo.eventSource(),
                recordingInfo.recordingStartedAt(),
                recordingInfo.recordingFinishedAt(),
                createdAt, false, false, recording.id());

        profileInitializer.initialize(profileInfo, null, filePath);

        ProfileRepository profileRepository = localCoreRepositories.newProfileRepository(profileId);
        profileRepository.insert(ProfileRepository.InsertProfile.quickProfile(
                file.filename(),
                recordingInfo.eventSource(), createdAt,
                recording.id(),
                recordingInfo.recordingStartedAt(),
                recordingInfo.recordingFinishedAt()));
        profileRepository.enableProfile(createdAt);

        return profileId;
    }

    /**
     * Prefers the recording metadata persisted at upload time (event source + profiling start/end)
     * and re-parses the recording file only when any of them is missing — e.g. when the metadata
     * parse failed during the upload.
     */
    private RecordingInformation resolveRecordingInformation(Recording recording, Path filePath) {
        boolean persistedInfoComplete = recording.eventSource() != null
                && recording.eventSource() != RecordingEventSource.UNKNOWN
                && recording.recordingStartedAt() != null
                && recording.recordingFinishedAt() != null;

        if (persistedInfoComplete) {
            return new RecordingInformation(
                    FileSystemUtils.size(filePath),
                    recording.eventSource(),
                    recording.recordingStartedAt(),
                    recording.recordingFinishedAt());
        }
        return recordingInformationParser.provide(filePath);
    }

    private String analyzeHeapDump(Recording recording, RecordingFile file) {
        String profileId = IDGenerator.generate();
        Instant createdAt = clock.instant();

        Path heapDumpAnalysisPath = jeffreyDirs.profileDir(profileId).resolve(MicroscopeJeffreyDirs.HEAP_DUMP_ANALYSIS_DIR);
        FileSystemUtils.createDirectories(heapDumpAnalysisPath);

        Path sourcePath = resolveRecordingFilePath(file);
        Path targetPath = heapDumpAnalysisPath.resolve(file.filename());

        try {
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy heap dump to analysis directory", e);
        }

        ProfileInfo profileInfo = new ProfileInfo(
                profileId, null, null, file.filename(),
                RecordingEventSource.HEAP_DUMP,
                createdAt, createdAt, createdAt, true, false, recording.id());

        ProfileRepository profileRepository = localCoreRepositories.newProfileRepository(profileId);
        profileRepository.insert(ProfileRepository.InsertProfile.quickProfile(
                file.filename(),
                RecordingEventSource.HEAP_DUMP, createdAt,
                recording.id(),
                createdAt, createdAt));
        profileRepository.enableProfile(createdAt);

        profileManagerFactory.apply(profileInfo);

        return profileId;
    }

    @Override
    public void updateProfileName(String profileId, String profileName) {
        localCoreRepositories.newProfileRepository(profileId).update(profileName);
        LOG.info("Quick analysis profile renamed: profileId={} newName={}", profileId, profileName);
    }

    @Override
    public void deleteProfile(String recordingId) {
        Recording recording = recordingRepository.findRecording(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Recording not found: " + recordingId));

        if (!recording.hasProfile()) {
            throw new IllegalStateException("Recording has no profile: " + recordingId);
        }

        profileCleanup.deleteProfile(recording.profileId());

        LOG.info("Quick analysis profile deleted: recordingId={} profileId={}", recordingId, recording.profileId());
    }

    @Override
    public Optional<ProfileManager> profile(String profileId) {
        return localCoreRepositories.newProfileRepository(profileId).find()
                .map(profileManagerFactory);
    }

    private Path resolveRecordingFilePath(RecordingFile file) {
        return recordingsDir.resolve(file.recordingId() + "-" + file.filename());
    }
}
