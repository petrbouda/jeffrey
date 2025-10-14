/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RepositoryFile;
import pbouda.jeffrey.provider.api.NewRecordingHolder;
import pbouda.jeffrey.provider.api.model.recording.NewRecording;
import pbouda.jeffrey.provider.reader.jfr.chunk.Recordings;
import pbouda.jeffrey.recording.ProjectRecordingInitializer;

import java.nio.file.Path;
import java.util.List;

public class RecordingsDownloadManagerImpl implements RecordingsDownloadManager {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingsDownloadManagerImpl.class);

    private final ProjectInfo projectInfo;
    private final ProjectRecordingInitializer recordingInitializer;
    private final RepositoryManager repositoryManager;

    public RecordingsDownloadManagerImpl(
            ProjectInfo projectInfo,
            ProjectRecordingInitializer recordingInitializer,
            RepositoryManager repositoryManager) {

        this.projectInfo = projectInfo;
        this.recordingInitializer = recordingInitializer;
        this.repositoryManager = repositoryManager;
    }

    @Override
    public void mergeAndDownloadSession(String sessionId) {
        RecordingSession session = findRecordingSession(sessionId);
        mergeAndDownloadSessionWithSelectedRecording(session, session.files());
    }

    @Override
    public void downloadSession(String sessionId) {
        RecordingSession session = findRecordingSession(sessionId);
        copySessionWithSelectedRecording(session, session.files());
    }

    @Override
    public void mergeAndDownloadSelectedRawRecordings(String sessionId, List<String> rawRecordingIds) {
        RecordingSession session = findRecordingSession(sessionId);
        List<RepositoryFile> repositoryFiles = session.files().stream()
                .filter(file -> rawRecordingIds.contains(file.id()))
                .toList();

        mergeAndDownloadSessionWithSelectedRecording(session, repositoryFiles);
    }

    @Override
    public void downloadSelectedRawRecordings(String sessionId, List<String> rawRecordingIds) {
        RecordingSession session = findRecordingSession(sessionId);
        List<RepositoryFile> repositoryFiles = session.files().stream()
                .filter(file -> rawRecordingIds.contains(file.id()))
                .toList();

        copySessionWithSelectedRecording(session, repositoryFiles);
    }

    private void copySessionWithSelectedRecording(RecordingSession session, List<RepositoryFile> repositoryFiles) {
        List<Path> recordingRepositoryFiles = repositoryFiles.stream()
                // Only include recording files can be uploaded (without additional files)
                .filter(RepositoryFile::isRecordingFile)
                .map(RepositoryFile::filePath)
                .toList();

        String folderName = session.name();
        recordingInitializer.newCopiedRecording(folderName, recordingRepositoryFiles);

        LOG.info("Copy Recordings: project_id={} folder_name={} recordings={}",
                projectInfo.id(), folderName, recordingRepositoryFiles);
    }

    private void mergeAndDownloadSessionWithSelectedRecording(
            RecordingSession session, List<RepositoryFile> repositoryFiles) {

        if (repositoryFiles.isEmpty()) {
            throw new IllegalArgumentException("No files selected to merge and upload for session: " + session.id());
        }

        if (LOG.isDebugEnabled()) {
            List<String> filesToMerge = repositoryFiles.stream()
                    .map(RepositoryFile::filePath)
                    .map(p -> p.getFileName().toString())
                    .toList();

            LOG.debug("Merging and uploading recording session: id={} name={} files={}",
                    session.id(), session.name(), filesToMerge);
        }

        String mergedFilename = session.recordingFileType()
                .appendExtension(session.name());

        List<Path> recordingFiles = repositoryFiles.stream()
                .filter(RepositoryFile::isRecordingFile)
                .map(RepositoryFile::filePath)
                .toList();

        List<RepositoryFile> additionalFiles = repositoryFiles.stream()
                .filter(RepositoryFile::isAdditionalFile)
                .toList();

        NewRecording newRecording = new NewRecording(session.name(), mergedFilename, null);
        try (NewRecordingHolder holder = recordingInitializer.newRecording(newRecording, additionalFiles)) {
            Recordings.mergeByFileCopy(recordingFiles, holder.outputPath());
        } catch (Exception e) {
            throw new RuntimeException("Cannot upload the recording: " + newRecording, e);
        }

        LOG.info("Merged and Uploaded recording: name={} folder_id={} project_id={}",
                newRecording.recordingName(), newRecording.folderId(), projectInfo.id());
    }

    private RecordingSession findRecordingSession(String sessionId) {
        return repositoryManager.findRecordingSessions(sessionId)
                .orElseThrow(() -> new RuntimeException("Recording session not found: " + sessionId));
    }
}
