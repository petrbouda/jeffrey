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
import cafe.jeffrey.hub.client.FileDownloadClient;
import cafe.jeffrey.hub.client.RepositoryClient;
import cafe.jeffrey.recordings.core.download.FileProgress;
import cafe.jeffrey.recordings.core.download.ProgressCallback;
import cafe.jeffrey.recordings.core.download.ProgressTrackingInputStream;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.hub.client.dto.RecordingSessionResponse;
import cafe.jeffrey.hub.client.dto.RepositoryFileResponse;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.common.model.repository.RecordingChunks;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;

/**
 * Brings a hub session's files onto this machine and stores them as one local recording.
 * <p>
 * The hub only serves files, one download each, and they are stored as they came. Every finished,
 * non-transient file is downloaded the same way, under a concurrency cap and with per-file
 * progress; the JFR chunks become the recording's files, oldest first, and everything else is
 * stored beside them. Nothing is joined: a profile is parsed chunk by chunk anyway, so a joined
 * file would only be a copy. There is one pipeline: a caller without a screen passes
 * {@link ProgressCallback#NONE} rather than taking a different path.
 * <p>
 * A chunk that fails to arrive fails the download, because a recording with a chunk missing is
 * not that recording; the other transfers still in flight are stopped rather than run to an end
 * nobody will use. Any other file that fails to arrive is left out, reported through the
 * callback and a {@link NotificationType#DOWNLOAD_FILE_MISSING} notification, and the download
 * still succeeds — a recording without its GC log is still the recording.
 */
