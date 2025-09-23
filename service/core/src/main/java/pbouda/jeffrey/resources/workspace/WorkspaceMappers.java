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

package pbouda.jeffrey.resources.workspace;

import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceLocation;
import pbouda.jeffrey.resources.response.WorkspaceResponse;
import pbouda.jeffrey.resources.util.InstantUtils;

import java.net.URI;

public abstract class WorkspaceMappers {

    public static WorkspaceResponse toResponse(WorkspaceInfo info) {
        return new WorkspaceResponse(
                info.id(),
                info.name(),
                info.description(),
                info.enabled(),
                InstantUtils.formatInstant(info.createdAt()),
                info.projectCount(),
                info.isMirrored());
    }

    public static WorkspaceResource.WorkspaceEventResponse toEventResponse(WorkspaceEvent event) {
        return new WorkspaceResource.WorkspaceEventResponse(
                event.eventId(),
                event.originEventId(),
                event.projectId(),
                event.repositoryId(),
                event.eventType(),
                event.content(),
                event.originCreatedAt().toEpochMilli(),
                event.createdAt().toEpochMilli()
        );
    }

    public static WorkspaceInfo toWorkspaceInfo(URI uri, String endpointPath, WorkspaceResponse response) {
        String relativePath = endpointPath.replace("{id}", response.id());
        return new WorkspaceInfo(
                response.id(),
                null,
                response.name(),
                response.description(),
                WorkspaceLocation.of(uri.resolve(relativePath)),
                response.enabled(),
                response.createdAt() != null ? InstantUtils.parseInstant(response.createdAt()) : null,
                response.isMirrored(),
                response.projectCount());
    }
}
