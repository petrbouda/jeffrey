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
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.provider.api.NewRecordingHolder;
import pbouda.jeffrey.provider.api.model.recording.NewRecording;
import pbouda.jeffrey.provider.api.model.recording.RecordingFolder;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;
import pbouda.jeffrey.recording.ProjectRecordingInitializer;

import java.io.InputStream;
import java.util.List;

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
        projectRecordingRepository.insertFolder(folderName);
    }

    @Override
    public List<RecordingFolder> allRecordingFolders() {
        return projectRecordingRepository.findAllRecordingFolders();
    }

    @Override
    public void deleteFolder(String folderId) {
        // TODO: Remove all files as well
        projectRecordingRepository.deleteFolder(folderId);
    }

    @Override
    public void delete(String recordingId) {
        // TODO: Remove files as well
        projectRecordingRepository.deleteRecordingWithFiles(recordingId);
    }
}
