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
import pbouda.jeffrey.platform.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.workspace.WorkspaceEventConverter;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.workspace.RepositorySessionInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

public class SessionFinishEventEmitter {

    private static final Logger LOG = LoggerFactory.getLogger(SessionFinishEventEmitter.class);

    private final Clock clock;
    private final CompositeWorkspacesManager compositeWorkspacesManager;

    public SessionFinishEventEmitter(Clock clock, CompositeWorkspacesManager compositeWorkspacesManager) {
        this.clock = clock;
        this.compositeWorkspacesManager = compositeWorkspacesManager;
    }

    public void emitSessionFinished(ProjectInfo projectInfo, RepositorySessionInfo sessionInfo) {
        Optional<WorkspaceManager> workspaceOpt =
                compositeWorkspacesManager.findById(projectInfo.workspaceId());

        if (workspaceOpt.isEmpty()) {
            LOG.warn("Cannot emit session finished event, workspace not found: workspaceId={}",
                    projectInfo.workspaceId());
            return;
        }

        WorkspaceEvent event = WorkspaceEventConverter.sessionFinished(
                clock.instant(),
                projectInfo,
                sessionInfo,
                WorkspaceEventCreator.SESSION_FINISHED_DETECTOR_JOB);

        workspaceOpt.get().workspaceEventManager().batchInsertEvents(List.of(event));
    }
}