public class RemoteRecordingsDownloadManager implements RecordingsDownloadManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteRecordingsDownloadManager.class);

    /**
     * Maximum number of concurrent file downloads.
     */
    private static final int MAX_CONCURRENT_DOWNLOADS = 5;

    private static final int COPY_BUFFER_SIZE = 8192;

    private static final String CANCELLED_MESSAGE = "Download cancelled";

    private final TempDirProvider tempDirProvider;
    private final FileDownloadClient fileDownloadClient;
    private final RepositoryClient repositoryClient;
    private final RecordingsCoreManager recordingsManager;
    private final OriginContext originContext;
    private final String projectName;

    public RemoteRecordingsDownloadManager(
            TempDirProvider tempDirProvider,
            FileDownloadClient fileDownloadClient,
            RepositoryClient repositoryClient,
            RecordingsCoreManager recordingsManager,
            OriginContext originContext,
            String projectName) {

        this.tempDirProvider = tempDirProvider;
        this.fileDownloadClient = fileDownloadClient;
        this.repositoryClient = repositoryClient;
        this.recordingsManager = recordingsManager;
        this.originContext = originContext;
        this.projectName = projectName;
    }

    @Override
    public String downloadSession(String sessionId) {
        RecordingSessionResponse session = repositoryClient.recordingSession(sessionId);
        return download(session, finishedFiles(session, Optional.empty()), ProgressCallback.NONE);
    }

    @Override
    public String downloadFiles(String sessionId, List<String> fileIds) {
        return downloadFiles(sessionId, fileIds, ProgressCallback.NONE);
    }

    @Override
    public String downloadFiles(String sessionId, List<String> fileIds, ProgressCallback progress) {
        RecordingSessionResponse session = repositoryClient.recordingSession(sessionId);
        return download(session, finishedFiles(session, Optional.of(Set.copyOf(fileIds))), progress);
    }

    /**
     * The session's finished files, narrowed to the requested ids when a selection was made. An
     * empty selection is a selection of nothing, and fails below as a session without a chunk.
     */
    private static List<RepositoryFile> finishedFiles(RecordingSessionResponse session, Optional<Set<String>> fileIds) {
        return session.files().stream()
                .map(RepositoryFileResponse::from)
                .filter(RepositoryFile::isFinished)
                .filter(file -> fileIds.map(ids -> ids.contains(file.id())).orElse(true))
                .toList();
    }

    /**
     * The files a session download brings over, split by the one thing that differs between
     * them: chunks are the recording, everything else is stored beside it. {@link #all()} lists
     * the chunks first.
     */
    private record SessionFiles(List<RepositoryFile> chunks, List<RepositoryFile> others) {

        /**
         * @param files the session's finished files; a transient one among them is left out here,
         *              because the hub never serves it
         */
        static SessionFiles of(String sessionId, List<RepositoryFile> files) {
            List<RepositoryFile> chunks = RecordingChunks.finished(files);
            if (chunks.isEmpty()) {
                throw Exceptions.emptyRecordingSession(sessionId);
            }
            List<RepositoryFile> others = files.stream()
                    .filter(file -> !file.isRecordingChunk())
                    .filter(file -> !file.isTransient())
                    .toList();
            return new SessionFiles(chunks, others);
        }

        List<RepositoryFile> all() {
            List<RepositoryFile> all = new ArrayList<>(chunks);
            all.addAll(others);
            return all;
        }

        long totalBytes() {
            return all().stream().mapToLong(RepositoryFile::size).sum();
        }
    }

    private String download(
            RecordingSessionResponse recordingSession,
            List<RepositoryFile> files,
            ProgressCallback progressCallback) {

        String recordingSessionId = recordingSession.id();
        SessionFiles sessionFiles = SessionFiles.of(recordingSessionId, files);
        List<RepositoryFile> allFiles = sessionFiles.all();
        Transfer transfer = new Transfer(progressCallback);

        LOG.info("Starting session download: sessionId={} files={} totalBytes={} maxConcurrent={}",
                recordingSessionId, allFiles.size(), sessionFiles.totalBytes(), MAX_CONCURRENT_DOWNLOADS);

        // Notify start
        progressCallback.onStart(allFiles.size(), sessionFiles.totalBytes());

        // Report all files as pending upfront so the UI can show them immediately
        progressCallback.onFilesDiscovered(allFiles.stream()
                .map(file -> FileProgress.pending(file.name(), file.size()))
                .toList());

        // Check for cancellation
        if (progressCallback.isCancelled()) {
            throw new CancellationException(CANCELLED_MESSAGE);
        }

        // Semaphore to limit concurrent downloads
        Semaphore downloadSemaphore = new Semaphore(MAX_CONCURRENT_DOWNLOADS);

        try (TempDirectory tempDir = tempDirProvider.newTempDir()) {
            // Every file the same way, chunks and others alike; a chunk that fails to arrive fails
            // the download, because a recording with a chunk missing is not that recording.
            List<CompletableFuture<Path>> chunkFutures = sessionFiles.chunks().stream()
                    .map(chunk -> CompletableFuture.supplyAsync(() -> downloadChunk(
                            recordingSessionId, chunk, tempDir, downloadSemaphore, transfer), Schedulers.sharedVirtual()))
                    .toList();
            List<CompletableFuture<Path>> otherFutures = sessionFiles.others().stream()
                    .map(other -> CompletableFuture.supplyAsync(
                            () -> downloadOtherFileLeniently(recordingSessionId, other, tempDir, downloadSemaphore, transfer),
                            Schedulers.sharedVirtual()))
                    .toList();

            // Every transfer settles before anything is judged: a stopped one is still writing
            // into the temp directory until it does, and the directory goes when this block ends.
            awaitAll(chunkFutures, otherFutures);

            List<Path> chunkPaths = chunkResults(chunkFutures);
            // Collect successful downloads (filter out nulls from failed/cancelled downloads)
            List<Path> otherPaths = otherFutures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList();

            // Check cancellation before processing
            if (progressCallback.isCancelled() || chunkPaths.contains(null)) {
                throw new CancellationException(CANCELLED_MESSAGE);
            }

            // Processing phase: store the chunks as the recording, the other files beside them
            progressCallback.onProcessing();

            String recordingId = persistToRecordings(
                    recordingSession, recordingSessionId, chunkPaths, otherPaths);

            // Completed successfully
            progressCallback.onComplete();
            LOG.info("Session download completed: sessionId={} recordingId={} chunks={} otherFiles={}",
                    recordingSessionId, recordingId, chunkPaths.size(), otherPaths.size());

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
     * Waits for every transfer to settle, however it settled; what each one came to is read off
     * its own future afterwards.
     */
    private static void awaitAll(List<CompletableFuture<Path>> chunks, List<CompletableFuture<Path>> others) {
        CompletableFuture.allOf(Stream.concat(chunks.stream(), others.stream())
                        .toArray(CompletableFuture[]::new))
                .handle((ignored, failure) -> null)
                .join();
    }

    /**
     * The chunks' paths, or the failure of the first chunk that did not arrive. A failure is
     * thrown as itself, not in the {@link CompletionException} a future wraps it in: a
     * cancellation has to reach the caller as a cancellation, and an error with its own message
     * and type rather than the wrapper's. The chunks a failure stopped were cancelled by it, so a
     * real failure is preferred to the cancellations around it.
     */
    private static List<Path> chunkResults(List<CompletableFuture<Path>> chunkFutures) {
        CancellationException cancellation = null;
        for (CompletableFuture<Path> future : chunkFutures) {
            try {
                future.join();
            } catch (CompletionException e) {
                RuntimeException failure = unwrap(e);
                if (!(failure instanceof CancellationException cancelled)) {
                    throw failure;
                }
                cancellation = cancelled;
            }
        }
        if (cancellation != null) {
            throw cancellation;
        }
        return chunkFutures.stream().map(CompletableFuture::join).toList();
    }

    private static RuntimeException unwrap(CompletionException e) {
        return e.getCause() instanceof RuntimeException cause ? cause : e;
    }

    /**
     * Downloads one chunk, and when it does not arrive, stops the rest: there is no recording
     * without it, so the other transfers would only be spending bandwidth on a result that is
     * already lost.
     */
    private Path downloadChunk(
            String recordingSessionId,
            RepositoryFile chunk,
            TempDirectory tempDir,
            Semaphore downloadSemaphore,
            Transfer transfer) {

        try {
            return downloadFile(recordingSessionId, chunk, tempDir, downloadSemaphore, transfer);
        } catch (CancellationException e) {
            transfer.onFileError(chunk.name(), e.getMessage());
            throw e;
        } catch (Exception e) {
            LOG.warn("Failed to download a chunk, stopping the session download: sessionId={} file={} error={}",
                    recordingSessionId, chunk.name(), e.getMessage());
            transfer.abort();
            transfer.onFileError(chunk.name(), e.getMessage());
            throw e;
        }
    }

    /**
     * Downloads one file under the concurrency limit, reporting its progress. {@code null} when
     * the download was cancelled before this file's turn came.
     */
    private Path downloadFile(
            String recordingSessionId,
            RepositoryFile file,
            TempDirectory tempDir,
            Semaphore downloadSemaphore,
            Transfer transfer) {

        try {
            downloadSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Download interrupted");
        }
        try {
            if (transfer.isCancelled()) {
                return null;
            }

            Path target = tempDir.resolve(file.name());
            fileDownloadClient.streamFile(recordingSessionId, file.id(), (inputStream, contentLength) -> {
                long expectedSize = contentLength > 0 ? contentLength : file.size();
                transfer.onFileStart(file.name(), expectedSize);
                streamToFileWithProgress(inputStream, target, file.name(), expectedSize, transfer);
            });

            transfer.onFileComplete(file.name());
            return target;
        } finally {
            downloadSemaphore.release();
        }
    }

    /**
     * A file that is not a chunk is wanted but not required: when it fails to arrive, the
     * download still succeeds without it, and a notification says so.
     */
    private Path downloadOtherFileLeniently(
            String recordingSessionId,
            RepositoryFile file,
            TempDirectory tempDir,
            Semaphore downloadSemaphore,
            Transfer transfer) {

        try {
            return downloadFile(recordingSessionId, file, tempDir, downloadSemaphore, transfer);
        } catch (CancellationException e) {
            transfer.onFileError(file.name(), e.getMessage());
            return null;
        } catch (Exception e) {
            LOG.warn("Failed to download session file: file={} error={}", file.name(), e.getMessage());
            transfer.onFileError(file.name(), e.getMessage());

            // Returning null drops this file out of the collected result and the download still
            // reports success -- so a recording can arrive complete-looking with its heap dump or
            // its log quietly absent.
            Notifications.of(NotificationType.DOWNLOAD_FILE_MISSING)
                    .attribute("file", file.name())
                    .attribute("sessionId", recordingSessionId)
                    .errorType(e)
                    .emit();

            return null;
        }
    }

    /**
     * Copies the stream to the target, stopping on cancellation and on a stream that runs past
     * the size the hub announced for it. A finished file does not grow, so more bytes than
     * announced is a hub that is not sending what it listed, and the file is refused rather than
     * written for as long as the hub cares to send.
     */
    private static void streamToFileWithProgress(
            InputStream source,
            Path target,
            String fileName,
            long expectedSize,
            Transfer transfer) throws IOException {

        try (InputStream in = new ProgressTrackingInputStream(
                source, fileName, transfer::onFileProgress);
             OutputStream out = Files.newOutputStream(target)) {

            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            long written = 0;
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                if (transfer.isCancelled()) {
                    throw new CancellationException("Download cancelled during file copy");
                }
                written += bytesRead;
                if (expectedSize > 0 && written > expectedSize) {
                    throw new IOException("The hub sent more than it announced for the file: file="
                            + fileName + " announced=" + expectedSize);
                }
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * One session download as its transfers see it: the caller's progress listener, plus the one
     * thing the transfers decide among themselves — that a chunk has failed and the rest should
     * stop. Cancelled from the caller's side or aborted from within, the answer to
     * {@link #isCancelled()} is the same, so every copy loop stops the same way.
     */
    private static final class Transfer implements ProgressCallback {

        private final ProgressCallback delegate;
        private final AtomicBoolean aborted = new AtomicBoolean(false);

        private Transfer(ProgressCallback delegate) {
            this.delegate = delegate;
        }

        void abort() {
            aborted.set(true);
        }

        @Override
        public boolean isCancelled() {
            return aborted.get() || delegate.isCancelled();
        }

        @Override
        public void onStart(int totalFiles, long totalBytes) {
            delegate.onStart(totalFiles, totalBytes);
        }

        @Override
        public void onFilesDiscovered(List<FileProgress> pendingFiles) {
            delegate.onFilesDiscovered(pendingFiles);
        }

        @Override
        public void onFileStart(String fileName, long fileSize) {
            delegate.onFileStart(fileName, fileSize);
        }

        @Override
        public void onFileProgress(String fileName, long bytesDownloaded) {
            delegate.onFileProgress(fileName, bytesDownloaded);
        }

        @Override
        public void onFileComplete(String fileName) {
            delegate.onFileComplete(fileName);
        }

        @Override
        public void onFileError(String fileName, String errorMessage) {
            delegate.onFileError(fileName, errorMessage);
        }

        @Override
        public void onProcessing() {
            delegate.onProcessing();
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }

        @Override
        public void onError(String errorMessage) {
            delegate.onError(errorMessage);
        }
    }

    private static final DateTimeFormatter RECORDING_FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

    /**
     * What the recording is called: the project and the session's start, since a session has no
     * one file to be named after.
     */
    private String buildRecordingName(RecordingSessionResponse session) {
        String name = sanitizeForFilename(projectName);
        String timestamp = RECORDING_FILE_TIMESTAMP.format(Instant.ofEpochMilli(session.createdAt()));
        return name + "_" + timestamp;
    }

    private static String sanitizeForFilename(String value) {
        if (value == null || value.isBlank()) {
            return "recording";
        }
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
        return sanitized.isEmpty() ? "recording" : sanitized;
    }

    /**
     * Persist the session's chunks as the recording's files + the session's other files into
     * Recordings storage, tagged with the {@code origin.*} system tags from {@link #originContext}.
     * The files are moved out of the temp directory, which is discarded right after.
     *
     * @return id of the newly created local recording
     */
    private String persistToRecordings(
            RecordingSessionResponse session, String recordingSessionId, List<Path> chunks, List<Path> additionalFiles) {

        Map<String, String> originTags = originContext.toTagMap(recordingSessionId);
        return recordingsManager.createDownloadedRecording(
                buildRecordingName(session), chunks, additionalFiles, originTags);
    }
}
