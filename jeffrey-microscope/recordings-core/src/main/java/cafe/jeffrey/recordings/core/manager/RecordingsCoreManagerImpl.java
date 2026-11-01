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
import cafe.jeffrey.microscope.model.ProfileKind;
import cafe.jeffrey.storage.recording.api.file.Bundle;
import cafe.jeffrey.storage.recording.api.file.BundleFile;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import cafe.jeffrey.storage.recording.api.file.RecordingStorageLayout;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
import cafe.jeffrey.shared.notification.NotificationCategory;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;
import cafe.jeffrey.jfr.events.notification.Severity;

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
import java.util.Set;

/**
 * Deployment-agnostic implementation of the recording store. Holds NO profile coupling — optional
 * metadata enrichment is delegated to {@link RecordingMetadataParser} and profile cleanup on
 * deletion to {@link RecordingProfileCleanup}, both of which default to no-ops.
 */
public class RecordingsCoreManagerImpl implements RecordingsCoreManager {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingsCoreManagerImpl.class);

    private static final String ARTIFACT_NAME_SEPARATOR = "_";

    /** The kinds whose files carry their own metadata (format, window) to read when stored. */
    private static final Set<ProfileKind> PARSED_KINDS = Set.of(ProfileKind.JFR, ProfileKind.STACK_SAMPLES);

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

        List<Bundle> bundles = recordingRepository.findBundlesByGroupId(groupId);
        for (Bundle bundle : bundles) {
            deleteBundleInternal(bundle);
        }

        recordingRepository.deleteGroup(groupId);

        LOG.info("Quick analysis group deleted: groupId={} recordingsDeleted={} bundlesDeleted={}",
                groupId, recordings.size(), bundles.size());

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
        requireInitializable(filename);

        String recordingId = IDGenerator.generate();
        StoredFile stored = new StoredFile(filename, storagePath(recordingId, filename));

        try {
            Files.copy(inputStream, stored.path(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save uploaded file", e);
        }

        Recording recording = prepareRecording(recordingId, filename, List.of(stored), groupId, null);
        recordingRepository.insertRecording(recording, recording.files().getFirst());

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
        requireInitializable(filename);

        LOG.debug("Importing recording from local path: path={}", path);
        try (InputStream inputStream = Files.newInputStream(path)) {
            return uploadRecording(filename, inputStream, null);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read recording from path: " + path, e);
        }
    }

    @Override
    public Optional<Recording> findByFileNameAndSize(String fileName, long sizeInBytes) {
        return recordingRepository.findByFileNameAndSize(fileName, sizeInBytes);
    }

    /**
     * Stores a downloaded session as a bundle: its recording files as one recording, each heap dump as a
     * recording of its own, and the logs and crash files as download-only files of the bundle.
     * <p>
     * The files are kept apart, each under the name the session gave it. Nothing joins them: the
     * parser reads a recording's files as independent inputs, and its window is taken across all of
     * them rather than from any one. Every member carries the session's origin tags, so each says
     * where it came from on its own.
     *
     * @return the id of the bundle's recording item — the JFR chunks — which is what a download is
     *         analysed by first
     */
    @Override
    public String createDownloadedBundle(
            String bundleName,
            List<Path> recordingFiles,
            List<Path> artifactFiles,
            Map<String, String> originTags) {

        BundlePlan plan = BundlePlan.of(bundleName, recordingFiles, artifactFiles);
        String bundleId = IDGenerator.generate();
        Instant uploadedAt = clock.instant();

        List<Recording> members = plan.items().stream()
                .map(item -> {
                    String recordingId = IDGenerator.generate();
                    List<StoredFile> stored = item.files().stream()
                            .map(file -> copyIntoStorage(recordingId, file))
                            .toList();
                    return prepareRecording(recordingId, item.name(), stored, null, bundleId);
                })
                .toList();

        List<BundleFile> files = plan.files().stream()
                .map(file -> bundleFile(bundleId, copyIntoStorage(bundleId, file), uploadedAt))
                .toList();

        recordingRepository.insertBundle(new Bundle(bundleId, bundleName, null, uploadedAt, files), members);

        if (originTags != null && !originTags.isEmpty()) {
            members.forEach(member -> recordingTagsRepository.insert(member.id(), originTags));
        }

        LOG.info("Quick analysis bundle downloaded from project: bundleId={} bundleName={} itemCount={} "
                        + "fileCount={} tagCount={}",
                bundleId, bundleName, members.size(), files.size(), originTags == null ? 0 : originTags.size());
        return members.getFirst().id();
    }

    @Override
    public void addDownloadedArtifacts(String recordingId, List<Path> artifacts, Map<String, String> originTags) {
        if (artifacts.isEmpty()) {
            return;
        }
        Recording recording = recordingRepository.findRecording(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("Recording not found: " + recordingId));
        Bundle bundle = bundleForArtifacts(recording);
        List<Recording> members = recordingRepository.findBundleMembers(bundle.id());
        for (Path artifact : artifacts) {
            String filename = artifact.getFileName().toString();
            if (ManagedFile.of(filename).isInitializable()) {
                boolean alreadyStored = members.stream().flatMap(member -> member.files().stream())
                        .anyMatch(file -> file.filename().equals(filename) && file.sizeInBytes() == fileSize(artifact));
                if (alreadyStored) {
                    continue;
                }
                String memberId = IDGenerator.generate();
                Recording member = prepareRecording(memberId, bundle.bundleName() + ARTIFACT_NAME_SEPARATOR + filename,
                        List.of(copyIntoStorage(memberId, artifact)), null, bundle.id());
                recordingRepository.insertRecording(member, member.files().getFirst());
                if (originTags != null && !originTags.isEmpty()) {
                    recordingTagsRepository.insert(memberId, originTags);
                }
            } else {
                StoredFile stored = copyIntoStorage(bundle.id(), artifact);
                String fileId = bundle.files().stream().filter(file -> file.filename().equals(filename))
                        .map(BundleFile::id).findFirst().orElseGet(IDGenerator::generate);
                recordingRepository.upsertBundleFile(new BundleFile(fileId, bundle.id(), filename,
                        ManagedFile.of(filename), clock.instant(), sizeOf(stored)));
            }
        }
    }

    private Bundle bundleForArtifacts(Recording recording) {
        if (recording.inBundle()) {
            return recordingRepository.findBundle(recording.bundleId())
                    .orElseThrow(() -> new IllegalArgumentException("Downloaded recording has no bundle: " + recording.id()));
        }
        Bundle bundle = new Bundle(IDGenerator.generate(), recording.recordingName(), recording.groupId(),
                clock.instant(), List.of());
        recordingRepository.attachRecordingToBundle(recording.id(), bundle);
        return bundle;
    }

    private static long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read downloaded artifact size: " + file, e);
        }
    }

    private StoredFile copyIntoStorage(String ownerId, Path source) {
        String filename = source.getFileName().toString();
        StoredFile stored = new StoredFile(filename, storagePath(ownerId, filename));
        try {
            Files.copy(source, stored.path(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy downloaded file into QA storage: " + filename, e);
        }
        return stored;
    }

    /**
     * The recording a set of stored files becomes, ready to insert: an upload of one file, or one item
     * of a bundle. Its kind is read off the first file — a recording is one format throughout — and,
     * for a recording that is parsed into events, refined by its metadata along with its window.
     *
     * @param recordingName what the recording is called in the list — the file's own name for an
     *                      upload, the session's name for a bundle's recording item
     * @param files         the recording's files, already in storage
     * @param bundleId      the bundle it belongs to, or {@code null} for a standalone recording
     */
    private Recording prepareRecording(
            String recordingId,
            String recordingName,
            List<StoredFile> files,
            String groupId,
            String bundleId) {

        RecordingEventSource eventSource = ManagedFile.of(files.getFirst().filename())
                .profileSource()
                .orElse(RecordingEventSource.UNKNOWN);
        Instant uploadedAt = clock.instant();

        Instant profilingStartedAt = null;
        Instant profilingFinishedAt = null;

        if (PARSED_KINDS.contains(eventSource.kind())) {
            Optional<RecordingMetadata> metadata = metadataParser.parse(files.stream().map(StoredFile::path).toList());
            if (metadata.isPresent()) {
                RecordingMetadata recordingInfo = metadata.get();
                eventSource = recordingInfo.eventSource();
                profilingStartedAt = recordingInfo.recordingStartedAt();
                profilingFinishedAt = recordingInfo.recordingFinishedAt();
            }
        }

        // No file is the primary one — which comes first only decides what a listing shows.
        List<RecordingFile> recordingFiles = files.stream()
                .map(stored -> recordingFile(recordingId, stored, uploadedAt))
                .toList();

        return new Recording(
                recordingId, recordingName, groupId, eventSource, uploadedAt,
                profilingStartedAt, profilingFinishedAt,
                false, null, null, recordingFiles, bundleId);
    }

    /**
     * One row describing a file already sitting in storage.
     *
     * <p>The row carries the file's <em>own</em> name, never the name it happens to sit under:
     * {@link #resolveRecordingFilePath} puts the recording id back in front of it, so a row holding
     * the storage name would resolve to a path with that prefix on it twice. That is what
     * {@link StoredFile} exists to keep from being expressible.
     */
    private RecordingFile recordingFile(String recordingId, StoredFile stored, Instant uploadedAt) {
        return new RecordingFile(
                IDGenerator.generate(), recordingId, stored.filename(),
                ManagedFile.of(stored.filename()),
                uploadedAt, sizeOf(stored));
    }

    private BundleFile bundleFile(String bundleId, StoredFile stored, Instant uploadedAt) {
        return new BundleFile(
                IDGenerator.generate(), bundleId, stored.filename(),
                ManagedFile.of(stored.filename()),
                uploadedAt, sizeOf(stored));
    }

    private static long sizeOf(StoredFile stored) {
        try {
            return Files.size(stored.path());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to get file size: " + stored.filename(), e);
        }
    }

    /**
     * A file uploaded or imported on its own has to become a profile; a log or a crash file never can,
     * so it is refused at the door instead of being stored and failing at initialization.
     */
    private static void requireInitializable(String filename) {
        if (!ManagedFile.of(filename).isInitializable()) {
            throw new IllegalArgumentException("Unsupported recording file type: " + filename
                    + " (expected a JFR recording, a heap dump, a perf-counters snapshot, a pprof or an OpenTelemetry profile)");
        }
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
        if (recording.inBundle()) {
            dropBundleIfEmpty(recording.bundleId());
        }

        LOG.info("Quick analysis recording deleted: recordingId={} bundleId={}", recordingId, recording.bundleId());

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

    // --- Bundle operations ---

    @Override
    public List<Bundle> listBundles() {
        return recordingRepository.findAllBundles();
    }

    @Override
    public Optional<Bundle> findBundle(String bundleId) {
        return recordingRepository.findBundle(bundleId);
    }

    @Override
    public List<Recording> findBundleMembers(String bundleId) {
        return recordingRepository.findBundleMembers(bundleId);
    }

    @Override
    public void moveBundleToGroup(String bundleId, String groupId) {
        LOG.debug("Moving bundle to group: bundleId={} groupId={}", bundleId, groupId);
        recordingRepository.updateBundleGroup(bundleId, groupId);
    }

    @Override
    public void deleteBundle(String bundleId) {
        Bundle bundle = recordingRepository.findBundle(bundleId)
                .orElseThrow(() -> new IllegalArgumentException("Bundle not found: " + bundleId));

        int membersDeleted = deleteBundleInternal(bundle);

        LOG.info("Quick analysis bundle deleted: bundleId={} recordingsDeleted={}", bundleId, membersDeleted);

        // Raised like a group delete: one click took every recording and file of the bundle.
        Notifications.of(NotificationType.BUNDLE_DELETED)
                .attribute("bundleId", bundleId)
                .attribute("bundleName", bundle.bundleName())
                .attribute("recordingsDeleted", membersDeleted)
                .attribute("fileCount", bundle.files().size())
                .emit();
    }

    @Override
    public Optional<Path> findBundleFile(String bundleId, String fileId) {
        return recordingRepository.findBundle(bundleId)
                .flatMap(bundle -> bundle.files().stream()
                        .filter(file -> file.id().equals(fileId))
                        .findFirst()
                        .map(file -> storagePath(bundleId, file.filename())));
    }

    /** Deletes every member with its profile and files, then the bundle's own files and row. */
    private int deleteBundleInternal(Bundle bundle) {
        List<Recording> members = recordingRepository.findBundleMembers(bundle.id());
        for (Recording member : members) {
            deleteRecordingInternal(member);
            recordingRepository.deleteRecordingWithFiles(member.id());
            recordingTagsRepository.deleteForRecording(member.id());
        }
        for (BundleFile file : bundle.files()) {
            FileSystemUtils.removeFile(storagePath(bundle.id(), file.filename()));
        }
        recordingRepository.deleteBundle(bundle.id());
        return members.size();
    }

    /**
     * A bundle whose last item was deleted on its own is gone with it, unless it still holds files to
     * download. Deleting one item never takes the others: it deletes exactly what was named.
     */
    private void dropBundleIfEmpty(String bundleId) {
        recordingRepository.findBundle(bundleId)
                .filter(bundle -> bundle.files().isEmpty())
                .filter(bundle -> recordingRepository.findBundleMembers(bundleId).isEmpty())
                .ifPresent(bundle -> {
                    recordingRepository.deleteBundle(bundleId);
                    LOG.info("Quick analysis bundle removed with its last item: bundleId={}", bundleId);
                });
    }

    // --- Internal helpers ---

    private void deleteRecordingInternal(Recording recording) {
        profileCleanup.onRecordingDeleted(recording);

        for (RecordingFile file : recording.files()) {
            FileSystemUtils.removeFile(resolveRecordingFilePath(file));
        }
    }

    private Path resolveRecordingFilePath(RecordingFile file) {
        return storagePath(file.recordingId(), file.filename());
    }

    /**
     * Where a recording's file sits. One method rather than the layout call at each of the four
     * places that write or read a file; {@link RecordingStorageLayout} owns the convention itself.
     */
    private Path storagePath(String recordingId, String filename) {
        return RecordingStorageLayout.storagePath(recordingsDir, recordingId, filename);
    }

    /**
     * A file of a recording: the name it is known by, and where it actually sits.
     *
     * <p>The two are deliberately carried together rather than derived from each other. They differ
     * by the storage prefix {@link #storagePath} adds, so reading the name back off the path yields
     * the prefixed name — which, put through the same method again, resolves to a path carrying the
     * prefix twice. Keeping both means that mistake has nowhere to happen.
     */
    private record StoredFile(String filename, Path path) {
    }
}
