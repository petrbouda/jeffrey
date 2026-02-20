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

package pbouda.jeffrey.platform.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.manager.SchedulerManager;
import pbouda.jeffrey.platform.resources.request.CreateJobRequest;
import pbouda.jeffrey.platform.resources.request.UpdateEnabledRequest;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.shared.common.model.job.JobInfo;

import java.util.List;


public class SchedulerResource {

    private static final Logger LOG = LoggerFactory.getLogger(SchedulerResource.class);

    private final SchedulerManager schedulerManager;

    public SchedulerResource(SchedulerManager schedulerManager) {
        this.schedulerManager = schedulerManager;
    }

    @POST
    public Response createJob(CreateJobRequest request) {
        LOG.debug("Creating scheduler job: jobType={}", request.jobType());
        if (request.jobType() == null) {
            throw Exceptions.invalidRequest("Job type is required");
        }
        schedulerManager.create(request.jobType(), request.params());
        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    public List<JobInfo> allJobs() {
        LOG.debug("Listing scheduler jobs");
        return schedulerManager.all();
    }

    @PUT
    @Path("/{jobId}/enabled")
    public void updateEnabled(@PathParam("jobId") String jobId, UpdateEnabledRequest request) {
        LOG.debug("Updating job enabled state: jobId={} enabled={}", jobId, request.enabled());
        if (jobId == null || jobId.isBlank()) {
            throw Exceptions.invalidRequest("Job ID is required");
        }
        schedulerManager.updateEnabled(jobId, request.enabled());
    }

    @DELETE
    @Path("/{jobId}")
    public void delete(@PathParam("jobId") String jobId) {
        LOG.debug("Deleting scheduler job: jobId={}", jobId);
        if (jobId == null || jobId.isBlank()) {
            throw Exceptions.invalidRequest("Job ID is required");
        }
        schedulerManager.delete(jobId);
    }
}
