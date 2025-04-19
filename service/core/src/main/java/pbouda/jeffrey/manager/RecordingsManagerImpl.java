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
import pbouda.jeffrey.common.JfrFileUtils;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.provider.api.RecordingInitializer;
import pbouda.jeffrey.provider.api.model.recording.NewRecording;
import pbouda.jeffrey.provider.api.model.recording.RecordingFolder;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class RecordingsManagerImpl implements RecordingsManager {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingsManagerImpl.class);

    private final ProjectInfo projectInfo;
    private final ProjectDirs projectDirs;
    private final RecordingInitializer recordingInitializer;
    private final ProjectRecordingRepository projectRecordingRepository;

    public RecordingsManagerImpl(
            ProjectInfo projectInfo,
            ProjectDirs workingDirs,
            RecordingInitializer recordingInitializer,
            ProjectRecordingRepository projectRecordingRepository) {

        this.projectInfo = projectInfo;
        this.projectDirs = workingDirs;
        this.recordingInitializer = recordingInitializer;
        this.projectRecordingRepository = projectRecordingRepository;
    }

    @Override
    public List<Recording> all() {
        return projectRecordingRepository.findAllRecordings();
    }

    @Override
    public void upload(String filename, String folderId, InputStream stream) {
        recordingInitializer.newRecording(new NewRecording(filename, folderId, stream));
        LOG.info("Uploaded recording: name={} folder_id={} project_id={}",
                filename, folderId, projectInfo.id());
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
    public void mergeAndUpload(Path relativePath, List<Path> paths) {
        throw new UnsupportedOperationException("mergeAndUpload not implemented");
//        upload(relativePath, targetPath -> FileSystemUtils.concatFiles(targetPath, paths));
    }

    private void upload(Path relativePath, Function<Path, Path> uploader) {
        Path targetPath = projectDirs.recordingsDir().resolve(relativePath);
        FileSystemUtils.createDirectories(targetPath.getParent());
        Path uploaded = uploader.apply(targetPath);
        if (!JfrFileUtils.isJfrFileReadable(uploaded)) {
            LOG.warn("The uploaded file is not a valid JFR file: {}", uploaded);
            FileSystemUtils.delete(uploaded);
            throw new IllegalArgumentException("The uploaded file is not a valid JFR file: " + uploaded);
        }
    }

    @Override
    public void delete(String recordingId) {
        projectRecordingRepository.deleteRecordingWithFile(recordingId);
    }
}
