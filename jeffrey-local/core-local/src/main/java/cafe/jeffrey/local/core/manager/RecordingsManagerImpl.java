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

package cafe.jeffrey.local.core.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.local.core.recording.ProjectRecordingInitializer;
import cafe.jeffrey.local.persistence.api.RecordingGroup;
import cafe.jeffrey.local.persistence.api.RecordingRepository;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class RecordingsManagerImpl implements RecordingsManager {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingsManagerImpl.class);

    private final ProjectInfo projectInfo;
    private final ProjectRecordingInitializer recordingInitializer;
    private final RecordingRepository recordingRepository;

    public RecordingsManagerImpl(
            ProjectInfo projectInfo,
            ProjectRecordingInitializer recordingInitializer,
            RecordingRepository recordingRepository) {

        this.projectInfo = projectInfo;
        this.recordingInitializer = recordingInitializer;
        this.recordingRepository = recordingRepository;
    }

    @Override
    public List<Recording> all() {
        return recordingRepository.findAllRecordings();
    }

    @Override
    public void createGroup(String groupName) {
        LOG.debug("Creating recording group: groupName={} projectId={}", groupName, projectInfo.id());
        recordingRepository.insertGroup(groupName);
    }

    @Override
    public List<RecordingGroup> allRecordingGroups() {
        return recordingRepository.findAllRecordingGroups();
    }

    @Override
    public void deleteGroup(String groupId) {
        recordingRepository.deleteGroup(groupId);
    }

    @Override
    public void delete(String recordingId) {
        LOG.debug("Deleting recording: recordingId={} projectId={}", recordingId, projectInfo.id());
        recordingRepository.deleteRecordingWithFiles(recordingId);
    }

    @Override
    public void moveRecordingToGroup(String recordingId, String groupId) {
        LOG.debug("Moving recording to group: recordingId={} groupId={} projectId={}", recordingId, groupId, projectInfo.id());
        recordingRepository.updateRecordingGroup(recordingId, groupId);
    }

    @Override
    public Optional<Path> findRecordingFile(String recordingId, String fileId) {
        Optional<Recording> recordingOpt = recordingRepository.findRecording(recordingId);
        if (recordingOpt.isEmpty()) {
            return Optional.empty();
        }

        Recording recording = recordingOpt.get();

        Optional<String> filenameOpt = recording.files().stream()
                .filter(file -> file.id().equals(fileId))
                .map(RecordingFile::filename)
                .findFirst();

        if (filenameOpt.isEmpty()) {
            return Optional.empty();
        }

        String filename = filenameOpt.get();

        List<Path> allFiles = recordingInitializer.recordingStorage().findAllFiles(recordingId);
        return allFiles.stream()
                .filter(path -> path.getFileName().toString().equals(filename))
                .findFirst();
    }
}
