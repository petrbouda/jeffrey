/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.hub.core.project.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.project.repository.file.FileInfoProcessor;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.FileSizeReader;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.RepositoryType;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile.JFR;
import static cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile.JFR_LZ4;

public class AsprofFileRepositoryStorage implements RepositoryStorage {

    private static final Logger LOG = LoggerFactory.getLogger(AsprofFileRepositoryStorage.class);

    // JFR_LZ4 must come first so removeExtension matches longer extension first (.jfr.lz4 before .jfr)
    private static final List<SupportedRecordingFile> RECORDING_FILE_TYPES = List.of(JFR_LZ4, JFR);

    private static final List<String> RECORDING_EXTENSIONS = RECORDING_FILE_TYPES.stream()
            .map(SupportedRecordingFile::fileExtension)
            .toList();

    private static final SupportedRecordingFile TARGET_COMPRESSED_TYPE = JFR_LZ4;

    // <project>/<instance-id>/<session-id> is two levels below the project root; one extra
    // level of slack absorbs layouts with a deeper relative session path.
    private static final int SESSION_SEARCH_MAX_DEPTH = 3;

    private final Lock compressionLock = new ReentrantLock();
    private final ProjectInfo projectInfo;
    private final Path workspacesDir;
    private final ProjectRepositoryRepository projectRepositoryRepository;
    private final FileInfoProcessor fileInfoProcessor;

    private volatile RepositoryInfo cachedRepositoryInfo;

    public AsprofFileRepositoryStorage(
            ProjectInfo projectInfo,
            Path workspacesDir,
            ProjectRepositoryRepository projectRepositoryRepository,
            FileInfoProcessor fileInfoProcessor) {

        this.projectInfo = projectInfo;
        this.workspacesDir = workspacesDir;
        this.projectRepositoryRepository = projectRepositoryRepository;
        this.fileInfoProcessor = fileInfoProcessor;
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

    private Path resolveWorkspacePath(RepositoryInfo repositoryInfo) {
        String workspacesPath = repositoryInfo.workspacesPath();
        Path resolvedWorkspacesPath = workspacesPath == null
                ? workspacesDir
                : workspacesDir.getFileSystem().getPath(workspacesPath);
        return resolvedWorkspacesPath
                .resolve(repositoryInfo.relativeWorkspacePath());
    }

    private Path resolveSessionPath(RepositoryInfo repositoryInfo, ProjectInstanceSessionInfo sessionInfo) {
        return resolveWorkspacePath(repositoryInfo)
                .resolve(repositoryInfo.relativeProjectPath())
                .resolve(sessionInfo.relativeSessionPath());
    }

    @Override
    public Optional<RecordingSession> singleSession(String sessionId, boolean withFiles) {
        Optional<ProjectInstanceSessionInfo> sessionOpt = projectRepositoryRepository.findSessionById(sessionId);
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }

        ProjectInstanceSessionInfo session = sessionOpt.get();
        Optional<String> latestSessionId = projectRepositoryRepository.findLatestSessionId();
        boolean isLatestSession = latestSessionId.map(id -> id.equals(sessionId)).orElse(false);

        return Optional.of(createRecordingSession(withFiles, session, isLatestSession));
    }

    @Override
    public List<RecordingSession> listSessions(boolean withFiles) {
        List<ProjectInstanceSessionInfo> sessions = projectRepositoryRepository.findAllSessions().stream()
                .sorted(Comparator.comparing(ProjectInstanceSessionInfo::originCreatedAt).reversed())
                .toList();

        // Creates RecordingSession objects for each session and marks the latest session as ACTIVE/UNKNOWN
        return IntStream.range(0, sessions.size())
                // First is latest after sorting
                .mapToObj(index -> createRecordingSession(withFiles, sessions.get(index), index == 0))
                .toList();
    }

    @Override
    public List<RecordingSession> listSessionsByInstanceId(String instanceId, boolean withFiles) {
        List<ProjectInstanceSessionInfo> sessions = projectRepositoryRepository.findSessionsByInstanceId(instanceId).stream()
                .sorted(Comparator.comparing(ProjectInstanceSessionInfo::originCreatedAt).reversed())
                .toList();

        // Only the repository-wide latest session may be ACTIVE/UNKNOWN; a per-instance "latest"
        // is not necessarily the same. Cross-reference with the repository's latest session id.
        Optional<String> latestSessionId = projectRepositoryRepository.findLatestSessionId();

        return sessions.stream()
                .map(session -> createRecordingSession(
                        withFiles,
                        session,
                        latestSessionId.map(id -> id.equals(session.sessionId())).orElse(false)))
                .toList();
    }

