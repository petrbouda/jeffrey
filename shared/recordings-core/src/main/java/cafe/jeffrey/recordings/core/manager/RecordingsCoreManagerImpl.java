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
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

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
        if (SupportedRecordingFile.of(filename) == SupportedRecordingFile.UNKNOWN) {
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
            Path mergedRecordingFile,
            List<Path> artifactFiles,
            Map<String, String> originTags) {

        String recordingId = IDGenerator.generate();
        String filename = mergedRecordingFile.getFileName().toString();
        Path targetPath = recordingsDir.resolve(recordingId + "-" + filename);

        try {
            Files.copy(mergedRecordingFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy downloaded recording into QA storage", e);
        }

        long sizeInBytes;
        try {
            sizeInBytes = Files.size(targetPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to get file size", e);
        }

        persistRecording(recordingId, filename, targetPath, sizeInBytes, null, artifactFiles, originTags);

        LOG.info("Quick analysis recording downloaded from project: recordingId={} filename={} artifactCount={} tagCount={} sourceName={}",
                recordingId, filename, artifactFiles.size(), originTags.size(), recordingName);
        return recordingId;
    }

    /**
     * Shared persistence path for both manual uploads and downloaded recordings.
     * Parses recording info, inserts the primary file, copies and inserts any artifact files,
     * then writes the supplied origin/system tags.
     */
    private void persistRecording(
            String recordingId,
            String filename,
            Path targetPath,
            long sizeInBytes,
            String groupId,
            List<Path> artifactFiles,
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
                SupportedRecordingFile.of(filename),
                uploadedAt, sizeInBytes);

        recordingRepository.insertRecording(recording, recordingFile);

        for (Path artifact : artifactFiles) {
            persistArtifact(recordingId, artifact, uploadedAt);
        }

        if (originTags != null && !originTags.isEmpty()) {
            recordingTagsRepository.insert(recordingId, originTags);
        }
    }

    private void persistArtifact(String recordingId, Path artifactPath, Instant uploadedAt) {
        String artifactFilename = artifactPath.getFileName().toString();
        Path targetPath = recordingsDir.resolve(recordingId + "-" + artifactFilename);
        try {
            Files.copy(artifactPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to copy artifact into QA storage: " + artifactFilename, e);
        }

        long sizeInBytes;
        try {
            sizeInBytes = Files.size(targetPath);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to get artifact file size: " + artifactFilename, e);
        }

        RecordingFile artifactFile = new RecordingFile(
                IDGenerator.generate(), recordingId, artifactFilename,
                SupportedRecordingFile.of(artifactFilename),
                uploadedAt, sizeInBytes);

        recordingRepository.insertRecordingFile(artifactFile);
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

    private static RecordingEventSource detectEventSource(String filename) {
        return SupportedRecordingFile.of(filename).eventSource();
    }
}
