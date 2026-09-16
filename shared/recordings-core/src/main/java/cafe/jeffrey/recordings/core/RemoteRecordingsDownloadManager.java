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

package cafe.jeffrey.recordings.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.client.manager.TempDirProvider;
import cafe.jeffrey.hub.client.FileStreamClient;
import cafe.jeffrey.hub.client.RepositoryClient;
import cafe.jeffrey.recordings.core.download.FileProgress;
import cafe.jeffrey.recordings.core.download.ProgressCallback;
import cafe.jeffrey.recordings.core.download.ProgressTrackingInputStream;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.hub.client.dto.RecordingSessionResponse;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.common.model.repository.ChunkWindow;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;

public class RemoteRecordingsDownloadManager implements RecordingsDownloadManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteRecordingsDownloadManager.class);

    /**
     * Maximum number of concurrent file downloads.
     */
    private static final int MAX_CONCURRENT_DOWNLOADS = 5;

    /**
     * Suffix for partially downloaded files before they are atomically moved to their final name.
     */
    private static final String PARTIAL_FILE_SUFFIX = ".part";

    /**
     * How much of a file is moved from the wire to disk at a time.
     */
    private static final int COPY_BUFFER_BYTES = 8192;

    /**
     * Path elements that name a directory rather than a file in it, and so are not a name a
     * session's file may be written under.
     */
    private static final Set<String> NOT_A_FILE_NAME = Set.of(".", "..");

    private final TempDirProvider tempDirProvider;
    private final FileStreamClient fileStreamClient;
    private final RepositoryClient repositoryClient;
    private final RecordingsCoreManager recordingsManager;
    private final OriginContext originContext;
    private final String projectName;

    public RemoteRecordingsDownloadManager(
            TempDirProvider tempDirProvider,
            FileStreamClient fileStreamClient,
            RepositoryClient repositoryClient,
            RecordingsCoreManager recordingsManager,
            OriginContext originContext,
            String projectName) {

        this.tempDirProvider = tempDirProvider;
        this.fileStreamClient = fileStreamClient;
        this.repositoryClient = repositoryClient;
        this.recordingsManager = recordingsManager;
        this.originContext = originContext;
        this.projectName = projectName;
    }

    @Override
    public String downloadSession(String recordingSessionId) {
        RecordingSession recordingSession = session(recordingSessionId);

        return download(recordingSession, recordingSession.finishedFiles(), ProgressCallback.noop());
    }

    @Override
    public String downloadRecordings(String recordingSessionId, List<String> fileIds) {
        RecordingSession recordingSession = session(recordingSessionId);

        return download(recordingSession, chosenFiles(recordingSession, fileIds), ProgressCallback.noop());
    }

    /**
     * The session's finished files the caller named, refusing an id the session does not hold.
     *
     * <p>Filtering alone would drop such an id without a word and return a recording made of the
     * rest — a download that looks like the one that was asked for and is not. The hub used to
     * catch this, because the merged RPC was the one call that saw a session and a whole selection
     * together; it serves one file per call now, so the refusal belongs here with the other one.
     */
    private static List<RepositoryFile> chosenFiles(RecordingSession session, List<String> fileIds) {
        List<RepositoryFile> finished = session.finishedFiles();

        Set<String> known = finished.stream().map(RepositoryFile::id).collect(Collectors.toSet());
        List<String> unknown = fileIds.stream().filter(id -> !known.contains(id)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Session " + session.id() + " has no finished file " + unknown
                    + ". Downloading the rest would have returned a recording made of whatever was left, "
                    + "with nothing saying a file was missing. Take the ids from the session's file listing; "
                    + "a file still being written is not one yet.");
        }

        Set<String> requestedFileIds = Set.copyOf(fileIds);
        return finished.stream()
                .filter(file -> requestedFileIds.contains(file.id()))
                .toList();
    }

    @Override
    public String downloadWindow(String recordingSessionId, ChunkWindow window) {
        RecordingSession recordingSession = session(recordingSessionId);
        ChunkWindow.Selection selection = window.select(recordingSession);
        if (selection.isEmpty()) {
            throw new IllegalArgumentException(
                    "No finished recording file of session " + recordingSessionId + " covers the window");
        }
        return download(recordingSession, selection.files(), ProgressCallback.noop());
    }

    /**
     * The session as the hub has it now, with its files. Everything below asks the session which
     * of its files are closed rather than asking a file: a file carries no status, and the one
     * the profiler still holds open is the session's newest chunk.
     */
    private RecordingSession session(String recordingSessionId) {
        return RecordingSessionResponse.from(repositoryClient.recordingSession(recordingSessionId));
    }

    /**
     * A recording that holds every finished recording file of its session is the session; one that
     * holds fewer — a window, or files picked from the listing — is a part of it, and is named and
     * tagged with the span its files cover so it is never mistaken for the whole.
     *
     * <p>Refuses a part with a hole in it. A recording carries one start and one end, taken across
     * its files, so a skipped chunk leaves no trace: the recording would claim a span half of which
     * it does not hold, and every rate read off it would be wrong by the size of the hole. That is
     * true whether the chunks are joined or kept apart — the span is the same either way — so the
     * rule outlived the merge that first motivated it. It lives here now: the hub serves one file
     * per call and so never sees the selection whole.
     *
     * @return the covered span, or {@code null} for the whole session
     */
    private static ChunkWindow.Selection partOf(RecordingSession session, List<RepositoryFile> files) {
        Set<String> chosen = files.stream().map(RepositoryFile::id).collect(Collectors.toSet());
        ChunkWindow.Selection selection = ChunkWindow.ofFiles(session, chosen);
        if (!selection.contiguous()) {
            throw new IllegalArgumentException(
                    "The recording files chosen from session " + session.id() + " are not next to each other: "
                            + selection.describeGap(session) + " lies between them. The recording would claim "
                            + "the whole span from the first file to the last while holding only part of it, so "
                            + "they have to be an unbroken run. Choose the files in between too, or ask for a "
                            + "time window instead.");
        }
        // An empty selection is the whole session as far as naming goes: there is no span to name.
        return selection.isEmpty() || selection.isWholeSession(session) ? null : selection;
    }

    /**
     * The same download, reporting as it goes. The progress channel is the only difference — both
     * this and the silent entry points run the one transfer below.
     *
     * @param recordingSessionId the recording session ID
     * @param fileIds            the list of file IDs to download
     * @param progressCallback   callback for receiving progress updates
     * @return id of the recording created in the local store
     */
    public String downloadRecordingsWithProgress(
            String recordingSessionId,
            List<String> fileIds,
            ProgressCallback progressCallback) {

        RecordingSession recordingSession = session(recordingSessionId);

        return download(recordingSession, chosenFiles(recordingSession, fileIds), progressCallback);
    }

    /**
     * Downloads a session's chosen files and makes one local recording of them.
     *
     * <p>Every file is fetched on its own and kept on its own: the recording files are not joined,
     * here or anywhere later. They are simply the several files the recording is made of, the way a
     * session holds them, and the parser reads them as independent inputs.
     *
     * <p>One method for both the watched and the unwatched download. They were two, and the two
     * counted their files differently — the progress bar jumped as soon as the first frame arrived,
     * because one of them thought a session was one file and the other thought it was many.
     */
    private String download(
            RecordingSession recordingSession,
            List<RepositoryFile> files,
            ProgressCallback progressCallback) {

        String recordingSessionId = recordingSession.id();

        // At least one recording file must be present. 0...n artifacts (heap dumps, logs) may come
        // along with it.
        if (files.stream().noneMatch(RepositoryFile::isRecordingFile)) {
            throw Exceptions.emptyRecordingSession(recordingSessionId);
        }

        // Before anything is transferred, and before the progress bar starts: a pick with a hole in
        // it is refused here rather than after the bytes have been paid for.
        ChunkWindow.Selection part = partOf(recordingSession, files);

        List<RepositoryFile> recordingFiles = files.stream()
                .filter(RepositoryFile::isRecordingFile)
                .filter(file -> !recordingSession.isOpen(file))
                .toList();
        List<RepositoryFile> artifactFiles = files.stream()
                .filter(RepositoryFile::isArtifactFile)
                .toList();

        long totalBytes = files.stream().mapToLong(RepositoryFile::size).sum();
        int totalFiles = recordingFiles.size() + artifactFiles.size();

        LOG.info("Starting parallel download: sessionId={} recordings={} artifacts={} totalBytes={} maxConcurrent={}",
                recordingSessionId, recordingFiles.size(), artifactFiles.size(), totalBytes, MAX_CONCURRENT_DOWNLOADS);

        progressCallback.onStart(totalFiles, totalBytes);

        // Every file is announced up front so the UI can show the whole set before any of it arrives.
        progressCallback.onFilesDiscovered(Stream.concat(recordingFiles.stream(), artifactFiles.stream())
                .map(file -> FileProgress.pending(file.name(), file.size()))
                .toList());

        throwIfCancelled(progressCallback);

        Semaphore downloadSemaphore = new Semaphore(MAX_CONCURRENT_DOWNLOADS);

        try (TempDirectory tempDir = tempDirProvider.newTempDir()) {
            // One transfer for both, now that the hub serves every kind of file through one call.
            // What still separates them is what a failure means, which is the two blocks below: a
            // missing recording fails the download, a missing artifact is noted and skipped.
            List<CompletableFuture<Path>> recordingDownloads = recordingFiles.stream()
                    .map(file -> fetch(file, recordingSessionId, tempDir, downloadSemaphore, progressCallback))
                    .toList();

            List<CompletableFuture<Path>> artifactDownloads = artifactFiles.stream()
                    .map(file -> fetch(file, recordingSessionId, tempDir, downloadSemaphore, progressCallback)
                            .exceptionally(throwable -> artifactMissing(file, recordingSessionId, throwable, progressCallback)))
                    .toList();

            // Everything is waited for before anything is read, including when a recording file has
            // already failed: the temp directory is deleted on the way out of this block, and a
            // transfer still writing into it would be writing into a directory that is being
            // removed -- reported afterwards as a lost artifact, for a download that had already
            // failed for another reason.
            List<Path> recordingPaths;
            try {
                recordingPaths = joinAll(recordingDownloads);
            } catch (RuntimeException e) {
                awaitQuietly(artifactDownloads);
                throw e;
            }

            // A recording file that did not arrive fails the whole download. Dropping it the way a
            // missing artifact is dropped would leave a profile with a hole in it that reads as a
            // quiet stretch -- the same defect the contiguity rule above exists to prevent, arrived
            // at by a different route.
            List<Path> artifactPaths = joinAll(artifactDownloads).stream()
                    .filter(Objects::nonNull)
                    .toList();

            throwIfCancelled(progressCallback);
            progressCallback.onProcessing();

            String recordingId = persistToRecordings(
                    recordingSession, recordingPaths, artifactPaths, part);

            progressCallback.onComplete();
            LOG.info("Parallel download completed: sessionId={} recordingId={} recordings={} artifacts={}",
                    recordingSessionId, recordingId, recordingPaths.size(), artifactPaths.size());

            return recordingId;

        } catch (Exception e) {
            // Unwrapped first: the transfers run as futures, so anything they throw -- a
            // cancellation included -- arrives wrapped in a CompletionException. Tested for as it
            // comes back, a cancelled download reads as a failure, and is reported as one.
            Throwable cause = unwrap(e);
            if (cause instanceof CancellationException cancellation) {
                LOG.info("Download cancelled: sessionId={}", recordingSessionId);
                throw cancellation;
            }

            LOG.error("Download failed: sessionId={} error={}", recordingSessionId, cause.getMessage(), cause);
            progressCallback.onError(cause.getMessage());

            // Whoever asked for this has usually navigated away by now: the progress channel is the
            // only thing that carries the error, and it dies with the page that was watching it.
            Notifications.of(NotificationType.DOWNLOAD_FAILED)
                    .attribute("sessionId", recordingSessionId)
                    .errorType(cause)
                    .emit();

            throw e;
        }
    }

    /**
     * What actually went wrong, out of the {@link CompletionException} a future wraps it in.
     *
     * <p>Everything here is transferred on a future, so the wrapper is on every failure this class
     * sees. Left on, it hides the one distinction that matters -- a cancellation the reader asked
     * for, against a download that failed -- behind a type nothing tests for.
     */
    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    /**
     * Streams one file of the session into the temp directory, under its own name, waiting for a
     * slot first so that no more than {@link #MAX_CONCURRENT_DOWNLOADS} are in flight at once.
     *
     * @param transfer what pulls this kind of file -- a recording chunk or an artifact
     */
    private CompletableFuture<Path> fetch(
            RepositoryFile file,
            String recordingSessionId,
            TempDirectory tempDir,
            Semaphore downloadSemaphore,
            ProgressCallback progressCallback) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                downloadSemaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                progressCallback.onFileError(file.name(), "Download interrupted");
                throw new CancellationException("Download interrupted while waiting for a slot");
            }

            try {
                throwIfCancelled(progressCallback);

                // The name comes off the transfer rather than out of the listing: the job may
                // have replaced this file with its archive since the session was listed, and the
                // bytes arriving are then the archive's. The progress channel keeps saying the
                // name the reader chose, which is the one it was shown.
                AtomicReference<Path> landed = new AtomicReference<>();
                fileStreamClient.streamFile(recordingSessionId, file.id(), (inputStream, transferred) -> {
                    long actualSize = transferred.size() > 0 ? transferred.size() : file.size();
                    progressCallback.onFileStart(file.name(), actualSize);
                    Path target = targetIn(tempDir, transferred.name());
                    streamToFileWithProgress(inputStream, target, file.name(), progressCallback);
                    landed.set(target);
                });

                progressCallback.onFileComplete(file.name());
                return landed.get();
            } finally {
                downloadSemaphore.release();
            }
        }, Schedulers.sharedVirtual());
    }

    /**
     * Where a file of the session is written, from a name that came off the wire.
     *
     * <p>The hub names its own files and nothing here has reason to doubt them, but the name is
     * remote input and this is a path: reduced to a single element it cannot climb out of the
     * directory it is resolved against, whatever the other end sends.
     */
    private static Path targetIn(TempDirectory tempDir, String name) {
        Path single = Path.of(name).getFileName();
        if (single == null || single.toString().isBlank() || NOT_A_FILE_NAME.contains(single.toString())) {
            throw new IllegalArgumentException("The session names a file that cannot be written: " + name);
        }
        return tempDir.resolve(single.toString());
    }

    /**
     * What a lost artifact costs: the download still succeeds without it. Said out loud, because
     * the recording that results looks complete and is missing its heap dump or its log.
     */
    private static Path artifactMissing(
            RepositoryFile file, String recordingSessionId, Throwable throwable, ProgressCallback progressCallback) {

        // Unwrapped for the same reason as in download(): a cancelled artifact is not a missing
        // one, and reported as one it would raise a notification per transfer in flight for a
        // download the reader stopped on purpose.
        Throwable cause = unwrap(throwable);
        if (cause instanceof CancellationException cancellation) {
            throw cancellation;
        }

        LOG.warn("Failed to download artifact: file={} error={}", file.name(), cause.getMessage());
        progressCallback.onFileError(file.name(), cause.getMessage());

        Notifications.of(NotificationType.DOWNLOAD_ARTIFACT_MISSING)
                .attribute("file", file.name())
                .attribute("sessionId", recordingSessionId)
                .errorType(cause)
                .emit();

        return null;
    }

    private static List<Path> joinAll(List<CompletableFuture<Path>> downloads) {
        CompletableFuture.allOf(downloads.toArray(CompletableFuture[]::new)).join();
        return downloads.stream().map(CompletableFuture::join).toList();
    }

    /**
     * Waits for transfers whose result is no longer wanted, so that none of them is still writing
     * when the directory they write into is removed. Their failures are the reason this is being
     * unwound, or are beside it; either way the one being thrown is what gets reported.
     */
    private static void awaitQuietly(List<CompletableFuture<Path>> downloads) {
        try {
            CompletableFuture.allOf(downloads.toArray(CompletableFuture[]::new)).join();
        } catch (RuntimeException suppressed) {
            LOG.debug("A transfer also failed while unwinding a failed download", suppressed);
        }
    }

    private static void throwIfCancelled(ProgressCallback progressCallback) {
        if (progressCallback.isCancelled()) {
            throw new CancellationException("Download cancelled");
        }
    }



    private static void streamToFileWithProgress(
            InputStream source,
            Path target,
            String fileName,
            ProgressCallback progressCallback) throws IOException {

        // Written beside the real name and moved onto it once the last byte has arrived. A transfer
        // that stops partway -- cancelled, or the hub going away -- otherwise leaves a truncated
        // file under the name a complete one would have, and a short chunk is indistinguishable
        // from a chunk that recorded less: exactly the hole the whole-run rules exist to refuse.
        Path partial = target.resolveSibling(target.getFileName().toString() + PARTIAL_FILE_SUFFIX);

        try (InputStream in = new ProgressTrackingInputStream(
                source, fileName, progressCallback::onFileProgress);
             OutputStream out = Files.newOutputStream(partial, StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                if (progressCallback.isCancelled()) {
                    throw new CancellationException("Download cancelled during file copy");
                }
                out.write(buffer, 0, bytesRead);
            }
        } catch (RuntimeException | IOException e) {
            FileSystemUtils.removeFile(partial);
            throw e;
        }

        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static final DateTimeFormatter RECORDING_NAME_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

    private static final String WINDOW_NAME_SEPARATOR = "_";
    private static final String OPEN_WINDOW_END = "open";
    private static final String WINDOW_TAG_SEPARATOR = "-";

    /**
     * What the local recording is called in the Recordings list:
     * {@code <project>_<session start>} for a whole session, with the covered span appended for a
     * part of one, so two windows of the same session read apart and neither reads as the session.
     *
     * <p>A name, not a file name. The recording is the several files it arrived as, each keeping
     * the name the session gave it; there is no single file left for this to be the name of.
     *
     * @param window the covered span, or {@code null} for the whole session
     */
    private String recordingName(RecordingSession session, ChunkWindow.Selection window) {
        String name = sanitizeForFilename(projectName) + WINDOW_NAME_SEPARATOR
                + RECORDING_NAME_TIMESTAMP.format(session.createdAt());
        if (window == null) {
            return name;
        }
        String end = window.coverageEnd() == null
                ? OPEN_WINDOW_END
                : RECORDING_NAME_TIMESTAMP.format(window.coverageEnd());
        return name + WINDOW_NAME_SEPARATOR + RECORDING_NAME_TIMESTAMP.format(window.coverageStart())
                + WINDOW_NAME_SEPARATOR + end;
    }

    private static String sanitizeForFilename(String value) {
        if (value == null || value.isBlank()) {
            return "recording";
        }
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
        return sanitized.isEmpty() ? "recording" : sanitized;
    }

    /**
     * Persists the session's recording files and artifacts as one local recording, tagged with the
     * {@code origin.*} system tags from {@link #originContext}.
     *
     * @param window the span the recording covers when it is a part of the session, or {@code null}
     *               for the whole session; a part is named after that span and tagged with it
     * @return id of the newly created local recording
     */
    private String persistToRecordings(
            RecordingSession session, List<Path> recordingPaths, List<Path> artifactPaths,
            ChunkWindow.Selection window) {

        String recordingSessionId = session.id();
        Map<String, String> originTags = originContext.toTagMap(recordingSessionId);
        if (window != null) {
            originTags.put(OriginContext.TAG_WINDOW, windowTag(window));
        }
        return recordingsManager.createDownloadedRecording(
                recordingName(session, window), recordingPaths, artifactPaths, originTags);
    }

    private static String windowTag(ChunkWindow.Selection window) {
        String end = window.coverageEnd() == null ? OPEN_WINDOW_END : Long.toString(window.coverageEnd().toEpochMilli());
        return window.coverageStart().toEpochMilli() + WINDOW_TAG_SEPARATOR + end;
    }
}
