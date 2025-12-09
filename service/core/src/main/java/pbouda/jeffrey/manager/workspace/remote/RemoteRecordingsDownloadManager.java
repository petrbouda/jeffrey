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

package pbouda.jeffrey.manager.workspace.remote;

import org.springframework.core.io.Resource;
import pbouda.jeffrey.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.common.filesystem.JeffreyDirs.Directory;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.common.model.repository.RepositoryFile;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.exception.Exceptions;
import pbouda.jeffrey.manager.RecordingsDownloadManager;
import pbouda.jeffrey.resources.response.RecordingSessionResponse;
import pbouda.jeffrey.resources.response.RepositoryFileResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RemoteRecordingsDownloadManager implements RecordingsDownloadManager {

    private static final String UNSUPPORTED =
            "Not supported operation in " + RemoteRecordingsDownloadManager.class.getSimpleName();

    private final JeffreyDirs jeffreyDirs;
    private final ProjectInfo projectInfo;
    private final WorkspaceInfo workspaceInfo;
    private final RemoteWorkspaceClient remoteWorkspaceClient;
    private final RecordingsDownloadManager commonDownloadManager;

    public RemoteRecordingsDownloadManager(
            JeffreyDirs jeffreyDirs,
            ProjectInfo projectInfo,
            WorkspaceInfo workspaceInfo,
            RemoteWorkspaceClient remoteWorkspaceClient,
            RecordingsDownloadManager commonDownloadManager) {

        this.jeffreyDirs = jeffreyDirs;
        this.projectInfo = projectInfo;
        this.workspaceInfo = workspaceInfo;
        this.remoteWorkspaceClient = remoteWorkspaceClient;
        this.commonDownloadManager = commonDownloadManager;
    }

    @Override
    public void mergeAndDownloadSession(String recordingSessionId) {
        RecordingSessionResponse recordingSession = remoteWorkspaceClient.recordingSession(
                workspaceInfo.originId(), projectInfo.originId(), recordingSessionId);

        List<RepositoryFile> files = recordingSession.files().stream()
                .map(RepositoryFileResponse::from)
                .filter(RepositoryFile::isFinished)
                .toList();

        processRecordingSession(recordingSessionId, files);
    }

    @Override
    public void mergeAndDownloadSelectedRawRecordings(String recordingSessionId, List<String> fileIds) {
        RecordingSessionResponse recordingSession = remoteWorkspaceClient.recordingSession(
                workspaceInfo.originId(), projectInfo.originId(), recordingSessionId);

        List<RepositoryFile> files = recordingSession.files().stream()
                .map(RepositoryFileResponse::from)
                .filter(RepositoryFile::isFinished)
                .filter(file -> fileIds.contains(file.id()))
                .toList();

        processRecordingSession(recordingSessionId, files);
    }

    private void processRecordingSession(String recordingSessionId, List<RepositoryFile> files) {
        // At least one recording file must be present, otherwise nothing to merge and download
        // 0...n additional recording files can be present (e.g. HeapDump, logs, etc.)
        if (files.stream().noneMatch(RepositoryFile::isRecordingFile)) {
            throw Exceptions.emptyRecordingSession(recordingSessionId);
        }

        // Resolve the type of the recording file (e.g. JFR, ...)
        SupportedRecordingFile recordingType = resolveRecordingFileType(files);

        List<String> onlyRecordingFileIds = files.stream()
                .filter(RepositoryFile::isRecordingFile)
                .map(RepositoryFile::id)
                .toList();

        try (Directory tempDir = jeffreyDirs.newTempDir()) {
            CompletableFuture<RepositoryFile> recordingFuture = remoteWorkspaceClient.downloadRecordings(
                            workspaceInfo.originId(), projectInfo.originId(), recordingSessionId, onlyRecordingFileIds)
                    .thenApply(resource -> copyRecordingFile(resource, recordingType, tempDir));

            List<CompletableFuture<RepositoryFile>> additionalFilesFutures = files.stream()
                    .filter(RepositoryFile::isAdditionalFile)
                    .map(file -> {
                        CompletableFuture<Resource> future = remoteWorkspaceClient.downloadFile(
                                workspaceInfo.originId(), projectInfo.originId(), recordingSessionId, file.id());

                        return future.thenApply(downloadedFile -> copyAdditionalFile(file, downloadedFile, tempDir));
                    })
                    .toList();

            List<RepositoryFile> repositoryFiles = waitForAll(recordingFuture, additionalFilesFutures);

            // Create a new recording in the local repository
            commonDownloadManager.createNewRecording(recordingSessionId, repositoryFiles);
        }
    }

    private static List<RepositoryFile> waitForAll(
            CompletableFuture<RepositoryFile> future,
            List<CompletableFuture<RepositoryFile>> otherFutures) {

        CompletableFuture.allOf(future, CompletableFuture.allOf(otherFutures.toArray(new CompletableFuture[0])))
                .join();

        List<RepositoryFile> results = new ArrayList<>();
        results.add(future.join());
        for (CompletableFuture<RepositoryFile> otherFuture : otherFutures) {
            results.add(otherFuture.join());
        }
        return results;
    }

    private static SupportedRecordingFile resolveRecordingFileType(List<RepositoryFile> files) {
        Optional<RepositoryFile> firstRecording = files.stream()
                .filter(RepositoryFile::isRecordingFile)
                .findFirst();

        if (firstRecording.isEmpty()) {
            throw new IllegalArgumentException("No recording file found in the provided files");
        }

        return firstRecording.get().fileType();
    }

    private static RepositoryFile copyAdditionalFile(RepositoryFile file, Resource resource, Directory tempDir) {
        try {
            Path target = tempDir.resolve(resource.getFilename());
            long size = Files.copy(resource.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return file.withFilePath(target, size);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create an additional file from remote source", e);
        }
    }

    private static RepositoryFile copyRecordingFile(
            Resource resource, SupportedRecordingFile recordingFile, Directory tempDir) {
        try {
            String filename = resource.getFilename();
            Path target = tempDir.resolve(filename);
            long size = Files.copy(resource.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return new RepositoryFile(
                    filename,
                    filename,
                    null,
                    size,
                    recordingFile,
                    true,
                    true,
                    RecordingStatus.FINISHED,
                    target);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create a recording file from remote source", e);
        }
    }

    @Override
    public void downloadSession(String recordingSessionId) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public void downloadSelectedRawRecordings(String recordingSessionId, List<String> rawRecordingIds) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public void createNewRecording(String recordingName, List<RepositoryFile> repositoryFiles) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }
}
