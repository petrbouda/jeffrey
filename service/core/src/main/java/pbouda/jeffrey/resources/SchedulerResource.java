/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import pbouda.jeffrey.manager.SchedulerManager;
import pbouda.jeffrey.common.model.job.JobInfo;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;

import java.util.List;
import java.util.Map;


public class SchedulerResource {

    public record CreateJobRequest(JobType jobType, Map<String, String> params) {
    }

    public record UpdateEnabledRequest(boolean enabled) {
    }

    private final SchedulerManager schedulerManager;

    public SchedulerResource(SchedulerManager schedulerManager) {
        this.schedulerManager = schedulerManager;
    }

    @POST
    public Response createJob(CreateJobRequest request) {
        schedulerManager.create(request.jobType(), request.params());
        return Response.ok().build();
    }

    @GET
    public List<JobInfo> allJobs() {
        return schedulerManager.all();
    }

    @PUT
    @Path("/{jobId}/enabled")
    public void updateEnabled(@PathParam("jobId") String jobId, UpdateEnabledRequest request) {
        schedulerManager.updateEnabled(jobId, request.enabled);
    }

    @DELETE
    @Path("/{jobId}")
    public void delete(@PathParam("jobId") String jobId) {
        schedulerManager.delete(jobId);
    }
}
