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
import org.springframework.core.io.Resource;
import cafe.jeffrey.hub.client.manager.TempDirProvider;
import cafe.jeffrey.hub.client.RecordingStreamClient;
import cafe.jeffrey.hub.client.RepositoryClient;
import cafe.jeffrey.recordings.core.download.FileProgress;
import cafe.jeffrey.recordings.core.download.ProgressCallback;
import cafe.jeffrey.recordings.core.download.ProgressTrackingInputStream;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.hub.client.dto.RecordingSessionResponse;
import cafe.jeffrey.hub.client.dto.RepositoryFileResponse;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.common.model.repository.ChunkWindow;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.notification.NotificationCategory;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;
import cafe.jeffrey.jfr.events.notification.Severity;

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

    private final TempDirProvider tempDirProvider;
    private final RecordingStreamClient recordingStreamClient;
    private final RepositoryClient repositoryClient;
    private final RecordingsCoreManager recordingsManager;
    private final OriginContext originContext;
    private final String projectName;

    public RemoteRecordingsDownloadManager(
            TempDirProvider tempDirProvider,
            RecordingStreamClient recordingStreamClient,
            RepositoryClient repositoryClient,
            RecordingsCoreManager recordingsManager,
            OriginContext originContext,
            String projectName) {

        this.tempDirProvider = tempDirProvider;
        this.recordingStreamClient = recordingStreamClient;
        this.repositoryClient = repositoryClient;
        this.recordingsManager = recordingsManager;
        this.originContext = originContext;
        this.projectName = projectName;
    }

    @Override
    public String downloadSession(String recordingSessionId) {
        RecordingSessionResponse recordingSession = repositoryClient.recordingSession(
                recordingSessionId);

        List<RepositoryFile> files = recordingSession.files().stream()
                .map(RepositoryFileResponse::from)
                .filter(RepositoryFile::isFinished)
                .toList();

        return download(recordingSession, files, ProgressCallback.noop());
    }

    @Override
    public String downloadRecordings(String recordingSessionId, List<String> fileIds) {
        RecordingSessionResponse recordingSession = repositoryClient.recordingSession(
                recordingSessionId);

        Set<String> requestedFileIds = Set.copyOf(fileIds);
        List<RepositoryFile> files = recordingSession.files().stream()
                .map(RepositoryFileResponse::from)
                .filter(RepositoryFile::isFinished)
                .filter(file -> requestedFileIds.contains(file.id()))
                .toList();

        return download(recordingSession, files, ProgressCallback.noop());
    }

    @Override
    public String downloadWindow(String recordingSessionId, ChunkWindow window) {
        RecordingSessionResponse recordingSession = repositoryClient.recordingSession(recordingSessionId);
        ChunkWindow.Selection selection = window.select(allFiles(recordingSession), finishedAt(recordingSession));
        if (selection.isEmpty()) {
            throw new IllegalArgumentException(
                    "No finished recording file of session " + recordingSessionId + " covers the window");
        }
        return download(recordingSession, selection.files(), ProgressCallback.noop());
    }

    private static List<RepositoryFile> allFiles(RecordingSessionResponse session) {
        return session.files().stream().map(RepositoryFileResponse::from).toList();
    }

    private static Instant finishedAt(RecordingSessionResponse session) {
        return session.finishedAt() == null ? null : Instant.ofEpochMilli(session.finishedAt());
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
    private static ChunkWindow.Selection partOf(RecordingSessionResponse session, List<RepositoryFile> files) {
        List<RepositoryFile> all = allFiles(session);
        Set<String> chosen = files.stream().map(RepositoryFile::id).collect(Collectors.toSet());
        ChunkWindow.Selection selection = ChunkWindow.ofFiles(all, chosen, finishedAt(session));
        if (!selection.contiguous()) {
            throw new IllegalArgumentException(
                    "The recording files chosen from session " + session.id() + " are not next to each other: "
                            + selection.describeGap(all) + " lies between them. The recording would claim "
                            + "the whole span from the first file to the last while holding only part of it, so "
                            + "they have to be an unbroken run. Choose the files in between too, or ask for a "
                            + "time window instead.");
        }
        // An empty selection is the whole session as far as naming goes: there is no span to name.
        return selection.isEmpty() || selection.isWholeSession(all) ? null : selection;
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

        RecordingSessionResponse recordingSession = repositoryClient.recordingSession(recordingSessionId);

        Set<String> requestedFileIds = Set.copyOf(fileIds);
        List<RepositoryFile> files = recordingSession.files().stream()
                .map(RepositoryFileResponse::from)
                .filter(RepositoryFile::isFinished)
                .filter(file -> requestedFileIds.contains(file.id()))
                .toList();

        return download(recordingSession, files, progressCallback);
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
            RecordingSessionResponse recordingSession,
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
                .filter(RepositoryFile::isFinished)
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
            // Recordings and artifacts go down the same bounded-parallel path and differ only in
            // what a failure means, which is the next two blocks.
            List<CompletableFuture<Path>> recordingDownloads = recordingFiles.stream()
                    .map(file -> fetch(file, recordingSessionId, tempDir, downloadSemaphore, progressCallback,
                            recordingStreamClient::streamRecordingFile))
                    .toList();

            List<CompletableFuture<Path>> artifactDownloads = artifactFiles.stream()
                    .map(file -> fetch(file, recordingSessionId, tempDir, downloadSemaphore, progressCallback,
                            recordingStreamClient::streamArtifactFile)
                            .exceptionally(throwable -> artifactMissing(file, recordingSessionId, throwable, progressCallback)))
                    .toList();

            // A recording file that did not arrive fails the whole download. Dropping it the way a
            // missing artifact is dropped would leave a profile with a hole in it that reads as a
            // quiet stretch -- the same defect the contiguity rule above exists to prevent, arrived
            // at by a different route.
            List<Path> recordingPaths = joinAll(recordingDownloads);
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

        } catch (CancellationException e) {
            LOG.info("Download cancelled: sessionId={}", recordingSessionId);
            throw e;
        } catch (Exception e) {
            LOG.error("Download failed: sessionId={} error={}", recordingSessionId, e.getMessage(), e);
            progressCallback.onError(e.getMessage());

            // Whoever asked for this has usually navigated away by now: the progress channel is the
            // only thing that carries the error, and it dies with the page that was watching it.
            Notifications.of(NotificationType.DOWNLOAD_FAILED)
                    .attribute("sessionId", recordingSessionId)
                    .errorType(e)
                    .emit();

            throw e;
        }
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
            ProgressCallback progressCallback,
            FileTransfer transfer) {

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

                Path target = tempDir.resolve(file.name());
                transfer.stream(recordingSessionId, file.id(), (inputStream, contentLength) -> {
                    long actualSize = contentLength > 0 ? contentLength : file.size();
                    progressCallback.onFileStart(file.name(), actualSize);
                    streamToFileWithProgress(inputStream, target, file.name(), progressCallback);
                });

                progressCallback.onFileComplete(file.name());
                return target;
            } finally {
                downloadSemaphore.release();
            }
        }, Schedulers.sharedVirtual());
    }

    /**
     * What a lost artifact costs: the download still succeeds without it. Said out loud, because
     * the recording that results looks complete and is missing its heap dump or its log.
     */
    private static Path artifactMissing(
            RepositoryFile file, String recordingSessionId, Throwable throwable, ProgressCallback progressCallback) {

        if (throwable instanceof CancellationException cancellation) {
            throw cancellation;
        }

        LOG.warn("Failed to download artifact: file={} error={}", file.name(), throwable.getMessage());
        progressCallback.onFileError(file.name(), throwable.getMessage());

        Notifications.of(NotificationType.DOWNLOAD_ARTIFACT_MISSING)
                .attribute("file", file.name())
                .attribute("sessionId", recordingSessionId)
                .errorType(throwable)
                .emit();

        return null;
    }

    private static List<Path> joinAll(List<CompletableFuture<Path>> downloads) {
        CompletableFuture.allOf(downloads.toArray(CompletableFuture[]::new)).join();
        return downloads.stream().map(CompletableFuture::join).toList();
    }

    private static void throwIfCancelled(ProgressCallback progressCallback) {
        if (progressCallback.isCancelled()) {
            throw new CancellationException("Download cancelled");
        }
    }

    /**
     * Pulls one file of a session onto this disk -- {@code streamRecordingFile} or
     * {@code streamArtifactFile}, which differ only in which of the hub's RPCs they call.
     */
    @FunctionalInterface
    private interface FileTransfer {
        void stream(String sessionId, String fileId, RecordingStreamClient.InputStreamConsumer consumer);
    }


    private static void streamToFileWithProgress(
            InputStream source,
            Path target,
            String fileName,
            ProgressCallback progressCallback) throws IOException {

        try (InputStream in = new ProgressTrackingInputStream(
                source, fileName, progressCallback::onFileProgress);
             OutputStream out = Files.newOutputStream(target)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                if (progressCallback.isCancelled()) {
                    throw new CancellationException("Download cancelled during file copy");
                }
                out.write(buffer, 0, bytesRead);
            }
        }
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
    private String recordingName(RecordingSessionResponse session, ChunkWindow.Selection window) {
        String name = sanitizeForFilename(projectName) + WINDOW_NAME_SEPARATOR
                + RECORDING_NAME_TIMESTAMP.format(Instant.ofEpochMilli(session.createdAt()));
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
            RecordingSessionResponse session, List<Path> recordingPaths, List<Path> artifactPaths,
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
