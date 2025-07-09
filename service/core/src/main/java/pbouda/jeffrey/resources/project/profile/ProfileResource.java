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

package pbouda.jeffrey.resources.project.profile;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.resources.project.profile.custom.ContainerOverviewResource;
import pbouda.jeffrey.resources.project.profile.custom.GCOverviewResource;
import pbouda.jeffrey.resources.project.profile.custom.HeapMemoryOverviewResource;
import pbouda.jeffrey.resources.project.profile.custom.HttpOverviewResource;
import pbouda.jeffrey.resources.project.profile.custom.JdbcPoolResource;
import pbouda.jeffrey.resources.project.profile.custom.JdbcStatementResource;

public class ProfileResource {

    public record UpdateProfile(String name) {
    }

    private final ProfileManager profileManager;

    public ProfileResource(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Path("/analysis")
    public AutoAnalysisResource autoAnalysisResource() {
        return new AutoAnalysisResource(profileManager.autoAnalysisManager());
    }

    @Path("/viewer")
    public EventViewerResource eventViewerResource() {
        return new EventViewerResource(profileManager.eventViewerManager());
    }

    @Path("/flamegraph")
    public FlamegraphResource flamegraphResource() {
        return new FlamegraphResource(
                profileManager.info(),
                profileManager.flamegraphManager());
    }

    @Path("/guardian")
    public GuardianResource guardianResource() {
        return new GuardianResource(profileManager.guardianManager());
    }

    @Path("/information")
    public ConfigurationResource configurationResource() {
        return new ConfigurationResource(profileManager.profileConfigurationManager());
    }

    @Path("/thread")
    public ThreadResource threadResource() {
        return new ThreadResource(profileManager.threadManager());
    }

    @Path("/compilation")
    public JITCompilationResource jitCompilationResource() {
        return new JITCompilationResource(profileManager.jitCompilationManager());
    }

    @Path("/subsecond")
    public SubSecondResource subSecondResource() {
        return new SubSecondResource(profileManager.subSecondManager());
    }

    @Path("/timeseries")
    public TimeseriesResource timeseriesResource() {
        return new TimeseriesResource(profileManager.timeseriesManager());
    }

    @Path("/perfcounters")
    public PerformanceCountersResource performanceCountersResource() {
        return new PerformanceCountersResource(profileManager.additionalFilesManager());
    }

    @Path("/jdbc/statement/overview")
    public JdbcStatementResource jdbcStatementResource() {
        return new JdbcStatementResource(profileManager.custom().jdbcStatementManager());
    }

    @Path("/jdbc/pool")
    public JdbcPoolResource jdbcPoolResource() {
        return new JdbcPoolResource(profileManager.custom().jdbcPoolManager());
    }

    @Path("/http/overview")
    public HttpOverviewResource httpOverviewResource() {
        return new HttpOverviewResource(profileManager.custom().httpManager());
    }

    @Path("/gc")
    public GCOverviewResource gcOverviewResource() {
        return new GCOverviewResource(profileManager.gcManager());
    }

    @Path("/container")
    public ContainerOverviewResource containerOverviewResource() {
        return new ContainerOverviewResource(profileManager.containerManager());
    }

    @Path("/heap-memory")
    public HeapMemoryOverviewResource heapMemoryOverviewResource() {
        return new HeapMemoryOverviewResource(profileManager.heapMemoryManager());
    }

    @GET
    public ProfileInfo getProfileInfo() {
        return profileManager.info();
    }

    @PUT
    public ProfileInfo updateProfile(UpdateProfile updateProfile) {
        return profileManager.updateName(updateProfile.name());
    }

    @DELETE
    public void deleteProfile() {
        profileManager.delete();
    }
}
