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

package pbouda.jeffrey.profile.resources;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.profile.ai.mcp.service.JfrAnalysisAssistantService;
import pbouda.jeffrey.profile.ai.service.HeapDumpContextExtractor;
import pbouda.jeffrey.profile.ai.service.OqlAssistantService;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.resources.custom.*;
import pbouda.jeffrey.shared.common.model.ProfileInfo;

public class ProfileResource {

    public record UpdateProfile(String name) {
    }

    private final ProfileManager profileManager;
    private final OqlAssistantService oqlAssistantService;
    private final JfrAnalysisAssistantService jfrAnalysisAssistantService;
    private final HeapDumpContextExtractor heapDumpContextExtractor;

    public ProfileResource(
            ProfileManager profileManager,
            OqlAssistantService oqlAssistantService,
            JfrAnalysisAssistantService jfrAnalysisAssistantService,
            HeapDumpContextExtractor heapDumpContextExtractor) {
        this.profileManager = profileManager;
        this.oqlAssistantService = oqlAssistantService;
        this.jfrAnalysisAssistantService = jfrAnalysisAssistantService;
        this.heapDumpContextExtractor = heapDumpContextExtractor;
    }

    @Path("/analysis")
    public AutoAnalysisResource autoAnalysisResource() {
        return new AutoAnalysisResource(profileManager.autoAnalysisManager());
    }

    @Path("/viewer")
    public EventViewerResource eventViewerResource() {
        return new EventViewerResource(profileManager.eventViewerManager());
    }

    @Path("/flags")
    public FlagsResource flagsResource() {
        return new FlagsResource(profileManager.flagsManager());
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
        return new SubSecondResource(profileManager.info(), profileManager.subSecondManager());
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

    @Path("/method-tracing")
    public MethodTracingResource methodTracingResource() {
        return new MethodTracingResource(profileManager.custom().methodTracingManager());
    }

    @Path("/gc")
    public GarbageCollectionResource gcOverviewResource() {
        return new GarbageCollectionResource(profileManager.gcManager());
    }

    @Path("/container")
    public ContainerOverviewResource containerOverviewResource() {
        return new ContainerOverviewResource(profileManager.containerManager());
    }

    @Path("/heap-memory")
    public HeapMemoryResource heapMemoryOverviewResource() {
        return new HeapMemoryResource(profileManager.heapMemoryManager());
    }

    @Path("/heap")
    public HeapDumpResource heapDumpResource() {
        return new HeapDumpResource(
                profileManager.heapDumpManager(),
                oqlAssistantService,
                heapDumpContextExtractor);
    }

    @Path("/features")
    public ProfileFeaturesResource featuresResource() {
        return new ProfileFeaturesResource(profileManager.featuresManager());
    }

    @Path("/ai-analysis")
    public AiAnalysisResource aiAnalysisResource() {
        return new AiAnalysisResource(
                profileManager.info(),
                jfrAnalysisAssistantService);
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
