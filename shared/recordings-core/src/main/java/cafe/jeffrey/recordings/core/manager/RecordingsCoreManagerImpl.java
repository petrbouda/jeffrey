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

package cafe.jeffrey.recordings.core.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.microscope.persistence.api.RecordingRepository;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.microscope.persistence.api.RecordingTagsRepository;
import cafe.jeffrey.recordings.core.manager.RecordingMetadataParser.RecordingMetadata;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.RecordingFile;
import cafe.jeffrey.shared.common.model.repository.SupportedFile;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;

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
 * Deployment-agnostic implementation of the recording store. Holds NO profile coupling — optional
 * metadata enrichment is delegated to {@link RecordingMetadataParser} and profile cleanup on
 * deletion to {@link RecordingProfileCleanup}, both of which default to no-ops.
 */
public class RecordingsCoreManagerImpl implements RecordingsCoreManager {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingsCoreManagerImpl.class);

    private final Clock clock;
    private final Path recordingsDir;
    private final RecordingRepository recordingRepository;
    private final RecordingTagsRepository recordingTagsRepository;
    private final RecordingMetadataParser metadataParser;
    private final RecordingProfileCleanup profileCleanup;

    public RecordingsCoreManagerImpl(
            Clock clock,
            Path recordingsDir,
            RecordingRepository recordingRepository,
            RecordingTagsRepository recordingTagsRepository,
            RecordingMetadataParser metadataParser,
            RecordingProfileCleanup profileCleanup) {

        this.clock = clock;
        this.recordingsDir = recordingsDir;
        this.recordingRepository = recordingRepository;
        this.recordingTagsRepository = recordingTagsRepository;
        this.metadataParser = metadataParser == null ? RecordingMetadataParser.NOOP : metadataParser;
        this.profileCleanup = profileCleanup == null ? RecordingProfileCleanup.NOOP : profileCleanup;
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
            recordingTagsRepository.deleteForRecording(recording.id());
        }

        recordingRepository.deleteGroup(groupId);

        LOG.info("Quick analysis group deleted: groupId={} recordingsDeleted={}", groupId, recordings.size());

        // A cascade rather than one deliberate delete: one click took every recording in the group,
        // so it is raised a level above RECORDING_DELETED even though each step was routine.
        Notifications.of(NotificationType.RECORDING_GROUP_DELETED)
                .attribute("groupId", groupId)
                .attribute("recordingsDeleted", recordings.size())
                .emit();
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

        persistRecording(recordingId, filename, targetPath, sizeInBytes, groupId, List.of(), Map.of());

        LOG.info("Quick analysis recording uploaded: recordingId={} filename={} groupId={}", recordingId, filename, groupId);
        return recordingId;
    }

    @Override
    public String importRecordingFromPath(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Recording path is required");
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Recording file not found: " + path);
        }

        String filename = path.getFileName().toString();
        if (SupportedFile.of(filename) == SupportedFile.UNKNOWN) {
            throw new IllegalArgumentException("Unsupported recording file type: " + filename);
        }

        LOG.debug("Importing recording from local path: path={}", path);
        try (InputStream inputStream = Files.newInputStream(path)) {
            return uploadRecording(filename, inputStream, null);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read recording from path: " + path, e);
        }
    }

    @Override
    public String createDownloadedRecording(
            String recordingName,
            Path recordingFile,
            List<Path> additionalFiles,
            Map<String, String> originTags) {

        String recordingId = IDGenerator.generate();
        String filename = recordingFile.getFileName().toString();
        Path targetPath = recordingsDir.resolve(recordingId + "-" + filename);

        try {
            Files.copy(recordingFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy downloaded recording into QA storage", e);
        }

        long sizeInBytes;
        try {
            sizeInBytes = Files.size(targetPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to get file size", e);
        }

        persistRecording(recordingId, filename, targetPath, sizeInBytes, null, additionalFiles, originTags);

        LOG.info("Quick analysis recording downloaded from project: recordingId={} filename={} additionalFileCount={} tagCount={} sourceName={}",
                recordingId, filename, additionalFiles.size(), originTags.size(), recordingName);
        return recordingId;
    }

    /**
     * Shared persistence path for both manual uploads and downloaded recordings.
     * Parses recording info, inserts the primary file, copies and inserts any additional files,
     * then writes the supplied origin/system tags.
     */
    private void persistRecording(
            String recordingId,
            String filename,
            Path targetPath,
            long sizeInBytes,
            String groupId,
            List<Path> additionalFiles,
            Map<String, String> originTags) {

        RecordingEventSource eventSource = detectEventSource(filename);
        Instant uploadedAt = clock.instant();

        Instant profilingStartedAt = null;
        Instant profilingFinishedAt = null;

        if (eventSource != RecordingEventSource.HEAP_DUMP) {
            Optional<RecordingMetadata> metadata = metadataParser.parse(targetPath);
            if (metadata.isPresent()) {
                RecordingMetadata recordingInfo = metadata.get();
                eventSource = recordingInfo.eventSource();
                profilingStartedAt = recordingInfo.recordingStartedAt();
                profilingFinishedAt = recordingInfo.recordingFinishedAt();
            }
        }

        Recording recording = new Recording(
                recordingId, filename, null, groupId, eventSource, uploadedAt,
                profilingStartedAt, profilingFinishedAt,
                false, null, null, List.of());

        String recordingFileId = IDGenerator.generate();
        RecordingFile recordingFile = new RecordingFile(
                recordingFileId, recordingId, filename,
                SupportedFile.of(filename),
                uploadedAt, sizeInBytes);

        recordingRepository.insertRecording(recording, recordingFile);

        for (Path additionalFile : additionalFiles) {
            persistAdditionalFile(recordingId, additionalFile, uploadedAt);
        }

        if (originTags != null && !originTags.isEmpty()) {
            recordingTagsRepository.insert(recordingId, originTags);
        }
    }

    private void persistAdditionalFile(String recordingId, Path additionalFilePath, Instant uploadedAt) {
        String additionalFilename = additionalFilePath.getFileName().toString();
        Path targetPath = recordingsDir.resolve(recordingId + "-" + additionalFilename);
        try {
            Files.copy(additionalFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to copy additional file into QA storage: " + additionalFilename, e);
        }

        long sizeInBytes;
        try {
            sizeInBytes = Files.size(targetPath);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to get additional file size: " + additionalFilename, e);
        }

        RecordingFile additionalFile = new RecordingFile(
                IDGenerator.generate(), recordingId, additionalFilename,
                SupportedFile.of(additionalFilename),
                uploadedAt, sizeInBytes);

        recordingRepository.insertRecordingFile(additionalFile);
    }

    @Override
    public List<Recording> listRecordings() {
        return recordingRepository.findAllRecordings();
    }

    @Override
    public Optional<Recording> findRecording(String recordingId) {
        return recordingRepository.findRecording(recordingId);
    }

    @Override
    public Map<String, List<RecordingTag>> tagsForRecordings(Collection<String> recordingIds) {
        return recordingTagsRepository.listForRecordings(recordingIds);
    }

    @Override
    public void deleteRecording(String recordingId) {
        Recording recording = recordingRepository.findRecording(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Recording not found: " + recordingId));

        deleteRecordingInternal(recording);
        recordingRepository.deleteRecordingWithFiles(recordingId);
        recordingTagsRepository.deleteForRecording(recordingId);

        LOG.info("Quick analysis recording deleted: recordingId={}", recordingId);

        // Said as well as logged, because this destroys files and cannot be undone: the log line is
        // gone with the next rotation, and a notification is still in the recording afterwards.
        Notifications.of(NotificationType.RECORDING_DELETED)
                .attribute("recordingId", recordingId)
                .attribute("recordingName", recording.recordingName())
                .attribute("groupId", recording.groupId())
                .attribute("fileCount", recording.files().size())
                .attribute("hadProfile", recording.hasProfile())
                .attribute("profileId", recording.profileId())
                .emit();
    }

    @Override
    public Optional<Path> findRecordingFile(String recordingId, String fileId) {
        return recordingRepository.findRecording(recordingId)
                .flatMap(rec -> rec.files().stream()
                        .filter(f -> f.id().equals(fileId))
                        .findFirst()
                        .map(this::resolveRecordingFilePath));
    }

    // --- Internal helpers ---

    private void deleteRecordingInternal(Recording recording) {
        profileCleanup.onRecordingDeleted(recording);

        for (RecordingFile file : recording.files()) {
            FileSystemUtils.removeFile(resolveRecordingFilePath(file));
        }
    }

    private Path resolveRecordingFilePath(RecordingFile file) {
        return recordingsDir.resolve(file.recordingId() + "-" + file.filename());
    }

    /**
     * What kind of events a file holds, from its name.
     *
     * <p>Decided by {@link SupportedFile} rather than by suffix tests of its own: the heap
     * dump branch used to carry its own copy of {@code .hprof} and {@code .hprof.gz}, which is one
     * more place to update when a format is added and one more place to disagree about case.
     */
    private static RecordingEventSource detectEventSource(String filename) {
        return SupportedFile.of(filename).eventSource().orElse(RecordingEventSource.UNKNOWN);
    }
}
