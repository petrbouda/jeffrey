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

package cafe.jeffrey.hub.core.project.repository;

import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.layout.InstanceState;
import cafe.jeffrey.shared.volume.layout.InstanceSummary;
import cafe.jeffrey.shared.volume.layout.ProjectSessions;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.SessionDeletion;
import cafe.jeffrey.shared.volume.layout.SessionDirectory;
import cafe.jeffrey.shared.volume.layout.SessionFile;
import cafe.jeffrey.shared.volume.layout.VolumeChanges;
import cafe.jeffrey.shared.volume.layout.VolumeMarkers;
import cafe.jeffrey.shared.volume.layout.VolumeSession;
import cafe.jeffrey.shared.volume.liveness.SessionLiveness;
import cafe.jeffrey.shared.volume.recording.RecordingFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A project's repository as it lies on the shared volume, which is all the hub knows of it. What
 * the volume holds is read through {@link ProjectTree}, the layout every reader of the volume
 * shares; what the hub changes on it — retention flags, deletions, compression — is written here,
 * on the filesystem the volume lies on. Nothing is cached; every call reads the directories again.
 *
 * <p>A session's id is its directory name and its lifecycle is read from its liveness files by
 * {@link SessionLiveness} at that moment. A file's id, timestamp source and whether it is a
 * recording come from {@link RecordingFormat#of(String)}: a JFR and its archive are the only types
 * the hub knows. Everything else (logs, heap dumps) is listed and served as it lies.
 */
public class FilesystemRepositoryStorage implements RepositoryStorage {

    private static final Logger LOG = LoggerFactory.getLogger(FilesystemRepositoryStorage.class);

    /** Joins a session's instance and its own directory into its name, relative to the project. */
    private static final String NAME_SEPARATOR = "/";

    private static final Map<InstanceState, ProjectInstanceStatus> INSTANCE_STATUSES = Map.of(
            InstanceState.PENDING, ProjectInstanceStatus.PENDING,
            InstanceState.ACTIVE, ProjectInstanceStatus.ACTIVE,
            InstanceState.FINISHED, ProjectInstanceStatus.FINISHED);

    private final ProjectInfo projectInfo;
    private final ProjectTree tree;
    private final ProjectSessions sessions;
    private final FilesystemVolume volume;

    /**
     * @param tree   the project's directory, as the volume's layout reads it
     * @param volume the filesystem the tree lies on, where the hub's own changes are written
     */
    public FilesystemRepositoryStorage(
            ProjectInfo projectInfo, ProjectTree tree, FilesystemVolume volume, SessionLiveness liveness) {

        this.projectInfo = projectInfo;
        this.tree = tree;
        this.sessions = new ProjectSessions(tree, liveness);
        this.volume = volume;
    }

    // ========== Instances and sessions ==========

    /** The session without its files, as the volume's layout read it at this moment. */
    private static RecordingSession toRecordingSession(VolumeSession session) {
        return new RecordingSession(
                session.id(),
                session.instanceId() + NAME_SEPARATOR + session.id(),
                session.instanceId(),
                session.createdAt(),
                session.lifecycle().finishedAt(),
                session.lifecycle().isRecording() ? RecordingStatus.ACTIVE : RecordingStatus.FINISHED,
                List.of(),
                session.retained(),
                session.lifecycle().heartbeatMissing());
    }

    @Override
    public Optional<RecordingSession> session(String sessionId) {
        return sessions.session(sessionId)
                .map(FilesystemRepositoryStorage::toRecordingSession)
                .map(this::withFiles);
    }

    @Override
    public List<RecordingSession> listSessions(SessionDetail detail) {
        return sessions.all().stream()
                .map(FilesystemRepositoryStorage::toRecordingSession)
                .map(session -> detail.withFiles() ? withFiles(session) : session)
                .toList();
    }

    @Override
    public RecordingSession withFiles(RecordingSession session) {
        VolumePath sessionDir = tree.path().resolve(session.instanceId()).resolve(session.id());
        ZoneId zone = tree.sessionAt(sessionDir).map(SessionDirectory::zone).orElse(ZoneOffset.UTC);
        List<RepositoryFile> files = tree.files(sessionDir, zone).stream()
                .map(this::toRepositoryFile)
                .toList();
        return new RecordingSession(
                session.id(),
                session.name(),
                session.instanceId(),
                session.createdAt(),
                session.finishedAt(),
                session.status(),
                files,
                session.retained(),
                session.heartbeatMissing());
    }

    private RepositoryFile toRepositoryFile(SessionFile file) {
        return new RepositoryFile(
                file.id(), file.name(), file.createdAt(), file.size(), file.recording(), volume.resolve(file.path()));
    }

    @Override
    public List<ProjectInstanceInfo> instances() {
        return sessions.instances().stream()
                .map(FilesystemRepositoryStorage::instanceInfo)
                .toList();
    }

    @Override
    public Optional<InstanceSessions> instanceWithSessions(String instanceId) {
        return sessions.instance(instanceId).map(found -> new InstanceSessions(
                instanceInfo(found.summary()),
                found.sessions().stream().map(FilesystemRepositoryStorage::toRecordingSession).toList()));
    }

    private static ProjectInstanceInfo instanceInfo(InstanceSummary summary) {
        return new ProjectInstanceInfo(
                summary.instance().id(),
                INSTANCE_STATUSES.get(summary.state()),
                summary.instance().createdAt(),
                summary.finishedAt().orElse(null),
                summary.sessionCount());
    }

    @Override
    public boolean setRetained(String sessionId, boolean retained) {
        if (!VolumeChanges.setRetained(volume, tree, sessionId, retained)) {
            return false;
        }
        LOG.info("Updated session retention: project_id={} session_id={} retained={}",
                projectInfo.id(), sessionId, retained);
        return true;
    }

    /**
     * Deletes the named files of a session, matched against the session's own listing. Never
     * resolve a path from an id: a recording's id has no extension, so it names no file, and an id
     * holding {@code ../} would walk out of the session directory.
     */
    @Override
    public void deleteRepositoryFiles(String sessionId, List<String> fileIds) {
        Optional<SessionDirectory> session = tree.session(sessionId);
        if (session.isEmpty()) {
            return;
        }
        Path sessionPath = volume.resolve(session.get().path());

        Set<String> requestedIds = Set.copyOf(fileIds);
        List<Path> matched;
        try (Stream<Path> files = Files.list(sessionPath)) {
            matched = files
                    .filter(Files::isRegularFile)
                    .filter(file -> requestedIds.contains(fileId(file)))
                    .toList();
        } catch (IOException e) {
            LOG.warn("Cannot list a session directory to delete files from it: session_path={}", sessionPath, e);
            return;
        }

        // Compared by id, not by count: during a compression a recording and its archive share an id
        Set<String> found = matched.stream()
                .map(FilesystemRepositoryStorage::fileId)
                .collect(Collectors.toSet());
        List<String> missing = requestedIds.stream().filter(id -> !found.contains(id)).toList();

        if (!missing.isEmpty()) {
            LOG.warn("Session holds no file with these ids, nothing deleted for them: session_id={} file_ids={}",
                    sessionId, missing);
        }

        for (Path file : matched) {
            FileSystemUtils.removeFile(file);
        }

        LOG.info("Deleted files in repository session: session_path={} file_ids={}", sessionPath, found);
    }

    private static String fileId(Path file) {
        return SessionFile.idOf(file.getFileName().toString());
    }

    /** The instance goes too when no directory is left in it ({@link VolumeChanges#deleteSession}). */
    @Override
    public SessionDeletion deleteSession(String sessionId) {
        SessionDeletion deletion = VolumeChanges.deleteSession(volume, tree, sessionId);
        if (deletion.deleted()) {
            LOG.info("Deleted session directory: session_id={} instance_deleted={}",
                    sessionId, deletion.instanceDeleted());
        }
        return deletion;
    }

    @Override
    public void deleteInstanceDirectory(String instanceId) {
        Optional<VolumePath> instanceDir = tree.instanceDirectory(instanceId);
        if (instanceDir.isEmpty()) {
            return;
        }
        Path instancePath = volume.resolve(instanceDir.get());
        FileSystemUtils.removeDirectory(instancePath);
        LOG.info("Deleted instance directory: instance_path={}", instancePath);
    }

    @Override
    public void deleteProjectDirectory() {
        if (!VolumeMarkers.isDirectory(volume, tree.path())) {
            return;
        }
        volume.delete(tree.path());
        LOG.info("Deleted project directory: project_path={}", tree.path());
    }

    // ========== Files ==========

    @Override
    public FileLookup file(String sessionId, String fileId) {
        RecordingSession session = resolveSession(sessionId);

        Optional<RepositoryFile> match = session.files().stream()
                .filter(candidate -> candidate.id().equals(fileId))
                .reduce(FilesystemRepositoryStorage::theOneThatStays);
        if (match.isEmpty()) {
            return new FileLookup.NotInSession(sessionId, fileId);
        }

        RepositoryFile file = match.get();
        if (session.isOpen(file)) {
            return new FileLookup.StillWriting(file.name());
        }
        if (!Files.isRegularFile(file.filePath())) {
            return new FileLookup.Vanished(sessionId, file.name());
        }
        if (file.isRecordingFile() && !file.hasContent()) {
            return new FileLookup.EmptyRecording(file.name());
        }
        return new FileLookup.Found(file.filePath());
    }

    /**
     * Which of two files sharing an id to serve: the archive. While the compression job runs, a
     * recording and its archive share an id and both are whole (the archive is published by a
     * rename); the recording is about to be deleted, the archive stays.
     */
    private static RepositoryFile theOneThatStays(RepositoryFile first, RepositoryFile second) {
        return RecordingFormat.of(first.name()).filter(RecordingFormat::isArchive).isPresent() ? first : second;
    }

    // ========== Session Compression ==========

    @Override
    public int compressSession(String sessionId) {
        RecordingSession session = resolveSession(sessionId);

        // Every closed chunk; for a live session, all but the one the profiler is still writing
        return (int) session.finishedRecordings().stream()
                .map(file -> compress(sessionId, file))
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    // ========== Private Helpers ==========

    private RecordingSession resolveSession(String sessionId) {
        return session(sessionId).orElseThrow(() -> Exceptions.recordingSessionNotFound(sessionId));
    }

    /**
     * Compresses one closed recording in place, replacing it with its LZ4 archive. Reached only from
     * {@link #compressSession} — never compress on a read path.
     *
     * <p>The source is deleted only once the archive exists, and {@link Compression#compress}
     * publishes the archive whole by a rename. That makes the "archive already exists" shortcut
     * safe, including for two overlapping runs over the same session.
     */
    private Path compress(String sessionId, RepositoryFile file) {
        // Only recordings reach here, so a format without compression is an archive already
        Optional<Compression> compressible = RecordingFormat.of(file.name()).flatMap(Compression::of);
        if (compressible.isEmpty()) {
            LOG.debug("Leaving an already compressed recording alone: session_id={} file={}", sessionId, file.name());
            return file.filePath();
        }
        Compression compression = compressible.get();

        Path sourcePath = file.filePath();
        Path compressedPath = compression.target(sourcePath);

        // Already compressed, here or by an overlapping run; the archive is whole by construction
        if (Files.exists(compressedPath)) {
            FileSystemUtils.removeFile(sourcePath);
            return compressedPath;
        }

        // Skip empty recording files left when the profiler stops before writing events,
        // for example after a non-graceful shutdown.
        if (FileSystemUtils.size(sourcePath) == 0) {
            LOG.debug("Skipping empty recording file: session_id={} file={}", sessionId, sourcePath);
            return null;
        }

        compression.compress(sourcePath, compressedPath);
        FileSystemUtils.removeFile(sourcePath);
        return compressedPath;
    }
}
