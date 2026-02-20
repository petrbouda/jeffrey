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

import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.platform.manager.SchedulerManager;
import pbouda.jeffrey.shared.common.exception.ErrorResponse;
import pbouda.jeffrey.shared.common.exception.ErrorType;
import pbouda.jeffrey.shared.common.model.job.JobInfo;
import pbouda.jeffrey.shared.common.model.job.JobType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SchedulerResourceTest extends AbstractResourceTest {

    private SchedulerManager schedulerManager;

    @Path("/scheduler")
    public static class TestRoot {
        private final SchedulerManager schedulerManager;

        public TestRoot(SchedulerManager schedulerManager) {
            this.schedulerManager = schedulerManager;
        }

        @Path("/")
        public SchedulerResource schedulerResource() {
            return new SchedulerResource(schedulerManager);
        }
    }

    @Override
    protected void configureResources(ResourceConfig config) {
        schedulerManager = mock(SchedulerManager.class);
        config.register(new TestRoot(schedulerManager));
    }

    @Nested
    class CreateJob {

        @Test
        void returns201_whenJobCreatedSuccessfully() {
            Response response = target("/scheduler")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(Map.of(
                            "jobType", "WORKSPACE_EVENTS_REPLICATOR",
                            "params", Map.of())));

            assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
            verify(schedulerManager).create(eq(JobType.WORKSPACE_EVENTS_REPLICATOR), any());
        }

        @Test
        void returns400_whenJobTypeIsNull() {
            Response response = target("/scheduler")
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(Map.of("params", Map.of())));

            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

            ErrorResponse error = response.readEntity(ErrorResponse.class);
            assertEquals(ErrorType.CLIENT, error.type());
            assertEquals("INVALID_REQUEST", error.code().name());
            verify(schedulerManager, never()).create(any(), any());
        }
    }

    @Nested
    class AllJobs {

        @Test
        void returnsJobsList() {
            JobInfo job = new JobInfo("job-1", null, JobType.WORKSPACE_EVENTS_REPLICATOR, Map.of(), true);
            when(schedulerManager.all()).thenReturn(List.of(job));

            List<JobInfo> result = target("/scheduler")
                    .request(MediaType.APPLICATION_JSON)
                    .get(new GenericType<>() {});

            assertEquals(1, result.size());
            assertEquals("job-1", result.getFirst().id());
            assertEquals(JobType.WORKSPACE_EVENTS_REPLICATOR, result.getFirst().jobType());
        }

        @Test
        void returnsEmptyList_whenNoJobs() {
            when(schedulerManager.all()).thenReturn(List.of());

            List<JobInfo> result = target("/scheduler")
                    .request(MediaType.APPLICATION_JSON)
                    .get(new GenericType<>() {});

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class DeleteJob {

        @Test
        void returns204_whenJobDeleted() {
            Response response = target("/scheduler/job-1")
                    .request()
                    .delete();

            assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
            verify(schedulerManager).delete("job-1");
        }
    }
}
