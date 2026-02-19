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

package pbouda.jeffrey.platform.project.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.jfr.JfrMessageEmitter;
import pbouda.jeffrey.platform.queue.PersistentQueue;
import pbouda.jeffrey.platform.workspace.WorkspaceEventConverter;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.repository.RepositoryFile;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

public class RecordingFileEventEmitter {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingFileEventEmitter.class);

    private final Clock clock;
    private final PersistentQueue<WorkspaceEvent> workspaceEventQueue;

    public RecordingFileEventEmitter(Clock clock, PersistentQueue<WorkspaceEvent> workspaceEventQueue) {
        this.clock = clock;
        this.workspaceEventQueue = workspaceEventQueue;
    }

    public void emitRecordingFileCreated(
            ProjectInfo projectInfo,
            String sessionId,
            RepositoryFile originalFile,
            long originalSize,
            long compressedSize,
            Path compressedPath) {

        WorkspaceEvent event = WorkspaceEventConverter.recordingFileCreated(
                clock.instant(),
                projectInfo,
                sessionId,
                originalFile,
                compressedPath,
                originalSize,
                compressedSize,
                WorkspaceEventCreator.REPOSITORY_STORAGE);

        workspaceEventQueue.appendBatch(projectInfo.workspaceId(), List.of(event));
        JfrMessageEmitter.recordingFileCreated(projectInfo.id(), sessionId, originalSize, compressedSize);
        LOG.debug("Emitted recording file created event: project_id={} session_id={}", projectInfo.id(), sessionId);
    }
}
