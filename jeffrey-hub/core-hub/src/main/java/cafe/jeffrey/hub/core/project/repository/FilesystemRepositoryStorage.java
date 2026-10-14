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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.hub.core.project.session.SessionPaths;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.hub.model.RepositoryInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import cafe.jeffrey.hub.model.repository.RepositoryFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A project's repository as it lies on the shared volume: instance directories, session
 * directories under them, and the files a profiler left behind.
 *
 * <p>A file's id, timestamp source and compressibility come from {@link HubManagedFile#of(String)}:
 * a JFR and its archive are the only types the hub knows. Everything else (logs, heap dumps) is
 * listed and served as it lies; Microscope classifies those names on its side.
 */
public class FilesystemRepositoryStorage implements RepositoryStorage {

    private static final Logger LOG = LoggerFactory.getLogger(FilesystemRepositoryStorage.class);

    /**
     * How a session directory is listed: newest first by filename. Presentation only — which chunk
     * the profiler still holds open is derived by {@code RecordingSession}, never from this order.
     */
    private static final Comparator<Path> NEWEST_BY_NAME =
            Comparator.comparing((Path file) -> file.getFileName().toString()).reversed();

    private final ProjectInfo projectInfo;
    private final Path workspacesDir;
    private final ProjectRepositoryRepository projectRepositoryRepository;

    private volatile RepositoryInfo cachedRepositoryInfo;

    public FilesystemRepositoryStorage(
            ProjectInfo projectInfo,
            Path workspacesDir,
            ProjectRepositoryRepository projectRepositoryRepository) {

        this.projectInfo = projectInfo;
        this.workspacesDir = workspacesDir;
        this.projectRepositoryRepository = projectRepositoryRepository;
    }

    @Override
    public RepositoryInfo repositoryInfo() {
        RepositoryInfo result = cachedRepositoryInfo;
        if (result == null) {
            List<RepositoryInfo> repositoryInfos = projectRepositoryRepository.getAll();
            if (repositoryInfos.isEmpty()) {
                throw new IllegalStateException("No repository info found for project: " + projectInfo.id());
            }
            result = repositoryInfos.getFirst();
            cachedRepositoryInfo = result;
        }
        return result;
    }

    private Path projectPath(RepositoryInfo repositoryInfo) {
        return SessionPaths.project(workspacesDir, repositoryInfo);
    }

    private Path resolveSessionPath(RepositoryInfo repositoryInfo, ProjectInstanceSessionInfo sessionInfo) {
        return SessionPaths.session(workspacesDir, repositoryInfo, sessionInfo);
    }

    /**
     * The directory of a session this project holds, or empty (and logged) when the session is not
     * the project's or its directory is gone.
     */
    private Optional<Path> sessionDirectory(String sessionId) {
        Optional<ProjectInstanceSessionInfo> sessionInfo = projectRepositoryRepository.findSessionById(sessionId);
        if (sessionInfo.isEmpty()) {
            LOG.warn("Session not found: project_id={} session_id={}", projectInfo.id(), sessionId);
            return Optional.empty();
        }
        Path sessionPath = resolveSessionPath(repositoryInfo(), sessionInfo.get());
        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: session_id={} session_path={}", sessionId, sessionPath);
            return Optional.empty();
        }
        return Optional.of(sessionPath);
    }

    @Override
    public Optional<RecordingSession> singleSession(String sessionId, SessionDetail detail) {
        Optional<ProjectInstanceSessionInfo> sessionOpt = projectRepositoryRepository.findSessionById(sessionId);
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(createRecordingSession(detail, sessionOpt.get()));
    }

    @Override
    public List<RecordingSession> listSessions(SessionDetail detail) {
        // Newest first, which the query already orders by origin_created_at
        return projectRepositoryRepository.findAllSessions().stream()
                .map(session -> createRecordingSession(detail, session))
                .toList();
    }

    @Override
    public List<RecordingSession> listSessionsByInstanceId(String instanceId, SessionDetail detail) {
        return projectRepositoryRepository.findSessionsByInstanceId(instanceId).stream()
                .map(session -> createRecordingSession(detail, session))
                .toList();
    }

    @Override
    public RecordingSession withFiles(RecordingSession session) {
        Path sessionPath = projectPath(repositoryInfo()).resolve(session.name());
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

    private RecordingSession createRecordingSession(SessionDetail detail, ProjectInstanceSessionInfo sessionInfo) {
        RepositoryInfo repositoryInfo = repositoryInfo();
        Path sessionPath = resolveSessionPath(repositoryInfo, sessionInfo);
        RecordingStatus recordingStatus = statusOf(sessionInfo);

        List<RepositoryFile> repositoryFiles;
        if (detail.withFiles()) {
            repositoryFiles = _listRepositoryFiles(sessionPath);
        } else {
            repositoryFiles = List.of();
        }

        return new RecordingSession(
                sessionInfo.sessionId(),
                sessionInfo.relativeSessionPath().toString(),
                sessionInfo.instanceId(),
                sessionInfo.originCreatedAt(),
                sessionInfo.finishedAt(),
                recordingStatus,
                repositoryFiles,
                sessionInfo.retained(),
                sessionInfo.heartbeatMissing());
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
     * A session is recording until something stamps {@code finishedAt} (the heartbeat, its startup
     * grace, or the reconciler when the instance's next session appears). Never derive this from
     * the session's position in the listing: with two instances per project that marked a live
     * session FINISHED and had its open chunk compressed away.
     */
    private static RecordingStatus statusOf(ProjectInstanceSessionInfo sessionInfo) {
        return sessionInfo.finishedAt() == null ? RecordingStatus.ACTIVE : RecordingStatus.FINISHED;
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
    public void deleteSession(String sessionId) {
        Optional<Path> directory = sessionDirectory(sessionId);
        if (directory.isEmpty()) {
            return;
        }
        FileSystemUtils.removeDirectory(directory.get());
        LOG.info("Deleted session directory: session_id={} session_path={}", sessionId, directory.get());
    }

    @Override
    public void deleteInstanceDirectory(String instanceId) {
        Path instancePath = projectPath(repositoryInfo()).resolve(instanceId);

        if (!Files.isDirectory(instancePath)) {
            return;
        }

        FileSystemUtils.removeDirectory(instancePath);
        LOG.info("Deleted instance directory: instance_path={}", instancePath);
    }

    @Override
    public void deleteProjectDirectory() {
        // getAll() instead of repositoryInfo(): a project without a repository has no
        // directory to delete and must not fail the surrounding delete flow
        List<RepositoryInfo> repositoryInfos = projectRepositoryRepository.getAll();
        if (repositoryInfos.isEmpty()) {
            return;
        }

        Path projectPath = projectPath(repositoryInfos.getFirst());

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
