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

package pbouda.jeffrey.local.core.resources.project;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.resources.response.InstanceDetailResponse;
import pbouda.jeffrey.local.core.resources.response.InstanceResponse;
import pbouda.jeffrey.local.core.resources.response.InstanceSessionResponse;
import pbouda.jeffrey.local.core.manager.project.RemoteInstancesManager;
import pbouda.jeffrey.shared.common.model.ProjectInfo;

import java.time.Clock;
import java.util.List;

public class ProjectInstancesResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectInstancesResource.class);

    private final ProjectInfo projectInfo;
    private final RemoteInstancesManager projectInstanceRepository;
    private final Clock clock;

    public ProjectInstancesResource(ProjectInfo projectInfo, RemoteInstancesManager projectInstanceRepository, Clock clock) {
        this.projectInfo = projectInfo;
        this.projectInstanceRepository = projectInstanceRepository;
        this.clock = clock;
    }

    @GET
    public List<InstanceResponse> list(
            @QueryParam("includeSessions") @DefaultValue("false") boolean includeSessions) {
        var result = projectInstanceRepository.findAll(includeSessions).stream()
                .map(i -> InstanceResponse.from(i, clock))
                .toList();
        LOG.debug("Listed project instances: projectId={} count={} include_sessions={}",
                projectInfo.id(), result.size(), includeSessions);
        return result;
    }

    @GET
    @Path("/{instanceId}")
    public InstanceResponse get(@PathParam("instanceId") String instanceId) {
        LOG.debug("Fetching project instance: instanceId={}", instanceId);
        return projectInstanceRepository.find(instanceId)
                .map(i -> InstanceResponse.from(i, clock))
                .orElseThrow(() -> new NotFoundException("Instance not found: " + instanceId));
    }

    @GET
    @Path("/{instanceId}/detail")
    public InstanceDetailResponse getDetail(@PathParam("instanceId") String instanceId) {
        LOG.debug("Fetching instance detail: instanceId={}", instanceId);
        return projectInstanceRepository.detail(instanceId)
                .orElseThrow(() -> new NotFoundException("Instance not found: " + instanceId));
    }

    @GET
    @Path("/{instanceId}/sessions")
    public List<InstanceSessionResponse> getSessions(@PathParam("instanceId") String instanceId) {
        var result = projectInstanceRepository.findSessions(instanceId).stream()
                .map(s -> InstanceSessionResponse.from(s, clock))
                .toList();
        LOG.debug("Listed instance sessions: projectId={} instanceId={} count={}", projectInfo.id(), instanceId, result.size());
        return result;
    }
}
