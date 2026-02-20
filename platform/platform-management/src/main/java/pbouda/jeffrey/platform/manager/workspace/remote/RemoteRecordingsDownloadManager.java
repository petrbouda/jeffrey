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

package pbouda.jeffrey.platform.manager.workspace.remote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import pbouda.jeffrey.platform.manager.RecordingsDownloadManager;
import pbouda.jeffrey.platform.manager.download.FileProgress;
import pbouda.jeffrey.platform.manager.download.ProgressCallback;
import pbouda.jeffrey.platform.manager.download.ProgressTrackingInputStream;
import pbouda.jeffrey.platform.resources.response.RecordingSessionResponse;
import pbouda.jeffrey.platform.resources.response.RepositoryFileResponse;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs.Directory;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.repository.RepositoryFile;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import pbouda.jeffrey.shared.common.Schedulers;

public class RemoteRecordingsDownloadManager implements RecordingsDownloadManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteRecordingsDownloadManager.class);

    /**
     * Maximum number of concurrent file downloads.
     */
    private static final int MAX_CONCURRENT_DOWNLOADS = 3;

    private static final String UNSUPPORTED =
            "Not supported operation in " + RemoteRecordingsDownloadManager.class.getSimpleName();

    private final JeffreyDirs jeffreyDirs;
    private final ProjectInfo projectInfo;
    private final WorkspaceInfo workspaceInfo;
    private final RemoteRecordingStreamClient recordingStreamClient;
    private final RemoteRepositoryClient repositoryClient;
    private final RecordingsDownloadManager commonDownloadManager;

    public RemoteRecordingsDownloadManager(
            JeffreyDirs jeffreyDirs,
            ProjectInfo projectInfo,
            WorkspaceInfo workspaceInfo,
            RemoteRecordingStreamClient recordingStreamClient,
            RemoteRepositoryClient repositoryClient,
            RecordingsDownloadManager commonDownloadManager) {

        this.jeffreyDirs = jeffreyDirs;
        this.projectInfo = projectInfo;
        this.workspaceInfo = workspaceInfo;
        this.recordingStreamClient = recordingStreamClient;
        this.repositoryClient = repositoryClient;
        this.commonDownloadManager = commonDownloadManager;
    }

    @Override
    public void mergeAndDownloadSession(String recordingSessionId) {
        RecordingSessionResponse recordingSession = repositoryClient.recordingSession(
                workspaceInfo.originId(), projectInfo.originId(), recordingSessionId);

        List<RepositoryFile> files = recordingSession.files().stream()
                .map(RepositoryFileResponse::from)
                .filter(RepositoryFile::isFinished)
                .toList();

        processRecordingSession(recordingSessionId, files);
    }

    @Override
    public void mergeAndDownloadRecordings(String recordingSessionId, List<String> fileIds) {
        RecordingSessionResponse recordingSession = repositoryClient.recordingSession(
                workspaceInfo.originId(), projectInfo.originId(), recordingSessionId);

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

        try (Directory tempDir = jeffreyDirs.newTempDir()) {
            // Download the merged recording file
            CompletableFuture<Path> recordingF = recordingStreamClient.downloadRecordings(
                            workspaceInfo.originId(), projectInfo.originId(), recordingSessionId, onlyRecordingFileIds)
                    .thenApply(resource -> copyToTempDir(resource, tempDir));

            // Download artifact files (heap dumps, logs, etc.)
            List<CompletableFuture<Path>> artifactsF = files.stream()
                    .filter(RepositoryFile::isArtifactFile)
                    .map(file -> recordingStreamClient.downloadFile(
                                    workspaceInfo.originId(), projectInfo.originId(), recordingSessionId, file.id())
                            .thenApply(resource -> copyToTempDir(resource, tempDir)))
                    .toList();

            // Wait for all downloads to complete
            Path recordingPath = recordingF.join();
            List<Path> artifactPaths = artifactsF.stream()
                    .map(CompletableFuture::join)
                    .toList();

            // Create a new recording in the local recordings storage
            commonDownloadManager.createNewRecording(recordingSessionId, recordingPath, artifactPaths);
        }
    }

    private static Path copyToTempDir(Resource resource, Directory tempDir) {
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
                workspaceInfo.originId(), projectInfo.originId(), recordingSessionId);

        List<RepositoryFile> files = recordingSession.files().stream()
                .map(RepositoryFileResponse::from)
                .filter(RepositoryFile::isFinished)
                .filter(file -> fileIds.contains(file.id()))
                .toList();

        processRecordingSessionWithProgress(recordingSessionId, files, progressCallback);
    }

    private void processRecordingSessionWithProgress(
            String recordingSessionId,
            List<RepositoryFile> files,
            ProgressCallback progressCallback) {

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
        String mergedFileName = "merged-recording.jfr.lz4";

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

        try (Directory tempDir = jeffreyDirs.newTempDir()) {
            // Download merged recording file with streaming progress

            if (progressCallback.isCancelled()) {
                throw new CancellationException("Download cancelled");
            }

            Path recordingPath = tempDir.resolve(mergedFileName);
            recordingStreamClient.streamRecordings(
                    workspaceInfo.originId(), projectInfo.originId(), recordingSessionId, recordingFileIds,
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
                                    recordingStreamClient.streamFile(
                                            workspaceInfo.originId(), projectInfo.originId(),
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

            // Create a new recording in the local recordings storage
            commonDownloadManager.createNewRecording(recordingSessionId, recordingPath, artifactPaths);

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
                source, fileName,
                (name, bytes) -> progressCallback.onFileProgress(name, bytes));
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

    @Override
    public void createNewRecording(String recordingName, Path recordingPath, List<Path> artifactPaths) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }
}
