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

package cafe.jeffrey.microscope.core.manager.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.core.client.RemoteRecordingStreamClient;
import cafe.jeffrey.microscope.core.client.RemoteRepositoryClient;
import cafe.jeffrey.microscope.core.manager.RecordingsDownloadManager;
import cafe.jeffrey.microscope.core.manager.download.FileProgress;
import cafe.jeffrey.microscope.core.manager.download.ProgressCallback;
import cafe.jeffrey.microscope.core.manager.download.ProgressTrackingInputStream;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.resources.response.RecordingSessionResponse;
import cafe.jeffrey.microscope.core.resources.response.RepositoryFileResponse;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;

import java.io.IOException;
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import cafe.jeffrey.shared.common.Schedulers;

public class RemoteRecordingsDownloadManager implements RecordingsDownloadManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteRecordingsDownloadManager.class);

    /**
     * Maximum number of concurrent file downloads.
     */
    private static final int MAX_CONCURRENT_DOWNLOADS = 5;

    private final MicroscopeJeffreyDirs jeffreyDirs;
    private final RemoteRecordingStreamClient recordingStreamClient;
    private final RemoteRepositoryClient repositoryClient;
    private final RecordingsManager recordingsManager;
    private final OriginContext originContext;
    private final String projectName;

    public RemoteRecordingsDownloadManager(
            MicroscopeJeffreyDirs jeffreyDirs,
            RemoteRecordingStreamClient recordingStreamClient,
            RemoteRepositoryClient repositoryClient,
            RecordingsManager recordingsManager,
            OriginContext originContext,
            String projectName) {

        this.jeffreyDirs = jeffreyDirs;
        this.recordingStreamClient = recordingStreamClient;
        this.repositoryClient = repositoryClient;
        this.recordingsManager = recordingsManager;
        this.originContext = originContext;
        this.projectName = projectName;
    }

    @Override
    public void mergeAndDownloadSession(String recordingSessionId) {
        RecordingSessionResponse recordingSession = repositoryClient.recordingSession(
                recordingSessionId);

        List<RepositoryFile> files = recordingSession.files().stream()
                .map(RepositoryFileResponse::from)
                .filter(RepositoryFile::isFinished)
                .toList();

        processRecordingSession(recordingSessionId, files);
    }

    @Override
    public void mergeAndDownloadRecordings(String recordingSessionId, List<String> fileIds) {
        RecordingSessionResponse recordingSession = repositoryClient.recordingSession(
                recordingSessionId);

        List<RepositoryFile> files = recordingSession.files().stream()
                .map(RepositoryFileResponse::from)
                .filter(RepositoryFile::isFinished)
                .filter(file -> fileIds.contains(file.id()))
                .toList();

        processRecordingSession(recordingSessionId, files);
    }

    // TODO: Simplify this behaviour
    private void processRecordingSession(String recordingSessionId, List<RepositoryFile> files) {
        // At least one recording file must be present, otherwise nothing to merge and download
        // 0...n additional recording files can be present (e.g. HeapDump, logs, etc.)
        if (files.stream().noneMatch(RepositoryFile::isRecordingFile)) {
            throw Exceptions.emptyRecordingSession(recordingSessionId);
        }

        List<String> onlyRecordingFileIds = files.stream()
                .filter(RepositoryFile::isRecordingFile)
                .filter(RepositoryFile::isFinished)
                .map(RepositoryFile::id)
                .toList();

        try (TempDirectory tempDir = jeffreyDirs.newTempDir()) {
            // Download the merged recording file
            CompletableFuture<Path> recordingF = recordingStreamClient.downloadRecordings(
                            recordingSessionId, onlyRecordingFileIds)
                    .thenApply(resource -> copyToTempDir(resource, tempDir));

            // Download artifact files (heap dumps, logs, etc.)
            List<CompletableFuture<Path>> artifactsF = files.stream()
                    .filter(RepositoryFile::isArtifactFile)
                    .map(file -> recordingStreamClient.downloadArtifactFile(
                                    recordingSessionId, file.id())
                            .thenApply(resource -> copyToTempDir(resource, tempDir)))
                    .toList();

            // Wait for all downloads to complete
            Path recordingPath = recordingF.join();
            List<Path> artifactPaths = artifactsF.stream()
                    .map(CompletableFuture::join)
                    .toList();

            // Persist into Recordings storage with origin tags
            persistToRecordings(recordingSessionId, recordingPath, artifactPaths);
        }
    }

    private static Path copyToTempDir(Resource resource, TempDirectory tempDir) {
        try {
            String filename = resource.getFilename();
            Path target = tempDir.resolve(filename);
            Files.copy(resource.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } catch (IOException e) {
            throw new RuntimeException("Cannot copy file from remote source", e);
        }
    }

    /**
     * Downloads recordings with progress tracking.
     * This method is similar to {@link #mergeAndDownloadRecordings} but reports progress via the callback.
     *
     * @param recordingSessionId the recording session ID
     * @param fileIds            the list of file IDs to download
     * @param progressCallback   callback for receiving progress updates
     */
    public void mergeAndDownloadRecordingsWithProgress(
            String recordingSessionId,
            List<String> fileIds,
            ProgressCallback progressCallback) {

        RecordingSessionResponse recordingSession = repositoryClient.recordingSession(
                recordingSessionId);

        List<RepositoryFile> files = recordingSession.files().stream()
                .map(RepositoryFileResponse::from)
                .filter(RepositoryFile::isFinished)
                .filter(file -> fileIds.contains(file.id()))
                .toList();

        processRecordingSessionWithProgress(recordingSession, files, progressCallback);
    }

    private void processRecordingSessionWithProgress(
            RecordingSessionResponse recordingSession,
            List<RepositoryFile> files,
            ProgressCallback progressCallback) {

        String recordingSessionId = recordingSession.id();

        // At least one recording file must be present
        if (files.stream().noneMatch(RepositoryFile::isRecordingFile)) {
            throw Exceptions.emptyRecordingSession(recordingSessionId);
        }

        List<RepositoryFile> recordingFiles = files.stream()
                .filter(RepositoryFile::isRecordingFile)
                .toList();

        List<RepositoryFile> artifactFiles = files.stream()
                .filter(RepositoryFile::isArtifactFile)
                .toList();

        List<String> recordingFileIds = recordingFiles.stream()
                .map(RepositoryFile::id)
                .toList();

        // Calculate total: 1 merged recording + individual artifact files
        long mergedSizeEstimate = recordingFiles.stream().mapToLong(RepositoryFile::size).sum();
        long totalBytes = files.stream().mapToLong(RepositoryFile::size).sum();
        int totalFiles = 1 + artifactFiles.size();
        String mergedFileName = buildMergedFileName(recordingSession);

        LOG.info("Starting parallel download with progress tracking: sessionId={} files={} totalBytes={} maxConcurrent={}",
                recordingSessionId, totalFiles, totalBytes, MAX_CONCURRENT_DOWNLOADS);

        // Notify start
        progressCallback.onStart(totalFiles, totalBytes);

        // Report all files as pending upfront so the UI can show them immediately
        List<FileProgress> pendingFiles = new ArrayList<>();
        pendingFiles.add(FileProgress.pending(mergedFileName, mergedSizeEstimate));
        for (RepositoryFile artifact : artifactFiles) {
            pendingFiles.add(FileProgress.pending(artifact.name(), artifact.size()));
        }
        progressCallback.onFilesDiscovered(pendingFiles);

        // Check for cancellation
        if (progressCallback.isCancelled()) {
            throw new CancellationException("Download cancelled");
        }

        // Semaphore to limit concurrent downloads
        Semaphore downloadSemaphore = new Semaphore(MAX_CONCURRENT_DOWNLOADS);

        try (TempDirectory tempDir = jeffreyDirs.newTempDir()) {
            // Download merged recording file with streaming progress

            if (progressCallback.isCancelled()) {
                throw new CancellationException("Download cancelled");
            }

            Path recordingPath = tempDir.resolve(mergedFileName);
            recordingStreamClient.streamRecordings(
                    recordingSessionId, recordingFileIds,
                    (inputStream, contentLength) -> {
                        long actualSize = contentLength > 0 ? contentLength : mergedSizeEstimate;
                        progressCallback.onFileStart(mergedFileName, actualSize);
                        streamToFileWithProgress(
                                inputStream, recordingPath, mergedFileName, progressCallback);
                    });

            LOG.info("Recording file received: file={} size={}", mergedFileName, FileSystemUtils.size(recordingPath));
            progressCallback.onFileComplete(mergedFileName);

            // Download artifact files in parallel with concurrency limit
            List<Path> artifactPaths = new ArrayList<>();

            if (!artifactFiles.isEmpty()) {
                List<CompletableFuture<Path>> artifactFutures = artifactFiles.stream()
                        .map(artifactFile -> CompletableFuture.supplyAsync(() -> {
                            try {
                                downloadSemaphore.acquire();
                                try {
                                    if (progressCallback.isCancelled()) {
                                        return null;
                                    }

                                    Path artifactPath = tempDir.resolve(artifactFile.name());
                                    recordingStreamClient.streamArtifactFile(
                                            recordingSessionId, artifactFile.id(),
                                            (inputStream, contentLength) -> {
                                                long actualSize = contentLength > 0 ? contentLength : artifactFile.size();
                                                progressCallback.onFileStart(artifactFile.name(), actualSize);
                                                streamToFileWithProgress(
                                                        inputStream, artifactPath, artifactFile.name(), progressCallback);
                                            });

                                    progressCallback.onFileComplete(artifactFile.name());
                                    return artifactPath;
                                } finally {
                                    downloadSemaphore.release();
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                progressCallback.onFileError(artifactFile.name(), "Download interrupted");
                                return null;
                            } catch (Exception e) {
                                LOG.warn("Failed to download artifact: file={} error={}", artifactFile.name(), e.getMessage());
                                progressCallback.onFileError(artifactFile.name(), e.getMessage());
                                return null;
                            }
                        }, Schedulers.sharedVirtual()))
                        .toList();

                // Wait for all artifact downloads to complete
                CompletableFuture.allOf(artifactFutures.toArray(new CompletableFuture[0])).join();

                // Collect successful downloads (filter out nulls from failed/cancelled downloads)
                for (CompletableFuture<Path> future : artifactFutures) {
                    Path path = future.join();
                    if (path != null) {
                        artifactPaths.add(path);
                    }
                }
            }

            // Check cancellation before processing
            if (progressCallback.isCancelled()) {
                throw new CancellationException("Download cancelled");
            }

            // Processing phase
            progressCallback.onProcessing();

            // Persist into Recordings storage with origin tags
            persistToRecordings(recordingSessionId, recordingPath, artifactPaths);

            // Completed successfully
            progressCallback.onComplete();
            LOG.info("Parallel download completed: sessionId={} artifacts={}", recordingSessionId, artifactPaths.size());

        } catch (CancellationException e) {
            LOG.info("Download cancelled: sessionId={}", recordingSessionId);
            throw e;
        } catch (Exception e) {
            LOG.error("Download failed: sessionId={} error={}", recordingSessionId, e.getMessage(), e);
            progressCallback.onError(e.getMessage());
            throw e;
        }
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

    private static final DateTimeFormatter MERGED_FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

    private String buildMergedFileName(RecordingSessionResponse session) {
        String name = sanitizeForFilename(projectName);
        String timestamp = MERGED_FILE_TIMESTAMP.format(Instant.ofEpochMilli(session.createdAt()));
        return name + "_" + timestamp + ".jfr.lz4";
    }

    private static String sanitizeForFilename(String value) {
        if (value == null || value.isBlank()) {
            return "recording";
        }
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
        return sanitized.isEmpty() ? "recording" : sanitized;
    }

    /**
     * Persist the merged recording + any artifact files into Recordings storage,
     * tagged with the {@code origin.*} system tags from {@link #originContext}.
     */
    private void persistToRecordings(
            String recordingSessionId, Path recordingPath, List<Path> artifactPaths) {

        Map<String, String> originTags = originContext.toTagMap(recordingSessionId);
        recordingsManager.createDownloadedRecording(
                recordingSessionId, recordingPath, artifactPaths, originTags);
    }
}
