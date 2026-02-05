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

package pbouda.jeffrey.platform.resources.pub;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import pbouda.jeffrey.platform.resources.project.ProjectInstancesResource.InstanceResponse;
import pbouda.jeffrey.platform.resources.project.ProjectInstancesResource.InstanceSessionResponse;
import pbouda.jeffrey.shared.common.InstantUtils;
import pbouda.jeffrey.provider.platform.repository.ProjectInstanceRepository;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;

import java.util.List;

public class ProjectInstancesPublicResource {

    private final ProjectInstanceRepository projectInstanceRepository;

    public ProjectInstancesPublicResource(ProjectInstanceRepository projectInstanceRepository) {
        this.projectInstanceRepository = projectInstanceRepository;
    }

    @GET
    public List<InstanceResponse> list() {
        return projectInstanceRepository.findAll().stream()
                .map(ProjectInstancesPublicResource::toResponse)
                .toList();
    }

    @GET
    @Path("/{instanceId}")
    public InstanceResponse get(@PathParam("instanceId") String instanceId) {
        return projectInstanceRepository.find(instanceId)
                .map(ProjectInstancesPublicResource::toResponse)
                .orElseThrow(() -> new NotFoundException("Instance not found: " + instanceId));
    }

    @GET
    @Path("/{instanceId}/sessions")
    public List<InstanceSessionResponse> getSessions(@PathParam("instanceId") String instanceId) {
        return projectInstanceRepository.findSessions(instanceId).stream()
                .map(ProjectInstancesPublicResource::toSessionResponse)
                .toList();
    }

    private static InstanceResponse toResponse(ProjectInstanceInfo info) {
        return new InstanceResponse(
                info.id(),
                info.hostname(),
                info.status().name(),
                InstantUtils.toEpochMilli(info.startedAt()),
                InstantUtils.toEpochMilli(info.finishedAt()),
                info.sessionCount(),
                info.activeSessionId());
    }

    private static InstanceSessionResponse toSessionResponse(ProjectInstanceSessionInfo info) {
        return new InstanceSessionResponse(
                info.sessionId(),
                info.repositoryId(),
                InstantUtils.toEpochMilli(info.createdAt()),
                InstantUtils.toEpochMilli(info.finishedAt()),
                info.finishedAt() == null);
    }
}
