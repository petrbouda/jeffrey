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

package pbouda.jeffrey.platform.workspace.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.jfr.JfrMessageEmitter;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.platform.workspace.model.RecordingFileCreatedEventContent;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;

public class RecordingFileCreatedWorkspaceEventConsumer implements WorkspaceEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingFileCreatedWorkspaceEventConsumer.class);

    @Override
    public void on(WorkspaceEvent event, ProjectsSynchronizerJobDescriptor jobDescriptor) {
        RecordingFileCreatedEventContent content =
                Json.read(event.content(), RecordingFileCreatedEventContent.class);

        LOG.debug("New recording file created: projectId={} originEventId={} fileName={} originalSize={} compressedSize={}",
                event.projectId(), event.originEventId(), content.fileName(),
                content.originalSize(), content.compressedSize());

        JfrMessageEmitter.recordingFileCreated(
                event.projectId(),
                event.originEventId(),
                content.originalSize(),
                content.compressedSize());
    }

    @Override
    public boolean isApplicable(WorkspaceEvent event) {
        return event.eventType() == WorkspaceEventType.RECORDING_FILE_CREATED;
    }
}
