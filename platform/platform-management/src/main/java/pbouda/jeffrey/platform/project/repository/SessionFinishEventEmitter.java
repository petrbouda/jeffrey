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
import pbouda.jeffrey.platform.queue.PersistentQueue;
import pbouda.jeffrey.platform.workspace.WorkspaceEventConverter;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.time.Clock;
import java.util.List;

public class SessionFinishEventEmitter {

    private static final Logger LOG = LoggerFactory.getLogger(SessionFinishEventEmitter.class);

    private final Clock clock;
    private final PersistentQueue<WorkspaceEvent> workspaceEventQueue;

    public SessionFinishEventEmitter(Clock clock, PersistentQueue<WorkspaceEvent> workspaceEventQueue) {
        this.clock = clock;
        this.workspaceEventQueue = workspaceEventQueue;
    }

    public void emitInstanceFinished(ProjectInfo projectInfo, String instanceId) {
        WorkspaceEvent event = WorkspaceEventConverter.instanceFinished(
                clock.instant(),
                projectInfo,
                instanceId,
                WorkspaceEventCreator.SESSION_FINISHED_DETECTOR_JOB);

        workspaceEventQueue.appendBatch(projectInfo.workspaceId(), List.of(event));
        LOG.debug("Emitted instance finished event: project_id={} instance_id={}", projectInfo.id(), instanceId);
    }

    public void emitSessionFinished(ProjectInfo projectInfo, ProjectInstanceSessionInfo sessionInfo) {
        WorkspaceEvent event = WorkspaceEventConverter.sessionFinished(
                clock.instant(),
                projectInfo,
                sessionInfo,
                WorkspaceEventCreator.SESSION_FINISHED_DETECTOR_JOB);

        workspaceEventQueue.appendBatch(projectInfo.workspaceId(), List.of(event));
        LOG.debug("Emitted session finished event: project_id={} session_id={}", projectInfo.id(), sessionInfo.sessionId());
    }
}
