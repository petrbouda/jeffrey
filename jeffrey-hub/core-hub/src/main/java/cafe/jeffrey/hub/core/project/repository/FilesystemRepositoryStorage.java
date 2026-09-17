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
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.hub.model.RepositoryInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import cafe.jeffrey.hub.model.repository.RepositoryFile;

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

/**
 * A project's repository as it lies on the shared volume: instance directories, session
 * directories under them, and the files a profiler left behind.
 *
 * <p>Named for async-profiler once, and that stopped being true. Nothing here is specific to a
 * profiler: a file is classified by {@link HubManagedFile#of(String)} and everything this class
 * then does to it — the id it is known by, where its timestamp comes from, whether it may be
 * compressed, whether it may be handed over — is a property that type declares about itself.
 *
 * <p>It is the one class whose <em>behaviour</em> a file's type decides, and the type is the
 * hub's own two-way one: a JFR and its archive; everything else has no name here.
 * The hub does not read a log or a heap dump and so does not need to tell them apart; Microscope
 * classifies the name again on its side, with the enum that knows what it can read.
 *
 * <p>The one thing here that is about the layout rather than about a file is
 * {@link #NEWEST_BY_NAME}, the order a session directory is listed in.
 */
public class FilesystemRepositoryStorage implements RepositoryStorage {

    private static final Logger LOG = LoggerFactory.getLogger(FilesystemRepositoryStorage.class);

    // <project>/<instance-id>/<session-id> is two levels below the project root; one extra
    // level of slack absorbs layouts with a deeper relative session path.
    private static final int SESSION_SEARCH_MAX_DEPTH = 3;

    /**
     * How a session directory is listed: newest first by filename.
     *
     * <p>A constant rather than something injected. It was a {@code FileInfoProcessor} with two
     * implementations, one ordering by name and one by modification time for a layout whose names
     * say nothing about order. No such layout was ever wired, so the second went, and an interface
     * with one implementation and one construction site is a seam that only claims to be one.
     *
     * <p>Presentation only. Which chunk the profiler still holds open is not read off this order —
     * {@code RecordingSession} derives it from the session and the timestamps — and it was reading
     * it off this order that left the two disagreeing.
     */
    private static final Comparator<Path> NEWEST_BY_NAME =
            Comparator.comparing((Path file) -> file.getFileName().toString()).reversed();

    private final Lock compressionLock = new ReentrantLock();
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
     * file that no longer exists.
     */
    RepositoryFile describe(Path file, Path sessionPath) {
        String sourceName = sessionPath.relativize(file).toString();
        Optional<HubManagedFile> recording = HubManagedFile.of(sourceName);
        try {
            return new RepositoryFile(
                    fileId(file),
                    sourceName,
                    recording.map(HubManagedFile::timestampResolver)
                            .orElse(TimestampResolver.FILESYSTEM)
                            .resolve(file),
                    FileSystemUtils.size(file),
                    recording.isPresent(),
                    file);
        } catch (RuntimeException e) {
            LOG.debug("Leaving out a repository file that can no longer be described: file={} reason={}",
                    file, e.getMessage());
            return null;
        }
    }

    /**
     * A file's identity within its session, which its type decides — the extension comes off a
     * recording so the id survives compression, and stays on everything else.
     *
     * <p>Here because it is read in both directions: {@link #describe} hands an id out and
     * {@link #deleteRepositoryFiles} matches files against it, and an id that could not be
     * matched back to the file it came from is an id that deletes nothing.
     */
    private static String fileId(Path file) {
        return HubManagedFile.of(file)
                .map(type -> type.idOf(file))
                .orElseGet(() -> file.getFileName().toString());
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
                .map(FilesystemRepositoryStorage::fileId)
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


    // ========== Files ==========

    /**
     * Where one file of a session is, as it lies. A lookup and nothing else: it does not
     * compress, and the path it names is the file the listing named.
     *
     * <p>Refuses rather than returning nothing. This used to be two methods returning lists, both
     * called with a single id, and between them they filtered on several grounds — so a caller
     * that named an empty recording, or one that had vanished, or the chunk the profiler still
     * holds, was told the same thing in each case: not found. Each of those now says what it is.
     * What the hub does not judge is what kind of file it is handing over: the profiler's own
     * scratch file is served like any other, and it is Microscope, which knows the name, that
     * declines to ask for it.
     */
    @Override
    public Path file(String sessionId, String fileId) {
        RecordingSession session = resolveSession(sessionId);

        RepositoryFile file = session.files().stream()
                .filter(candidate -> candidate.id().equals(fileId))
                // A recording and the archive beside it share an id while the compression job
                // is between publishing one and removing the other. Both are whole; the archive is
                // the one that will still be there in a moment.
                .reduce(FilesystemRepositoryStorage::theOneThatStays)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Session " + sessionId + " holds no file with id " + fileId
                                + ". Take the id from the session's file listing."));

