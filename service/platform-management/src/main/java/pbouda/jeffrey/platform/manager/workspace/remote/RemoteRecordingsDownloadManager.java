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

package pbouda.jeffrey.platform.manager.workspace.remote;

import org.springframework.core.io.Resource;
import pbouda.jeffrey.shared.exception.Exceptions;
import pbouda.jeffrey.shared.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.filesystem.JeffreyDirs.Directory;
import pbouda.jeffrey.shared.model.ProjectInfo;
import pbouda.jeffrey.shared.model.repository.RepositoryFile;
import pbouda.jeffrey.shared.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.platform.manager.RecordingsDownloadManager;
import pbouda.jeffrey.platform.resources.response.RecordingSessionResponse;
import pbouda.jeffrey.platform.resources.response.RepositoryFileResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
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
    public void mergeAndDownloadRecordings(String recordingSessionId, List<String> fileIds) {
        RecordingSessionResponse recordingSession = remoteWorkspaceClient.recordingSession(
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
            CompletableFuture<Path> recordingF = remoteWorkspaceClient.downloadRecordings(
                            workspaceInfo.originId(), projectInfo.originId(), recordingSessionId, onlyRecordingFileIds)
                    .thenApply(resource -> copyToTempDir(resource, tempDir));

            // Download artifact files (heap dumps, logs, etc.)
            List<CompletableFuture<Path>> artifactsF = files.stream()
                    .filter(RepositoryFile::isArtifactFile)
                    .map(file -> remoteWorkspaceClient.downloadFile(
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
            Files.copy(resource.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new RuntimeException("Cannot copy file from remote source", e);
        }
    }

    @Override
    public void createNewRecording(String recordingName, Path recordingPath, List<Path> artifactPaths) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }
}
