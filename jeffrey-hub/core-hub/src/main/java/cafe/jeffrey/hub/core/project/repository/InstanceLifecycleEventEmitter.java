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

package cafe.jeffrey.hub.core.project.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventConverter;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventPublisher;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Announces instance terminal transitions as workspace events.
 *
 * <p>These are audit-only events with no consumer, following the precedent set by
 * {@code PROJECT_INSTANCE_SESSION_FINISHED}: the transition is written to the database by the
 * caller and announced afterwards, rather than being event-sourced.
 *
 * <p><b>Known de-duplication behaviour.</b> The event's {@code originEventId} is the instance id,
 * so the queue's dedup key collapses repeats — each instance announces FINISHED and EXPIRED at
 * most once ever. {@code ProjectInstanceStatus} does permit {@code EXPIRED -> ACTIVE}
 * (a reactivated instance that starts streaming again), so a reactivated instance's second
 * terminal transition is silently swallowed. That is accepted: the event reads as "this instance
 * reached a terminal state", and reactivation is rare. Making it exact would mean composing the
 * transition timestamp into {@code originEventId}, which costs the clean instance-id correlation
 * key and adds churn rows for flapping instances.
 */
public class InstanceLifecycleEventEmitter {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceLifecycleEventEmitter.class);

    private final Clock clock;
    private final WorkspaceEventPublisher workspaceEventPublisher;

    public InstanceLifecycleEventEmitter(Clock clock, WorkspaceEventPublisher workspaceEventPublisher) {
        this.clock = clock;
        this.workspaceEventPublisher = workspaceEventPublisher;
    }

    public void emitInstanceFinished(
            ProjectInfo projectInfo,
            String instanceId,
            Instant finishedAt,
            WorkspaceEventCreator creator) {

        WorkspaceEvent event = WorkspaceEventConverter.instanceFinished(
                clock.instant(), projectInfo, instanceId, finishedAt, creator);

        workspaceEventPublisher.publishBatch(projectInfo.workspaceId(), List.of(event));
        LOG.debug("Emitted instance finished event: project_id={} instance_id={} creator={}",
                projectInfo.id(), instanceId, creator);
    }

    public void emitInstanceExpired(
            ProjectInfo projectInfo,
            String instanceId,
            Instant expiredAt,
            WorkspaceEventCreator creator) {

        WorkspaceEvent event = WorkspaceEventConverter.instanceExpired(
                clock.instant(), projectInfo, instanceId, expiredAt, creator);

        workspaceEventPublisher.publishBatch(projectInfo.workspaceId(), List.of(event));
        LOG.debug("Emitted instance expired event: project_id={} instance_id={} creator={}",
                projectInfo.id(), instanceId, creator);
    }
}