    private RecordingSession createRecordingSession(
            boolean withFiles, ProjectInstanceSessionInfo sessionInfo, boolean isLatestSession) {

        RepositoryInfo repositoryInfo = repositoryInfo();

        Path sessionPath = resolveSessionPath(repositoryInfo, sessionInfo);

        // Determine status based on business rule: only latest session can be ACTIVE/UNKNOWN
        RecordingStatus recordingStatus = determineSessionStatus(sessionInfo, isLatestSession);

        List<RepositoryFile> repositoryFiles;
        if (withFiles) {
            repositoryFiles = _listRepositoryFiles(recordingStatus, sessionPath);
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
                sessionPath,
                repositoryFiles,
                sessionInfo.retained());
    }

    /**
     * One file of a session as the repository reports it, or {@code null} when the file can no
     * longer be described because it is no longer there.
     *
     * <p>A session directory changes while it is being listed. async-profiler deletes its
     * {@code .jfr.N~} cache as soon as it flushes a chunk, and the compression job replaces a
     * recording with its archive, so a name this listing has just read can be gone by the time
     * its size or timestamp is asked for. Leaving such a file out is what the next listing will
     * say anyway; raising the failure instead would take the whole instance page down over one
     * file that no longer exists. The race is old, but it used to be hidden: a {@code stat()}
     * answered from the client's attribute cache still reports a file the share has already
     * removed, where asking the share for a handle does not.
     */
    RepositoryFile describe(Path file, RecordingStatus sessionStatus, Path sessionPath) {
        String sourceName = sessionPath.relativize(file).toString();
        SupportedRecordingFile fileType = SupportedRecordingFile.of(sourceName);
        try {
            return new RepositoryFile(
                    fileId(file),
                    sourceName,
                    fileInfoProcessor.createdAt(file),
                    sizeReader(sessionStatus, fileType).size(file),
                    fileType,
                    file);
        } catch (RuntimeException e) {
            LOG.debug("Leaving out a repository file that can no longer be described: file={} reason={}",
                    file, e.getMessage());
            return null;
        }
    }

    /**
     * A file's identity within its session: its own name with the recording extension stripped,
     * so it survives the hub compressing the file and an id taken from a listing still names the
     * same recording afterwards.
     *
     * <p>One definition, because it is read in both directions — {@link #describe} hands it out
     * and {@link #deleteRepositoryFiles} matches files against it — and an id that could not be
     * matched back to the file it came from is an id that deletes nothing.
     */
    private static String fileId(Path file) {
        return FileSystemUtils.removeExtension(file, RECORDING_EXTENSIONS);
    }

    /**
     * How one file's size is read, which on an SMB mount is the difference between a figure and
     * a round trip. Only a session that is still recording has files open on another client, and
     * the share answers a directory listing about such a file with the size it last saw rather
     * than the size the file has; opening it asks the share for the current one. Every other file
     * is measured from the listing at no cost: a finished session's writer has closed its files,
     * and a compressed recording was written and closed by this hub, so both are final however
     * old the listing is. That matters because a listing covers every session of a project, and
     * an open apiece would be hundreds of round trips on one page load.
     */
    static FileSizeReader sizeReader(RecordingStatus sessionStatus, SupportedRecordingFile fileType) {
        if (sessionStatus == RecordingStatus.FINISHED || fileType == SupportedRecordingFile.JFR_LZ4) {
            return FileSizeReader.FILE_ATTRIBUTES;
        }
        return FileSizeReader.LIVE_FILE;
    }

    private RecordingStatus determineSessionStatus(ProjectInstanceSessionInfo sessionInfo, boolean isLatestSession) {
        if (isLatestSession) {
            return sessionInfo.finishedAt() != null ? RecordingStatus.FINISHED : RecordingStatus.ACTIVE;
        } else {
            return RecordingStatus.FINISHED;
        }
    }

