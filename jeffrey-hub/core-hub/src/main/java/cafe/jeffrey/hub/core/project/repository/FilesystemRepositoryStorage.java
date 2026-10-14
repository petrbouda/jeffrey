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
import java.time.Instant;
import java.util.ArrayList;
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

    /** A session directory and its marker, before its lifecycle is read. */
    private record SessionDirectory(Path path, Instant createdAt) {
    }

    private List<Path> instanceDirectories() {
        return VolumeMarkers.childDirectories(projectDir).stream()
                .filter(dir -> Files.isRegularFile(dir.resolve(JeffreyLayout.INSTANCE_INFO_FILE)))
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

    private Optional<Path> instanceDirectory(String instanceId) {
        return child(projectDir, instanceId)
                .filter(dir -> Files.isRegularFile(dir.resolve(JeffreyLayout.INSTANCE_INFO_FILE)));
    }

    /**
     * The directory of a session this project holds, or empty when there is none: a session lies
     * in one of the instance directories under its own id.
     */
    private Optional<Path> sessionDirectory(String sessionId) {
        for (Path instanceDir : instanceDirectories()) {
            Optional<Path> sessionDir = child(instanceDir, sessionId)
                    .filter(dir -> Files.isRegularFile(dir.resolve(JeffreyLayout.SESSION_INFO_FILE)));
            if (sessionDir.isPresent()) {
                return sessionDir;
            }
        }
        return Optional.empty();
    }

    private static List<SessionDirectory> sessionDirectoriesOldestFirst(Path instanceDir) {
        return VolumeMarkers.childDirectories(instanceDir).stream()
                .map(dir -> VolumeMarkers.read(dir, JeffreyLayout.SESSION_INFO_FILE, RemoteProjectInstanceSession.class)
                        .map(marker -> new SessionDirectory(dir, Instant.ofEpochMilli(marker.createdAt()))))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(SessionDirectory::createdAt))
                .toList();
    }

    /**
     * One instance's sessions, newest first. Read together because a session's lifecycle depends
     * on the start of the next one: a newer session in the same instance means this one ended.
     */
    private List<RecordingSession> instanceSessions(Path instanceDir, SessionDetail detail) {
        String instanceId = instanceDir.getFileName().toString();
        List<SessionDirectory> oldestFirst = sessionDirectoriesOldestFirst(instanceDir);
        List<RecordingSession> sessions = new ArrayList<>(oldestFirst.size());
        for (int i = 0; i < oldestFirst.size(); i++) {
            SessionDirectory session = oldestFirst.get(i);
            Instant nextStart = i + 1 < oldestFirst.size() ? oldestFirst.get(i + 1).createdAt() : null;
            sessions.add(toRecordingSession(instanceId, session, nextStart, detail));
        }
        return sessions.reversed();
    }

    private RecordingSession toRecordingSession(
            String instanceId, SessionDirectory session, Instant nextStart, SessionDetail detail) {

        SessionLifecycle lifecycle = liveness.assess(session.path(), session.createdAt(), nextStart);
        List<RepositoryFile> files = detail.withFiles() ? _listRepositoryFiles(session.path()) : List.of();
        return new RecordingSession(
                session.path().getFileName().toString(),
                projectDir.relativize(session.path()).toString(),
                instanceId,
                session.createdAt(),
                lifecycle.finishedAt(),
                lifecycle.isRecording() ? RecordingStatus.ACTIVE : RecordingStatus.FINISHED,
                files,
                VolumeMarkers.hasFlag(session.path(), RETAINED_MARKER),
                lifecycle.heartbeatMissing());
    }

    @Override
    public Optional<RecordingSession> singleSession(String sessionId, SessionDetail detail) {
        return sessionDirectory(sessionId)
                .flatMap(sessionDir -> instanceSessions(sessionDir.getParent(), SessionDetail.HEADERS).stream()
                        .filter(session -> session.id().equals(sessionId))
                        .findFirst())
                .map(session -> detail.withFiles() ? withFiles(session) : session);
    }

    @Override
    public List<RecordingSession> listSessions(SessionDetail detail) {
        return instanceDirectories().stream()
                .flatMap(instanceDir -> instanceSessions(instanceDir, detail).stream())
                .sorted(NEWEST_FIRST)
                .toList();
    }

    @Override
    public List<RecordingSession> listSessionsByInstanceId(String instanceId, SessionDetail detail) {
        return instanceDirectory(instanceId)
                .map(instanceDir -> instanceSessions(instanceDir, detail))
                .orElse(List.of());
    }

    @Override
    public RecordingSession withFiles(RecordingSession session) {
        Path sessionPath = projectDir.resolve(session.name());
        return new RecordingSession(
                session.id(),
                session.name(),
                session.instanceId(),
                session.createdAt(),
                session.finishedAt(),
                session.status(),
                _listRepositoryFiles(sessionPath),
                session.retained(),
                session.heartbeatMissing());
    }

    @Override
    public List<ProjectInstanceInfo> instances() {
        return instanceDirectories().stream()
                .map(this::instanceOf)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<ProjectInstanceInfo> instance(String instanceId) {
        return instanceDirectory(instanceId).flatMap(this::instanceOf);
    }

    /**
     * An instance, its status read off its newest session: PENDING without one, ACTIVE while the
     * newest records, FINISHED (when the newest finished) otherwise. Only the newest can still be
     * recording — a newer session in the same instance is what finishes the one before it — so
     * the older sessions' liveness files are never read here; their markers give the count.
     */
    private Optional<ProjectInstanceInfo> instanceOf(Path instanceDir) {
        return VolumeMarkers.read(instanceDir, JeffreyLayout.INSTANCE_INFO_FILE, RemoteProjectInstance.class)
                .map(marker -> {
                    List<SessionDirectory> oldestFirst = sessionDirectoriesOldestFirst(instanceDir);
                    Instant startedAt = Instant.ofEpochMilli(marker.createdAt());
                    String id = instanceDir.getFileName().toString();
                    if (oldestFirst.isEmpty()) {
                        return new ProjectInstanceInfo(id, ProjectInstanceStatus.PENDING, startedAt, null, 0);
                    }
                    SessionDirectory newest = oldestFirst.getLast();
                    SessionLifecycle lifecycle = liveness.assess(newest.path(), newest.createdAt(), null);
                    ProjectInstanceStatus status = lifecycle.isRecording()
                            ? ProjectInstanceStatus.ACTIVE
                            : ProjectInstanceStatus.FINISHED;
                    return new ProjectInstanceInfo(id, status, startedAt, lifecycle.finishedAt(), oldestFirst.size());
                });
    }

    @Override
    public boolean setRetained(String sessionId, boolean retained) {
        Optional<Path> sessionDir = sessionDirectory(sessionId);
        if (sessionDir.isEmpty()) {
            return false;
        }
        VolumeMarkers.setFlag(sessionDir.get(), RETAINED_MARKER, retained);
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
    RepositoryFile describe(Path file, Path sessionPath) {
        String sourceName = sessionPath.relativize(file).toString();
        Optional<HubManagedFile> recording = HubManagedFile.of(sourceName);
        try {
            // One stat answers the size and, for a file whose name says nothing, the timestamp
            BasicFileAttributes attributes = FileSystemUtils.readAttributes(file);
            if (!attributes.isRegularFile()) {
                return null;
            }
            return new RepositoryFile(
                    recording.map(type -> type.idOf(file)).orElse(sourceName),
                    sourceName,
                    recording.map(HubManagedFile::timestampResolver)
                            .orElse(TimestampResolver.FILESYSTEM)
                            .resolve(file, () -> attributes),
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
    public void deleteRepositoryFiles(String sessionId, List<String> sessionFileIds) {
        Optional<Path> directory = sessionDirectory(sessionId);
        if (directory.isEmpty()) {
            return;
        }
        Path sessionPath = directory.get();

        Set<String> requestedIds = Set.copyOf(sessionFileIds);
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

    @Override
    public boolean deleteSession(String sessionId) {
        Optional<Path> directory = sessionDirectory(sessionId);
        if (directory.isEmpty()) {
            return false;
        }
        FileSystemUtils.removeDirectory(directory.get());
        LOG.info("Deleted session directory: session_id={} session_path={}", sessionId, directory.get());
        return true;
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
        Optional<RecordingSession> sessionOpt = singleSession(sessionId, SessionDetail.WITH_FILES);
        if (sessionOpt.isEmpty()) {
            throw Exceptions.recordingSessionNotFound(sessionId);
        }
        return sessionOpt.get();
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

    private List<RepositoryFile> _listRepositoryFiles(Path sessionPath) {

        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: session_path={}", sessionPath);
            return List.of();
        }

        return FileSystemUtils.sortedFilesInDirectory(sessionPath, NEWEST_BY_NAME).stream()
                .filter(FileSystemUtils::isNotHidden)
                .map(file -> describe(file, sessionPath))
                .filter(Objects::nonNull)
                .toList();
    }
}
