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
import pbouda.jeffrey.local.persistence.model.QuickGroupInfo;
import pbouda.jeffrey.local.persistence.model.QuickProfileInfo;
import pbouda.jeffrey.local.persistence.model.QuickRecordingInfo;
import pbouda.jeffrey.local.persistence.repository.QuickGroupRepository;
import pbouda.jeffrey.local.persistence.repository.QuickProfileRepository;
import pbouda.jeffrey.local.persistence.repository.QuickRecordingRepository;
import pbouda.jeffrey.profile.ProfileInitializer;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.provider.profile.RecordingInformationParser;
import pbouda.jeffrey.provider.profile.model.recording.RecordingInformation;
import pbouda.jeffrey.shared.common.IDGenerator;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;

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
    private final RecordingInformationParser recordingInformationParser;
    private final ProfileInitializer profileInitializer;
    private final ProfileManager.Factory profileManagerFactory;
    private final QuickProfileRepository quickProfileRepository;
    private final QuickGroupRepository quickGroupRepository;
    private final QuickRecordingRepository quickRecordingRepository;

    public QuickAnalysisManagerImpl(
            Clock clock,
            LocalJeffreyDirs jeffreyDirs,
            RecordingInformationParser recordingInformationParser,
            ProfileInitializer profileInitializer,
            ProfileManager.Factory profileManagerFactory,
            QuickProfileRepository quickProfileRepository,
            QuickGroupRepository quickGroupRepository,
            QuickRecordingRepository quickRecordingRepository) {

        this.clock = clock;
        this.jeffreyDirs = jeffreyDirs;
        this.recordingInformationParser = recordingInformationParser;
        this.profileInitializer = profileInitializer;
        this.profileManagerFactory = profileManagerFactory;
        this.quickProfileRepository = quickProfileRepository;
        this.quickGroupRepository = quickGroupRepository;
        this.quickRecordingRepository = quickRecordingRepository;
    }

    // --- Group operations ---

    @Override
    public String createGroup(String groupName) {
        String groupId = IDGenerator.generate();
        Instant createdAt = clock.instant();

        QuickGroupInfo group = new QuickGroupInfo(groupId, groupName, createdAt);
        quickGroupRepository.insert(group);

        LOG.info("Quick analysis group created: groupId={} groupName={}", groupId, groupName);
        return groupId;
    }

    @Override
    public List<QuickGroupInfo> listGroups() {
        return quickGroupRepository.findAll();
    }

    @Override
    public void deleteGroup(String groupId) {
        List<QuickRecordingInfo> recordings = quickRecordingRepository.findByGroupId(groupId);

        for (QuickRecordingInfo recording : recordings) {
            deleteRecordingInternal(recording);
        }

        quickRecordingRepository.deleteByGroupId(groupId);
        quickGroupRepository.delete(groupId);

        LOG.info("Quick analysis group deleted: groupId={} recordingsDeleted={}", groupId, recordings.size());
    }

    // --- Recording operations ---

    @Override
    public String uploadRecording(String filename, InputStream inputStream, String groupId) {
        if (groupId != null && quickGroupRepository.findById(groupId).isEmpty()) {
            throw new IllegalArgumentException("Group not found: " + groupId);
        }

        Path quickRecordingsDir = jeffreyDirs.quickRecordings();
        FileSystemUtils.createDirectories(quickRecordingsDir);

        String recordingId = IDGenerator.generate();
        Path targetPath = quickRecordingsDir.resolve(recordingId + "-" + filename);

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

        QuickRecordingInfo recording = new QuickRecordingInfo(
                recordingId, filename, groupId, eventSource,
                targetPath.toString(), sizeInBytes, uploadedAt,
                profilingStartedAt, profilingFinishedAt, null);

        quickRecordingRepository.insert(recording);

        LOG.info("Quick analysis recording uploaded: recordingId={} filename={} groupId={}", recordingId, filename, groupId);
        return recordingId;
    }

    @Override
    public List<QuickRecordingInfo> listRecordings() {
        return quickRecordingRepository.findAll();
    }

    @Override
    public void deleteRecording(String recordingId) {
        QuickRecordingInfo recording = quickRecordingRepository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Recording not found: " + recordingId));

        deleteRecordingInternal(recording);
        quickRecordingRepository.delete(recordingId);

        LOG.info("Quick analysis recording deleted: recordingId={}", recordingId);
    }

    // --- Profile creation ---

    @Override
    public String analyzeRecording(String recordingId) {
        QuickRecordingInfo recording = quickRecordingRepository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Recording not found: " + recordingId));

        if (recording.hasProfile()) {
            return recording.profileId();
        }

        String profileId;
        if (recording.eventSource() == RecordingEventSource.HEAP_DUMP) {
            profileId = analyzeHeapDump(recording);
        } else {
            profileId = analyzeJfr(recording);
        }

        quickRecordingRepository.updateProfileId(recordingId, profileId);

        LOG.info("Quick analysis recording analyzed: recordingId={} profileId={}", recordingId, profileId);
        return profileId;
    }

    private String analyzeJfr(QuickRecordingInfo recording) {
        Path filePath = Path.of(recording.filePath());
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("Recording file does not exist: " + filePath);
        }

        String profileId = IDGenerator.generate();
        Instant createdAt = clock.instant();

        RecordingInformation recordingInfo = recordingInformationParser.provide(filePath);

        ProfileInfo profileInfo = new ProfileInfo(
                profileId, null, null, recording.filename(),
                recordingInfo.eventSource(),
                recordingInfo.recordingStartedAt(),
                recordingInfo.recordingFinishedAt(),
                createdAt, false, null);

        profileInitializer.initialize(profileInfo, null, filePath);

        QuickProfileInfo quickInfo = new QuickProfileInfo(
                profileId, recording.filename(), null,
                recordingInfo.eventSource(),
                createdAt,
                recordingInfo.recordingStartedAt(),
                recordingInfo.recordingFinishedAt());

        quickProfileRepository.insert(quickInfo);
        return profileId;
    }

    private String analyzeHeapDump(QuickRecordingInfo recording) {
        String profileId = IDGenerator.generate();
        Instant createdAt = clock.instant();

        Path heapDumpAnalysisPath = jeffreyDirs.quickHeapDumpAnalysisDir(profileId);
        FileSystemUtils.createDirectories(heapDumpAnalysisPath);

        Path sourcePath = Path.of(recording.filePath());
        Path targetPath = heapDumpAnalysisPath.resolve(recording.filename());

        try {
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy heap dump to analysis directory", e);
        }

        ProfileInfo profileInfo = new ProfileInfo(
                profileId, null, null, recording.filename(),
                RecordingEventSource.HEAP_DUMP,
                createdAt, createdAt, createdAt, true, null);

        QuickProfileInfo quickInfo = new QuickProfileInfo(
                profileId, recording.filename(), null,
                RecordingEventSource.HEAP_DUMP,
                createdAt, createdAt, createdAt);

        quickProfileRepository.insert(quickInfo);
        profileManagerFactory.apply(profileInfo);

        return profileId;
    }

    @Override
    public void updateProfileName(String profileId, String profileName) {
        quickProfileRepository.updateProfileName(profileId, profileName);
        LOG.info("Quick analysis profile renamed: profileId={} newName={}", profileId, profileName);
    }

    @Override
    public void deleteProfile(String recordingId) {
        QuickRecordingInfo recording = quickRecordingRepository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Recording not found: " + recordingId));

        if (!recording.hasProfile()) {
            throw new IllegalStateException("Recording has no profile: " + recordingId);
        }

        deleteProfileInternal(recording.profileId());
        quickRecordingRepository.updateProfileId(recordingId, null);

        LOG.info("Quick analysis profile deleted: recordingId={} profileId={}", recordingId, recording.profileId());
    }

    // --- Profile access ---

    @Override
    public Optional<ProfileManager> profile(String profileId) {
        return quickProfileRepository.find(profileId)
                .map(QuickProfileInfo::toProfileInfo)
                .map(profileManagerFactory);
    }

    // --- Internal helpers ---

    private void deleteRecordingInternal(QuickRecordingInfo recording) {
        if (recording.hasProfile()) {
            deleteProfileInternal(recording.profileId());
        }

        Path recordingFile = Path.of(recording.filePath());
        if (Files.exists(recordingFile)) {
            try {
                Files.delete(recordingFile);
            } catch (IOException e) {
                LOG.warn("Failed to delete recording file: path={}", recordingFile, e);
            }
        }
    }

    private void deleteProfileInternal(String profileId) {
        Path profileDir = jeffreyDirs.quickProfileDir(profileId);

        quickProfileRepository.delete(profileId);

        if (Files.exists(profileDir)) {
            FileSystemUtils.removeDirectory(profileDir);
        }

        LOG.info("Quick analysis profile deleted: profileId={}", profileId);
    }

    private static RecordingEventSource detectEventSource(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".hprof") || lower.endsWith(".hprof.gz")) {
            return RecordingEventSource.HEAP_DUMP;
        }
        return RecordingEventSource.UNKNOWN;
    }
}
