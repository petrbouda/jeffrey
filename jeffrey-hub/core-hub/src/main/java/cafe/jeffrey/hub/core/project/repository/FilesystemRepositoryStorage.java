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

import cafe.jeffrey.hub.core.volume.VolumeMarkerWriter;
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
import cafe.jeffrey.shared.volume.layout.InstanceDirectory;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.SessionDirectory;
import cafe.jeffrey.shared.volume.layout.SessionFile;
import cafe.jeffrey.shared.volume.liveness.SessionLifecycle;
import cafe.jeffrey.shared.volume.liveness.SessionLiveness;
import cafe.jeffrey.shared.volume.recording.RecordingFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
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

    private static final Comparator<RecordingSession> NEWEST_FIRST =
            Comparator.comparing(RecordingSession::createdAt).reversed();

    private final ProjectInfo projectInfo;
    private final ProjectTree tree;
    private final FilesystemVolume volume;
    private final SessionLiveness liveness;

    /**
     * @param tree   the project's directory, as the volume's layout reads it
     * @param volume the filesystem the tree lies on, where the hub's own changes are written
     */
    public FilesystemRepositoryStorage(
            ProjectInfo projectInfo, ProjectTree tree, FilesystemVolume volume, SessionLiveness liveness) {

        this.projectInfo = projectInfo;
        this.tree = tree;
        this.volume = volume;
        this.liveness = liveness;
    }

    // ========== Instances and sessions ==========

    /**
     * An instance's sessions, newest first. Read together because a session's lifecycle depends on
     * the start of the next one: a newer session in the same instance means this one ended.
     */
    private List<RecordingSession> sessionsOf(InstanceDirectory instance) {
        List<SessionDirectory> oldestFirst = tree.sessionsOldestFirst(instance.path());
        return oldestFirst.stream()
                .map(session -> toRecordingSession(session, ProjectTree.nextStart(oldestFirst, session)))
                .toList()
                .reversed();
    }

    /** The session without its files, its lifecycle read from its liveness files at this moment. */
    private RecordingSession toRecordingSession(SessionDirectory session, Instant nextStart) {
        SessionLifecycle lifecycle = liveness.assess(session.path(), session.createdAt(), nextStart);
        return new RecordingSession(
                session.id(),
                session.instanceId() + NAME_SEPARATOR + session.id(),
                session.instanceId(),
                session.createdAt(),
                lifecycle.finishedAt(),
                lifecycle.isRecording() ? RecordingStatus.ACTIVE : RecordingStatus.FINISHED,
                List.of(),
                tree.isRetained(session),
                lifecycle.heartbeatMissing());
    }

    /**
     * Only this session's liveness files are read; its siblings are needed for their start times
     * alone, which their markers give.
     */
    @Override
    public Optional<RecordingSession> session(String sessionId) {
        return tree.session(sessionId).map(session -> {
            List<SessionDirectory> siblings = tree.sessionsOldestFirst(session.path().parent());
            return withFiles(toRecordingSession(session, ProjectTree.nextStart(siblings, session)));
        });
    }

    @Override
    public List<RecordingSession> listSessions(SessionDetail detail) {
        return tree.instances().stream()
                .flatMap(instance -> sessionsOf(instance).stream())
                .map(session -> detail.withFiles() ? withFiles(session) : session)
                .sorted(NEWEST_FIRST)
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

    /**
     * Every instance, each read off its newest session alone: a newer session is what finishes the
     * one before it, so only the newest can still be recording and the older sessions' liveness
     * files are never read here. Their markers give the count.
     */
    @Override
    public List<ProjectInstanceInfo> instances() {
        return tree.instances().stream()
                .map(instance -> {
                    List<SessionDirectory> oldestFirst = tree.sessionsOldestFirst(instance.path());
                    Optional<RecordingSession> newest = oldestFirst.isEmpty()
                            ? Optional.empty()
                            : Optional.of(toRecordingSession(oldestFirst.getLast(), null));
                    return instanceInfo(instance, oldestFirst.size(), newest);
                })
                .toList();
    }

    @Override
    public Optional<InstanceSessions> instanceWithSessions(String instanceId) {
        return tree.instance(instanceId).map(instance -> {
            List<RecordingSession> sessions = sessionsOf(instance);
            return new InstanceSessions(
                    instanceInfo(instance, sessions.size(), sessions.stream().findFirst()), sessions);
        });
    }

    /** PENDING without a session; otherwise ACTIVE while the newest records, FINISHED once it ended. */
    private static ProjectInstanceInfo instanceInfo(
            InstanceDirectory instance, int sessionCount, Optional<RecordingSession> newest) {

        ProjectInstanceStatus status = newest
                .map(session -> session.status() == RecordingStatus.ACTIVE
                        ? ProjectInstanceStatus.ACTIVE
                        : ProjectInstanceStatus.FINISHED)
                .orElse(ProjectInstanceStatus.PENDING);
        Instant finishedAt = newest.map(RecordingSession::finishedAt).orElse(null);
        return new ProjectInstanceInfo(instance.id(), status, instance.createdAt(), finishedAt, sessionCount);
    }

    @Override
    public boolean setRetained(String sessionId, boolean retained) {
        Optional<SessionDirectory> session = tree.session(sessionId);
        if (session.isEmpty()) {
            return false;
        }
        VolumeMarkerWriter.setFlag(volume.resolve(session.get().path()), SessionDirectory.RETAINED_FLAG, retained);
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

    /**
     * The instance goes too when no directory is left in it. Any directory counts, marker or not:
     * one the provisioner has created but not yet described is a session about to start, and its
     * profiler may already be writing into it.
     */
    @Override
    public SessionDeletion deleteSession(String sessionId) {
        Optional<SessionDirectory> session = tree.session(sessionId);
        if (session.isEmpty()) {
            return SessionDeletion.NOT_FOUND;
        }
        VolumePath instanceDir = session.get().path().parent();
        Path sessionPath = volume.resolve(session.get().path());
        FileSystemUtils.removeDirectory(sessionPath);
        LOG.info("Deleted session directory: session_id={} session_path={}", sessionId, sessionPath);

        if (tree.holdsAnyDirectory(instanceDir)) {
            return SessionDeletion.SESSION_DELETED;
        }
        Path instancePath = volume.resolve(instanceDir);
        FileSystemUtils.removeDirectory(instancePath);
        LOG.info("Deleted instance directory with its last session: instance_path={}", instancePath);
        return SessionDeletion.SESSION_AND_INSTANCE_DELETED;
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
        Path projectPath = volume.resolve(tree.path());
        if (!Files.isDirectory(projectPath)) {
            return;
        }
        FileSystemUtils.removeDirectory(projectPath);
        LOG.info("Deleted project directory: project_path={}", projectPath);
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