    @Override
    public List<Path> listSessionDirectoriesOnDisk() {
        RepositoryInfo repositoryInfo = repositoryInfo();
        Path projectPath = resolveWorkspacePath(repositoryInfo)
                .resolve(repositoryInfo.relativeProjectPath());

        if (!Files.isDirectory(projectPath)) {
            return List.of();
        }

        // Sessions sit at <project>/<instance-id>/<session-id>, but relative session paths are
        // opaque to this class, so the depth bound is a guard rather than an exact expectation.
        // The marker file is what actually identifies a session directory: it keeps the sweep
        // from ever proposing an instance directory, a streaming repo, or a stray folder.
        try (Stream<Path> stream = Files.walk(projectPath, SESSION_SEARCH_MAX_DEPTH)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve(JeffreyLayout.SESSION_INFO_FILE)))
                    .toList();
        } catch (IOException e) {
            LOG.warn("Cannot walk project directory for session directories: project_path={}", projectPath, e);
            return List.of();
        }
    }

    /**
     * Deletes the named files of a session, looked up in the session's own listing.
     *
     * <p>Through the listing rather than by building a path out of the id, which is what this
     * did and why it deleted nothing. A file's id is its name with the recording extension
     * stripped — that is the point of it, so an id survives the hub compressing the file — so
     * {@code sessionPath.resolve(id)} named a file that does not exist, and
     * {@code deleteIfExists} reported success for it. Both retention jobs and the UI's delete ran
     * as designed and freed nothing.
     *
     * <p>It also closes what that path built out of the id let through. The ids arrive over gRPC,
     * and {@code resolve} on one holding {@code ../} walks out of the session directory: any file
     * the hub could delete, it would delete. A listing has no entry for such an id, so there is
     * now nothing to resolve.
     */
    @Override
    public void deleteRepositoryFiles(String sessionId, List<String> sessionFileIds) {
        RepositoryInfo repositoryInfo = repositoryInfo();

        Optional<ProjectInstanceSessionInfo> workspaceSessionOpt =
                projectRepositoryRepository.findSessionById(sessionId);

        if (workspaceSessionOpt.isEmpty()) {
            LOG.warn("Session not found for project {}: {}", projectInfo.id(), sessionId);
            return;
        }
        ProjectInstanceSessionInfo sessionInfo = workspaceSessionOpt.get();

        Path sessionPath = resolveSessionPath(repositoryInfo, sessionInfo);
        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: {}", sessionPath);
            return;
        }

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

        // Over the ids rather than over the counts: one id can match two files while a compression
        // is in flight — the recording and the archive beside it strip to the same id, and both are
        // that chunk — so a count says nothing about whether every id was found.
        Set<String> found = matched.stream()
                .map(AsprofFileRepositoryStorage::fileId)
                .collect(Collectors.toSet());
        List<String> missing = requestedIds.stream().filter(id -> !found.contains(id)).toList();

        // Named but not held: said so rather than passed over, because a call that deleted nothing
        // and a call that deleted everything it asked for read exactly the same otherwise.
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
        RepositoryInfo repositoryInfo = repositoryInfo();

        Optional<ProjectInstanceSessionInfo> workspaceSessionOpt =
                projectRepositoryRepository.findSessionById(sessionId);

        if (workspaceSessionOpt.isEmpty()) {
            LOG.warn("Session not found for project {}: {}", projectInfo.id(), sessionId);
            return;
        }
        ProjectInstanceSessionInfo sessionInfo = workspaceSessionOpt.get();

        Path sessionPath = resolveSessionPath(repositoryInfo, sessionInfo);
        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: {}", sessionPath);
            return;
        }

        FileSystemUtils.removeDirectory(sessionPath);
        LOG.info("Deleted session directory: {}", sessionPath);
    }

    @Override
    public void deleteInstanceDirectory(String instanceId) {
        RepositoryInfo repositoryInfo = repositoryInfo();
        Path instancePath = resolveWorkspacePath(repositoryInfo)
                .resolve(repositoryInfo.relativeProjectPath())
                .resolve(instanceId);

        if (!Files.isDirectory(instancePath)) {
            return;
        }

        FileSystemUtils.removeDirectory(instancePath);
        LOG.info("Deleted instance directory: {}", instancePath);
    }

    @Override
    public void deleteProjectDirectory() {
        // getAll() instead of repositoryInfo(): a project without a repository has no
        // directory to delete and must not fail the surrounding delete flow
        List<RepositoryInfo> repositoryInfos = projectRepositoryRepository.getAll();
        if (repositoryInfos.isEmpty()) {
            return;
        }

        RepositoryInfo repositoryInfo = repositoryInfos.getFirst();
        Path projectPath = resolveWorkspacePath(repositoryInfo)
                .resolve(repositoryInfo.relativeProjectPath());

        if (!Files.isDirectory(projectPath)) {
            return;
        }

        FileSystemUtils.removeDirectory(projectPath);
        LOG.info("Deleted project directory: {}", projectPath);
    }

    @Override
    public RepositoryType type() {
        return RepositoryType.ASYNC_PROFILER;
    }

    // ========== Recording Files ==========

    @Override
    public List<Path> recordings(String sessionId, List<String> recordingIds) {
        RecordingSession session = resolveSession(sessionId);

        // No ids named means every finished recording file, which is what the gRPC contract
        // promises. It used to mean the opposite for an empty list: the test was for null, and a
        // caller over the wire cannot send null — protobuf hands back an empty list — so asking
        // for "all of it" that way selected nothing and failed as an empty session.
        boolean allOfThem = recordingIds == null || recordingIds.isEmpty();

        // finishedRecordings() is already the closed chunks, oldest first: the one the profiler
        // still holds open is not a recording anyone may be handed.
        return session.finishedRecordings().stream()
                .filter(file -> Files.isRegularFile(file.filePath()))
                .filter(file -> allOfThem || recordingIds.contains(file.id()))
                .map(file -> ensureCompressed(sessionId, file))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    // ========== Artifact Files ==========

    @Override
    public List<Path> artifacts(String sessionId, List<String> artifactIds) {
        RecordingSession session = resolveSession(sessionId);

        return session.files().stream()
                .filter(file -> Files.isRegularFile(file.filePath()))
                .filter(RepositoryFile::isArtifactFile)
                .filter(file -> artifactIds == null || artifactIds.contains(file.id()))
                .map(RepositoryFile::filePath)
                .toList();
    }

    // ========== Session Compression ==========

    @Override
    public int compressSession(String sessionId) {
        RecordingSession session = resolveSession(sessionId);

        // Everything the profiler has closed, which for a live session is every chunk but the
        // one it is still writing. There is nothing further to decide: a closed chunk's bytes
        // are final whether or not its session has finished.
        return (int) session.finishedRecordings().stream()
                .map(file -> ensureCompressed(sessionId, file))
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    // ========== Private Helpers ==========

    private RecordingSession resolveSession(String sessionId) {
        Optional<RecordingSession> sessionOpt = singleSession(sessionId, true);
        if (sessionOpt.isEmpty()) {
            throw Exceptions.recordingSessionNotFound(sessionId);
        }
        return sessionOpt.get();
    }

    /**
     * Ensures the recording file is compressed (JFR_LZ4 format).
     * <p>
     * If already compressed, returns the original path. Otherwise, compresses the file
     * and stores the compressed version persistently in the same directory.
     * Uses double-check locking pattern for thread safety.
     * </p>
     */
    private Path ensureCompressed(String sessionId, RepositoryFile file) {
        if (file.fileType() == TARGET_COMPRESSED_TYPE) {
            return file.filePath();
        }

        Path sourcePath = file.filePath();
        Path compressedPath = sourcePath.resolveSibling(file.name() + ".lz4");

        // Fast path: check if already compressed by another thread
        if (Files.exists(compressedPath)) {
            FileSystemUtils.removeFile(sourcePath);
            return compressedPath;
        }

        compressionLock.lock();
        try {
            // Double-check after acquiring lock
            if (Files.exists(compressedPath)) {
                FileSystemUtils.removeFile(sourcePath);
                return compressedPath;
            }

            // Capture original file size before compression. Through an open handle rather than a
            // stat: this runs on a recording the profiler may still hold open, whose listed size
            // on an SMB mount can be anything from zero to the last flush, and the guard below
            // would drop a real recording on the strength of it. The open costs nothing here —
            // compressing the file opens it a moment later regardless.
            long originalSize = FileSizeReader.OPEN_HANDLE.size(sourcePath);

            // Skip empty recording files left when the profiler stops before writing events,
            // for example after a non-graceful shutdown.
            if (originalSize == 0) {
                LOG.debug("Skipping empty recording file: sessionId={} file={}", sessionId, sourcePath);
                return null;
            }

            // Compress, verify, and delete original
            Lz4Compressor.compress(sourcePath, compressedPath);
            long compressedSize = Files.size(compressedPath);
            if (Files.exists(compressedPath) && compressedSize > 0) {
                FileSystemUtils.removeFile(sourcePath);
            }
            return compressedPath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to verify compressed file: " + compressedPath, e);
        } finally {
            compressionLock.unlock();
        }
    }

    private List<RepositoryFile> _listRepositoryFiles(
            RecordingStatus recordingStatus,
            Path sessionPath) {

        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: {}", sessionPath);
            return List.of();
        }

        // Sorted by filename, for presentation. Which chunk the profiler still holds open is not
        // read off this order — RecordingSession derives it from the session and the timestamps —
        // and it was reading it off this order that left the two disagreeing.
        return FileSystemUtils.sortedFilesInDirectory(
                        sessionPath, fileInfoProcessor.comparator()).stream()
                .filter(Files::isRegularFile)
                .filter(FileSystemUtils::isNotHidden)
                .map(file -> describe(file, recordingStatus, sessionPath))
                .filter(Objects::nonNull)
                .toList();
    }
}
