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

package pbouda.jeffrey.platform.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.Recording;
import pbouda.jeffrey.provider.platform.NewRecordingHolder;
import pbouda.jeffrey.provider.platform.model.NewRecording;
import pbouda.jeffrey.provider.platform.model.RecordingFolder;
import pbouda.jeffrey.provider.platform.repository.ProjectRecordingRepository;
import pbouda.jeffrey.platform.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.shared.common.model.RecordingFile;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class RecordingsManagerImpl implements RecordingsManager {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingsManagerImpl.class);

    private final ProjectInfo projectInfo;
    private final ProjectRecordingInitializer recordingInitializer;
    private final ProjectRecordingRepository projectRecordingRepository;

    public RecordingsManagerImpl(
            ProjectInfo projectInfo,
            ProjectRecordingInitializer recordingInitializer,
            ProjectRecordingRepository projectRecordingRepository) {

        this.projectInfo = projectInfo;
        this.recordingInitializer = recordingInitializer;
        this.projectRecordingRepository = projectRecordingRepository;
    }

    @Override
    public List<Recording> all() {
        return projectRecordingRepository.findAllRecordings();
    }

    @Override
    public void upload(NewRecording newRecording, InputStream stream) {
        LOG.debug("Uploading recording: name={} folderId={} projectId={}", newRecording.recordingName(), newRecording.folderId(), projectInfo.id());
        try (NewRecordingHolder holder = recordingInitializer.newRecording(newRecording)) {
            holder.transferFrom(stream);
        } catch (Exception e) {
            throw new RuntimeException("Cannot upload the recording: " + newRecording, e);
        }

        LOG.info("Uploaded recording: name={} folder_id={} project_id={}",
                newRecording.recordingName(), newRecording.folderId(), projectInfo.id());
    }

    @Override
    public void createFolder(String folderName) {
        LOG.debug("Creating recording folder: folderName={} projectId={}", folderName, projectInfo.id());
        projectRecordingRepository.insertFolder(folderName);
    }

    @Override
    public List<RecordingFolder> allRecordingFolders() {
        return projectRecordingRepository.findAllRecordingFolders();
    }

    @Override
    public void deleteFolder(String folderId) {
        // TODO: Remove all files as well
        //  Scheduler removes files asynchronously?
        projectRecordingRepository.deleteFolder(folderId);
    }

    @Override
    public void delete(String recordingId) {
        LOG.debug("Deleting recording: recordingId={} projectId={}", recordingId, projectInfo.id());
        // TODO: Remove files as well
        //  Scheduler removes files asynchronously?
        projectRecordingRepository.deleteRecordingWithFiles(recordingId);
    }

    @Override
    public Optional<Path> findRecordingFile(String recordingId, String fileId) {
        // First, find the recording to get the file metadata
        Optional<Recording> recordingOpt = projectRecordingRepository.findRecording(recordingId);
        if (recordingOpt.isEmpty()) {
            return Optional.empty();
        }

        Recording recording = recordingOpt.get();

        // Find the file metadata by ID to get the filename
        Optional<String> filenameOpt = recording.files().stream()
                .filter(file -> file.id().equals(fileId))
                .map(RecordingFile::filename)
                .findFirst();

        if (filenameOpt.isEmpty()) {
            return Optional.empty();
        }

        String filename = filenameOpt.get();

        // Find the actual file in storage by filename
        List<Path> allFiles = recordingInitializer.recordingStorage().findAllFiles(recordingId);
        return allFiles.stream()
                .filter(path -> path.getFileName().toString().equals(filename))
                .findFirst();
    }
}
