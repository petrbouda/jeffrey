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

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.manager.SchedulerManager;

import static pbouda.jeffrey.configuration.AppConfiguration.GLOBAL_SCHEDULER_MANAGER_BEAN;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RootResource {

    private final ProjectsManager projectsManager;
    private final SchedulerManager globalSchedulerManager;

    @Inject
    public RootResource(
            ProjectsManager projectsManager,
            @Qualifier(GLOBAL_SCHEDULER_MANAGER_BEAN) SchedulerManager globalSchedulerManager) {

        this.projectsManager = projectsManager;
        this.globalSchedulerManager = globalSchedulerManager;
    }

    @Path("/projects")
    public ProjectsResource projectsResource() {
        return new ProjectsResource(projectsManager);
    }

    @Path("/scheduler")
    public SchedulerResource schedulerResource() {
        return new SchedulerResource(globalSchedulerManager);
    }
}
