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

import cafe.jeffrey.hub.core.volume.SessionLifecycle;
import cafe.jeffrey.hub.core.volume.SessionLiveness;
import cafe.jeffrey.hub.core.volume.VolumeMarkers;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstance;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.DateTimeException;
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
 * A project's repository as it lies on the shared volume, which is all the hub knows of it:
 * instance directories with their {@code .instance-info.json}, session directories under them
 * with their {@code .session-info.json}, and the files a profiler left behind. Nothing is cached;
 * every call reads the directories again.
 *
 * <p>A session's id is its directory name and its lifecycle is read from its liveness files by
 * {@link SessionLiveness} at that moment. A file's id, timestamp source and compressibility come
 * from {@link HubManagedFile#of(String)}: a JFR and its archive are the only types the hub knows.
 * Everything else (logs, heap dumps) is listed and served as it lies.
 */
public class FilesystemRepositoryStorage implements RepositoryStorage {

    private static final Logger LOG = LoggerFactory.getLogger(FilesystemRepositoryStorage.class);

    /** The flag file that pins a session against every retention rule. */
    static final String RETAINED_MARKER = ".retained";

    /**
     * How a session directory is listed: newest first by filename. Presentation only — which chunk
     * the profiler still holds open is derived by {@code RecordingSession}, never from this order.
     */
    private static final Comparator<Path> NEWEST_BY_NAME =
            Comparator.comparing((Path file) -> file.getFileName().toString()).reversed();

    private static final Comparator<RecordingSession> NEWEST_FIRST =
            Comparator.comparing(RecordingSession::createdAt).reversed();

    private final ProjectInfo projectInfo;
    private final Path projectDir;
    private final SessionLiveness liveness;

    public FilesystemRepositoryStorage(ProjectInfo projectInfo, Path projectDir, SessionLiveness liveness) {
        this.projectInfo = projectInfo;
        this.projectDir = projectDir;
        this.liveness = liveness;
    }

    // ========== Instances and sessions ==========

    /**
     * An instance directory whose marker reads. The one test of "is this an instance", shared by
     * every lookup and listing, so none of them disagrees about a directory with a broken marker.
     */
    private record InstanceDirectory(Path path, Instant createdAt) {

        String id() {
            return path.getFileName().toString();
        }
    }

    /**
     * A session directory whose marker reads — likewise the one test of "is this a session".
     *
     * @param zone the zone the session's machine runs in, which its recordings' names are local to
     */
    private record SessionDirectory(Path path, Instant createdAt, ZoneId zone) {

        String id() {
            return path.getFileName().toString();
        }

        String instanceId() {
            return path.getParent().getFileName().toString();
        }
    }

    private static Optional<InstanceDirectory> instanceAt(Path dir) {
        return VolumeMarkers.read(dir, JeffreyLayout.INSTANCE_INFO_FILE, RemoteProjectInstance.class)
                .map(marker -> new InstanceDirectory(dir, Instant.ofEpochMilli(marker.createdAt())));
    }

    private static Optional<SessionDirectory> sessionAt(Path dir) {
        return VolumeMarkers.read(dir, JeffreyLayout.SESSION_INFO_FILE, RemoteProjectInstanceSession.class)
                .map(marker -> new SessionDirectory(
                        dir, Instant.ofEpochMilli(marker.createdAt()), zoneOf(marker, dir)));
    }

    /**
     * The zone a session's recording names are local to: UTC for a marker written before the zone
     * was recorded, which is what those names were always read as, and for one that names no zone
     * this JVM knows.
     */
    private static ZoneId zoneOf(RemoteProjectInstanceSession marker, Path dir) {
        if (marker.zoneId() == null || marker.zoneId().isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(marker.zoneId());
        } catch (DateTimeException e) {
            LOG.debug("Session marker names an unknown time zone, reading its recordings in UTC: "
                    + "session_path={} zone_id={}", dir, marker.zoneId());
            return ZoneOffset.UTC;
        }
    }

    private List<InstanceDirectory> instanceDirectories() {
        return VolumeMarkers.childDirectories(projectDir).stream()
                .map(FilesystemRepositoryStorage::instanceAt)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * The directory {@code name} directly under {@code parent}, or empty for a name that is not one
     * plain path element — ids arrive over gRPC and must not walk out of the project.
     */
    private static Optional<Path> child(Path parent, String name) {
        if (name == null || name.isBlank() || name.startsWith(".")) {
            return Optional.empty();
        }
        Path candidate = parent.resolve(name).normalize();
        if (!parent.equals(candidate.getParent()) || !Files.isDirectory(candidate)) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }

    private Optional<InstanceDirectory> instanceDirectory(String instanceId) {
        return child(projectDir, instanceId).flatMap(FilesystemRepositoryStorage::instanceAt);
    }

    /** A session lies in one of the instance directories, under its own id. */
    private Optional<SessionDirectory> sessionDirectory(String sessionId) {
        return instanceDirectories().stream()
                .map(instance -> child(instance.path(), sessionId).flatMap(FilesystemRepositoryStorage::sessionAt))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static List<SessionDirectory> sessionsOldestFirst(Path instanceDir) {
        return VolumeMarkers.childDirectories(instanceDir).stream()
                .map(FilesystemRepositoryStorage::sessionAt)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(SessionDirectory::createdAt))
                .toList();
    }

    /** When the session after this one in its instance started, or {@code null} for the newest. */
    private static Instant nextStart(List<SessionDirectory> oldestFirst, Path sessionPath) {
        for (int i = 0; i < oldestFirst.size() - 1; i++) {
            if (oldestFirst.get(i).path().equals(sessionPath)) {
                return oldestFirst.get(i + 1).createdAt();
            }
        }
        return null;
    }

    /**
     * An instance's sessions, newest first. Read together because a session's lifecycle depends on
     * the start of the next one: a newer session in the same instance means this one ended.
     */
    private List<RecordingSession> sessionsOf(InstanceDirectory instance) {
        List<SessionDirectory> oldestFirst = sessionsOldestFirst(instance.path());
        return oldestFirst.stream()
                .map(session -> toRecordingSession(session, nextStart(oldestFirst, session.path())))
                .toList()
                .reversed();
    }

    /** The session without its files, its lifecycle read from its liveness files at this moment. */
    private RecordingSession toRecordingSession(SessionDirectory session, Instant nextStart) {
        SessionLifecycle lifecycle = liveness.assess(session.path(), session.createdAt(), nextStart);
        return new RecordingSession(
                session.id(),
                projectDir.relativize(session.path()).toString(),
                session.instanceId(),
                session.createdAt(),
                lifecycle.finishedAt(),
                lifecycle.isRecording() ? RecordingStatus.ACTIVE : RecordingStatus.FINISHED,
                List.of(),
                VolumeMarkers.hasFlag(session.path(), RETAINED_MARKER),
                lifecycle.heartbeatMissing());
    }

    /**
     * Only this session's liveness files are read; its siblings are needed for their start times
     * alone, which their markers give.
     */
    @Override
    public Optional<RecordingSession> session(String sessionId) {
        return sessionDirectory(sessionId).map(session -> {
            List<SessionDirectory> siblings = sessionsOldestFirst(session.path().getParent());
            return withFiles(toRecordingSession(session, nextStart(siblings, session.path())));
        });
    }

    @Override
    public List<RecordingSession> listSessions(SessionDetail detail) {
        return instanceDirectories().stream()
                .flatMap(instance -> sessionsOf(instance).stream())
                .map(session -> detail.withFiles() ? withFiles(session) : session)
                .sorted(NEWEST_FIRST)
                .toList();
    }

    @Override
    public RecordingSession withFiles(RecordingSession session) {
        Path sessionPath = projectDir.resolve(session.name());
        ZoneId zone = sessionAt(sessionPath).map(SessionDirectory::zone).orElse(ZoneOffset.UTC);
        return new RecordingSession(
                session.id(),
                session.name(),
                session.instanceId(),
                session.createdAt(),
                session.finishedAt(),
                session.status(),
                listFiles(sessionPath, zone),
                session.retained(),
                session.heartbeatMissing());
    }

    /**
     * Every instance, each read off its newest session alone: a newer session is what finishes the
     * one before it, so only the newest can still be recording and the older sessions' liveness
     * files are never read here. Their markers give the count.
     */
    @Override
    public List<ProjectInstanceInfo> instances() {
        return instanceDirectories().stream()
                .map(instance -> {
                    List<SessionDirectory> oldestFirst = sessionsOldestFirst(instance.path());
                    Optional<RecordingSession> newest = oldestFirst.isEmpty()
                            ? Optional.empty()
                            : Optional.of(toRecordingSession(oldestFirst.getLast(), null));
                    return instanceInfo(instance, oldestFirst.size(), newest);
                })
                .toList();
    }

    @Override
    public Optional<InstanceSessions> instanceWithSessions(String instanceId) {
        return instanceDirectory(instanceId).map(instance -> {
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
        Optional<SessionDirectory> session = sessionDirectory(sessionId);
        if (session.isEmpty()) {
            return false;
        }
        VolumeMarkers.setFlag(session.get().path(), RETAINED_MARKER, retained);
        LOG.info("Updated session retention: project_id={} session_id={} retained={}",
                projectInfo.id(), sessionId, retained);
        return true;
    }

    /**
     * One file of a session as the repository reports it, or {@code null} when it is gone.
     *
     * <p>A session directory changes while it is listed: async-profiler deletes its {@code .jfr.N~}
     * cache after each flush and the compression job replaces recordings with archives. A file that
     * vanished between listing and stat is left out rather than failing the whole listing.
     */
    RepositoryFile describe(Path file, Path sessionPath, ZoneId zone) {
        String sourceName = sessionPath.relativize(file).toString();
        Optional<HubManagedFile> recording = HubManagedFile.of(sourceName);
        try {
            // One stat answers the size and, for a file whose name says nothing, the timestamp
            BasicFileAttributes attributes = FileSystemUtils.readAttributes(file);
            if (!attributes.isRegularFile()) {
                return null;
            }
            return new RepositoryFile(
                    fileId(file),
                    sourceName,
                    recording.map(HubManagedFile::timestampResolver)
                            .orElse(TimestampResolver.FILESYSTEM)
                            .resolve(file, zone, () -> attributes),
                    attributes.size(),
                    recording.isPresent(),
                    file);
        } catch (UncheckedIOException e) {
            // Only an I/O failure means "vanished"; any other exception from a resolver is a bug
            LOG.debug("Leaving out a repository file that can no longer be described: file={} reason={}",
                    file, e.getMessage());
            return null;
        }
    }

    /**
     * A file's id within its session: a recording drops its extension so the id survives
     * compression; anything else keeps its whole name. {@link #describe} hands ids out and
     * {@link #deleteRepositoryFiles} matches against them, so both must use this.
     */
    private static String fileId(Path file) {
        return HubManagedFile.of(file)
                .map(type -> type.idOf(file))
                .orElseGet(() -> file.getFileName().toString());
    }

    /**
     * Deletes the named files of a session, matched against the session's own listing. Never
     * resolve a path from an id: a recording's id has no extension, so it names no file, and an id
     * holding {@code ../} would walk out of the session directory.
     */
    @Override
    public void deleteRepositoryFiles(String sessionId, List<String> fileIds) {
        Optional<SessionDirectory> session = sessionDirectory(sessionId);
        if (session.isEmpty()) {
            return;
        }
        Path sessionPath = session.get().path();

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

    /**
     * The instance goes too when no directory is left in it. Any directory counts, marker or not:
     * one the provisioner has created but not yet described is a session about to start, and its
     * profiler may already be writing into it.
     */
    @Override
    public SessionDeletion deleteSession(String sessionId) {
        Optional<SessionDirectory> session = sessionDirectory(sessionId);
        if (session.isEmpty()) {
            return SessionDeletion.NOT_FOUND;
        }
        Path sessionPath = session.get().path();
        Path instanceDir = sessionPath.getParent();
        FileSystemUtils.removeDirectory(sessionPath);
        LOG.info("Deleted session directory: session_id={} session_path={}", sessionId, sessionPath);

        if (!VolumeMarkers.childDirectories(instanceDir).isEmpty()) {
            return SessionDeletion.SESSION_DELETED;
        }
        FileSystemUtils.removeDirectory(instanceDir);
        LOG.info("Deleted instance directory with its last session: instance_path={}", instanceDir);
        return SessionDeletion.SESSION_AND_INSTANCE_DELETED;
    }

    @Override
    public void deleteInstanceDirectory(String instanceId) {
        Optional<Path> instancePath = child(projectDir, instanceId);
        if (instancePath.isEmpty()) {
            return;
        }
        FileSystemUtils.removeDirectory(instancePath.get());
        LOG.info("Deleted instance directory: instance_path={}", instancePath.get());
    }

    @Override
    public void deleteProjectDirectory() {
        if (!Files.isDirectory(projectDir)) {
            return;
        }
        FileSystemUtils.removeDirectory(projectDir);
        LOG.info("Deleted project directory: project_path={}", projectDir);
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
        return HubManagedFile.of(first.name()).filter(HubManagedFile::isArchive).isPresent() ? first : second;
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
        // Only recordings reach here, so a type without compression is an archive already
        Optional<Compression> compressible = HubManagedFile.of(file.name()).flatMap(HubManagedFile::compression);
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

    private List<RepositoryFile> listFiles(Path sessionPath, ZoneId zone) {
        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: session_path={}", sessionPath);
            return List.of();
        }

        return FileSystemUtils.sortedFilesInDirectory(sessionPath, NEWEST_BY_NAME).stream()
                .filter(FileSystemUtils::isNotHidden)
                .map(file -> describe(file, sessionPath, zone))
                .filter(Objects::nonNull)
                .toList();
    }
}
