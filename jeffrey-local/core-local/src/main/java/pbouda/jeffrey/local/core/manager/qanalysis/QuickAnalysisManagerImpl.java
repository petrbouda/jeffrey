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

package pbouda.jeffrey.local.core.manager.qanalysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.LocalJeffreyDirs;
import pbouda.jeffrey.local.persistence.model.RecordingGroup;
import pbouda.jeffrey.local.persistence.repository.LocalCoreRepositories;
import pbouda.jeffrey.local.persistence.repository.ProfileRepository;
import pbouda.jeffrey.local.persistence.repository.RecordingRepository;
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.provider.profile.RecordingInformationParser;
import pbouda.jeffrey.provider.profile.model.recording.RecordingInformation;
import pbouda.jeffrey.shared.common.IDGenerator;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.Recording;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.common.model.RecordingFile;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class QuickAnalysisManagerImpl implements QuickAnalysisManager {

    private static final Logger LOG = LoggerFactory.getLogger(QuickAnalysisManagerImpl.class);

    private final Clock clock;
    private final LocalJeffreyDirs jeffreyDirs;
    private final Path recordingsDir;
    private final RecordingInformationParser recordingInformationParser;
    private final ProfileInitializer profileInitializer;
    private final ProfileManager.Factory profileManagerFactory;
    private final LocalCoreRepositories localCoreRepositories;
    private final RecordingRepository recordingRepository;

    public QuickAnalysisManagerImpl(
            Clock clock,
            LocalJeffreyDirs jeffreyDirs,
            Path recordingsDir,
            RecordingInformationParser recordingInformationParser,
            ProfileInitializer profileInitializer,
            ProfileManager.Factory profileManagerFactory,
            LocalCoreRepositories localCoreRepositories) {

        this.clock = clock;
        this.jeffreyDirs = jeffreyDirs;
        this.recordingsDir = recordingsDir;
        this.recordingInformationParser = recordingInformationParser;
        this.profileInitializer = profileInitializer;
        this.profileManagerFactory = profileManagerFactory;
        this.localCoreRepositories = localCoreRepositories;
        this.recordingRepository = localCoreRepositories.newRecordingRepository(null);
    }

    // --- Group operations ---

    @Override
    public String createGroup(String groupName) {
        String groupId = recordingRepository.insertGroup(groupName);
        LOG.info("Quick analysis group created: groupId={} groupName={}", groupId, groupName);
        return groupId;
    }

    @Override
    public List<RecordingGroup> listGroups() {
        return recordingRepository.findAllRecordingGroups();
    }

    @Override
    public void deleteGroup(String groupId) {
        List<Recording> recordings = recordingRepository.findRecordingsByGroupId(groupId);

        for (Recording recording : recordings) {
            deleteRecordingInternal(recording);
        }

        recordingRepository.deleteGroup(groupId);

        LOG.info("Quick analysis group deleted: groupId={} recordingsDeleted={}", groupId, recordings.size());
    }

    // --- Recording operations ---

    @Override
    public void moveRecordingToGroup(String recordingId, String groupId) {
        LOG.debug("Moving quick recording to group: recordingId={} groupId={}", recordingId, groupId);
        recordingRepository.updateRecordingGroup(recordingId, groupId);
    }

    @Override
    public String uploadRecording(String filename, InputStream inputStream, String groupId) {
        if (groupId != null && recordingRepository.findGroupById(groupId).isEmpty()) {
            throw new IllegalArgumentException("Group not found: " + groupId);
        }

        String recordingId = IDGenerator.generate();
        Path targetPath = recordingsDir.resolve(recordingId + "-" + filename);

        try {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save uploaded file", e);
        }

        long sizeInBytes;
        try {
            sizeInBytes = Files.size(targetPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to get file size", e);
        }
        RecordingEventSource eventSource = detectEventSource(filename);
        Instant uploadedAt = clock.instant();

        Instant profilingStartedAt = null;
        Instant profilingFinishedAt = null;

        if (eventSource != RecordingEventSource.HEAP_DUMP) {
            try {
                RecordingInformation recordingInfo = recordingInformationParser.provide(targetPath);
                eventSource = recordingInfo.eventSource();
                profilingStartedAt = recordingInfo.recordingStartedAt();
                profilingFinishedAt = recordingInfo.recordingFinishedAt();
            } catch (Exception e) {
                LOG.warn("Failed to parse recording metadata: filename={} error={}", filename, e.getMessage());
            }
        }

        Recording recording = new Recording(
                recordingId, filename, null, groupId, eventSource, uploadedAt,
                profilingStartedAt, profilingFinishedAt,
                false, null, null, List.of());

        String recordingFileId = IDGenerator.generate();
        RecordingFile recordingFile = new RecordingFile(
                recordingFileId, recordingId, filename,
                SupportedRecordingFile.of(filename),
                uploadedAt, sizeInBytes);

        recordingRepository.insertRecording(recording, recordingFile);

        LOG.info("Quick analysis recording uploaded: recordingId={} filename={} groupId={}", recordingId, filename, groupId);
        return recordingId;
    }

    @Override
    public List<Recording> listRecordings() {
        return recordingRepository.findAllRecordings();
    }

    @Override
    public void deleteRecording(String recordingId) {
        Recording recording = recordingRepository.findRecording(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Recording not found: " + recordingId));

        deleteRecordingInternal(recording);
        recordingRepository.deleteRecordingWithFiles(recordingId);

        LOG.info("Quick analysis recording deleted: recordingId={}", recordingId);
    }

    // --- Profile creation ---

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
            profileId = analyzeJfr(recording, file);
        }

        LOG.info("Quick analysis recording analyzed: recordingId={} profileId={}", recordingId, profileId);
        return profileId;
    }

    private String analyzeJfr(Recording recording, RecordingFile file) {
        Path filePath = resolveRecordingFilePath(file);
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("Recording file does not exist: " + filePath);
        }

        String profileId = IDGenerator.generate();
        Instant createdAt = clock.instant();

        RecordingInformation recordingInfo = recordingInformationParser.provide(filePath);

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

    private String analyzeHeapDump(Recording recording, RecordingFile file) {
        String profileId = IDGenerator.generate();
        Instant createdAt = clock.instant();

        Path heapDumpAnalysisPath = jeffreyDirs.profileDir(profileId).resolve(LocalJeffreyDirs.HEAP_DUMP_ANALYSIS_DIR);
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

        deleteProfileInternal(recording.profileId());

        LOG.info("Quick analysis profile deleted: recordingId={} profileId={}", recordingId, recording.profileId());
    }

    // --- Profile access ---

    @Override
    public Optional<ProfileManager> profile(String profileId) {
        return localCoreRepositories.newProfileRepository(profileId).find()
                .map(profileManagerFactory);
    }

    // --- Internal helpers ---

    private void deleteRecordingInternal(Recording recording) {
        if (recording.hasProfile()) {
            deleteProfileInternal(recording.profileId());
        }

        if (!recording.files().isEmpty()) {
            RecordingFile file = recording.files().getFirst();
            FileSystemUtils.removeFile(resolveRecordingFilePath(file));
        }
    }

    private void deleteProfileInternal(String profileId) {
        Path profileDir = jeffreyDirs.profileDir(profileId);

        localCoreRepositories.newProfileRepository(profileId).delete();

        if (Files.exists(profileDir)) {
            FileSystemUtils.removeDirectory(profileDir);
        }

        LOG.info("Quick analysis profile deleted: profileId={}", profileId);
    }

    private Path resolveRecordingFilePath(RecordingFile file) {
        return recordingsDir.resolve(file.recordingId() + "-" + file.filename());
    }

    private static RecordingEventSource detectEventSource(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".hprof") || lower.endsWith(".hprof.gz")) {
            return RecordingEventSource.HEAP_DUMP;
        }
        return RecordingEventSource.UNKNOWN;
    }
}
