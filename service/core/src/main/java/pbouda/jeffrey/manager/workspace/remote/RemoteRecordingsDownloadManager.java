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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.repository.RepositoryFile;
import pbouda.jeffrey.manager.RecordingsDownloadManager;
import pbouda.jeffrey.provider.api.model.recording.NewRecording;
import pbouda.jeffrey.recording.ProjectRecordingInitializer;

import java.util.List;

public class RemoteRecordingsDownloadManager implements RecordingsDownloadManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteRecordingsDownloadManager.class);

    private static final String UNSUPPORTED =
            "Not supported operation in " + RemoteRecordingsDownloadManager.class.getSimpleName();

    private final JeffreyDirs jeffreyDirs;
    private final ProjectInfo projectInfo;
    private final RemoteWorkspaceClient remoteWorkspaceClient;
    private final RecordingsDownloadManager commonDownloadManager;

    public RemoteRecordingsDownloadManager(
            JeffreyDirs jeffreyDirs,
            ProjectInfo projectInfo,
            RemoteWorkspaceClient remoteWorkspaceClient,
            RecordingsDownloadManager commonDownloadManager) {

        this.jeffreyDirs = jeffreyDirs;
        this.projectInfo = projectInfo;
        this.remoteWorkspaceClient = remoteWorkspaceClient;
        this.commonDownloadManager = commonDownloadManager;
    }

    @Override
    public void mergeAndDownloadSession(String recordingSessionId) {
        remoteWorkspaceClient.recordingSessions(projectInfo.id(), recordingSessionId);

//        try (NewRecordingHolder holder = recordingInitializer.newRecording(newRecording);
//            var inputStream = remoteWorkspaceClient.downloadSession(projectInfo.id(), recordingSessionId)) {
//            // Transfer the remote recording to the local recording file
//            holder.transferFrom(inputStream);
//        } catch (Exception e) {
//            throw new RuntimeException("Cannot upload the recording: " + newRecording, e);
//        }

//        LOG.info("Downloaded and initialized recording: name={} project_id={}",
//                newRecording.recordingName(), projectInfo.id());
    }

    @Override
    public void mergeAndDownloadSelectedRawRecordings(String recordingSessionId, List<String> rawRecordingIds) {
        throw new UnsupportedOperationException(UNSUPPORTED);
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
