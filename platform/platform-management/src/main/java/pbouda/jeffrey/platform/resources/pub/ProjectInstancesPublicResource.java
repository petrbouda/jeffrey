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

package pbouda.jeffrey.platform.resources.pub;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import pbouda.jeffrey.platform.resources.response.InstanceResponse;
import pbouda.jeffrey.platform.resources.response.InstanceSessionResponse;
import pbouda.jeffrey.provider.platform.repository.ProjectInstanceRepository;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;

public class ProjectInstancesPublicResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectInstancesPublicResource.class);

    private final ProjectInfo projectInfo;
    private final ProjectInstanceRepository projectInstanceRepository;
    private final Clock clock;

    public ProjectInstancesPublicResource(ProjectInfo projectInfo, ProjectInstanceRepository projectInstanceRepository, Clock clock) {
        this.projectInfo = projectInfo;
        this.projectInstanceRepository = projectInstanceRepository;
        this.clock = clock;
    }

    @GET
    public List<InstanceResponse> list() {
        var result = projectInstanceRepository.findAll().stream()
                .map(i -> InstanceResponse.from(i, clock))
                .toList();
        LOG.debug("Listed public project instances: projectId={} count={}", projectInfo.id(), result.size());
        return result;
    }

    @GET
    @Path("/{instanceId}")
    public InstanceResponse get(@PathParam("instanceId") String instanceId) {
        LOG.debug("Fetching public project instance: instanceId={}", instanceId);
        return projectInstanceRepository.find(instanceId)
                .map(i -> InstanceResponse.from(i, clock))
                .orElseThrow(() -> new NotFoundException("Instance not found: " + instanceId));
    }

    @GET
    @Path("/{instanceId}/sessions")
    public List<InstanceSessionResponse> getSessions(@PathParam("instanceId") String instanceId) {
        var result = projectInstanceRepository.findSessions(instanceId).stream()
                .map(s -> InstanceSessionResponse.from(s, clock))
                .toList();
        LOG.debug("Listed public instance sessions: projectId={} instanceId={} count={}", projectInfo.id(), instanceId, result.size());
        return result;
    }
}