        if (session.isOpen(file)) {
            throw new IllegalArgumentException("File " + file.name() + " is the chunk the profiler is "
                    + "still writing, and reading it would give a truncated answer. It can be taken "
                    + "once the profiler has rolled the next one.");
        }
        if (!Files.isRegularFile(file.filePath())) {
            // Its own kind, because a caller holding the id can act on it: the compression job
            // publishes the archive and removes the recording between this listing and this
            // check, and the same id resolves to the archive on the next look.
            throw new FileVanishedException("File " + file.name() + " was listed for session "
                    + sessionId + " but is no longer on disk. It may have been replaced by its "
                    + "compressed form; ask for the same file id again.");
        }
        if (file.isRecordingFile() && !file.hasContent()) {
            throw new IllegalArgumentException("Recording " + file.name() + " is empty — the profiler "
                    + "stopped before it wrote an event into it.");
        }

        return file.filePath();
    }

    /**
     * Which of two files sharing an id to hand over — the archive rather than the recording.
     *
     * <p>A stripped extension is what makes an id survive compression, and it is also what lets
     * one id name two files for as long as the job takes to remove the first. Both are whole in
     * that window: the archive is published by a rename, so its name never names a file being
     * written into, and the recording is what it was made from. The archive is the one to serve
     * because it is the one that stays — the recording beside it is about to be deleted, and a
     * reader that resolved it may find it gone before it opens it.
     */
    private static RepositoryFile theOneThatStays(RepositoryFile first, RepositoryFile second) {
        return HubManagedFile.of(first.name()).filter(HubManagedFile::isArchive).isPresent() ? first : second;
    }

    // ========== Session Compression ==========

    @Override
    public int compressSession(String sessionId) {
        RecordingSession session = resolveSession(sessionId);

        // Everything the profiler has closed, which for a live session is every chunk but the
        // one it is still writing. There is nothing further to decide: a closed chunk's bytes
        // are final whether or not its session has finished.
        return (int) session.finishedRecordings().stream()
                .map(file -> compress(sessionId, file))
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
     * Compresses one closed recording in place, replacing it with its LZ4 archive.
     *
     * <p>Reached from {@link #compressSession} and nowhere else. Serving a recording used to
     * come through here too, compressing on the way out so the transfer carried less — and that
     * was wrong in a way the saving did not pay for: a download rewrote the repository while
     * reading it, and the reader was never told. The download carries the bytes and their length
     * and no name, so a client that had listed {@code …jfr} a moment earlier wrote LZ4 bytes
     * under that name, and a {@code .jfr} holding an LZ4 frame is not something any reader of it
     * can recover — compression is detected by the extension, nothing sniffs the frame.
     *
     * <p>The source is deleted only once the archive exists, and the archive exists only once it
     * is whole: {@link Compression#compress} writes elsewhere and renames onto the target, so
     * the target's name never names a file being written into. That is what makes the two
     * shortcuts below safe. Written straight to the target, as this did, a hub killed
     * mid-compression left a partial archive beside a whole recording, and the next run read
     * "the archive is there" and deleted the recording; two overlapping runs did the same to
     * each other, since the lock is held by one storage instance and an instance is built per
     * call.
     */
    private Path compress(String sessionId, RepositoryFile file) {
        // Every file here is a recording — finishedRecordings() saw to that — so the only way
        // its type has no compression is that it is the archive already.
        Compression compression = HubManagedFile.of(file.name())
                .map(HubManagedFile::compression)
                .orElse(Compression.NONE);
        if (!compression.isSupported()) {
            LOG.debug("Leaving an already compressed recording alone: sessionId={} file={}", sessionId, file.name());
            return file.filePath();
        }

        Path sourcePath = file.filePath();
        Path compressedPath = compression.target(sourcePath);

        // Already done, here or by a run this one overlaps: the archive under that name is whole
        // by construction, so the recording it was made from has served its purpose.
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

            // Skip empty recording files left when the profiler stops before writing events,
            // for example after a non-graceful shutdown.
            if (FileSystemUtils.size(sourcePath) == 0) {
                LOG.debug("Skipping empty recording file: sessionId={} file={}", sessionId, sourcePath);
                return null;
            }

            // No verification of the result here any more: compress() returns only once the
            // archive is whole and under its own name, and throws otherwise, so there is nothing
            // left for this to check that the check could still fail.
            compression.compress(sourcePath, compressedPath);
            FileSystemUtils.removeFile(sourcePath);
            return compressedPath;
        } finally {
            compressionLock.unlock();
        }
    }

    private List<RepositoryFile> _listRepositoryFiles(Path sessionPath) {

        if (!Files.isDirectory(sessionPath)) {
            LOG.warn("Session directory does not exist: {}", sessionPath);
            return List.of();
        }

        return FileSystemUtils.sortedFilesInDirectory(sessionPath, NEWEST_BY_NAME).stream()
                .filter(Files::isRegularFile)
                .filter(FileSystemUtils::isNotHidden)
                .map(file -> describe(file, sessionPath))
                .filter(Objects::nonNull)
                .toList();
    }
}
