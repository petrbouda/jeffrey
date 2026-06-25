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

package cafe.jeffrey.hub.core.grpc;

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.persistence.api.ProfilerRepository;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.shared.common.model.EffectiveProfilerSettings;
import cafe.jeffrey.shared.common.model.ProfilerInfo;

import java.util.List;

public class ProfilerSettingsGrpcService extends ProfilerSettingsServiceGrpc.ProfilerSettingsServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilerSettingsGrpcService.class);

    private final GrpcLookups lookups;
    private final ProfilerRepository profilerRepository;

    public ProfilerSettingsGrpcService(HubPlatformRepositories platformRepositories, GrpcLookups lookups) {
        this.lookups = lookups;
        this.profilerRepository = platformRepositories.newProfilerRepository();
    }

    @Override
    public void getSettings(GetProfilerSettingsRequest request, StreamObserver<GetProfilerSettingsResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ProjectManager project = lookups.projectManager(request.getProjectId());
            EffectiveProfilerSettings settings = project.profilerSettingsManager().fetchEffectiveSettings();

            LOG.debug("Fetched profiler settings via gRPC: projectId={}", request.getProjectId());

            return GetProfilerSettingsResponse.newBuilder()
                    .setAgentSettings(ProtoMappers.orEmpty(settings.agentSettings()))
                    .setLevel(ProtoMappers.settingsLevel(settings.level()))
                    .build();
        });
    }

    @Override
    public void upsertSettings(UpsertProfilerSettingsRequest request, StreamObserver<UpsertProfilerSettingsResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ProjectManager project = lookups.projectManager(request.getProjectId());
            project.profilerSettingsManager().upsertSettings(request.getAgentSettings());

            LOG.debug("Upserted profiler settings via gRPC: projectId={}", request.getProjectId());

            return UpsertProfilerSettingsResponse.getDefaultInstance();
        });
    }

    @Override
    public void deleteSettings(DeleteProfilerSettingsRequest request, StreamObserver<DeleteProfilerSettingsResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ProjectManager project = lookups.projectManager(request.getProjectId());
            project.profilerSettingsManager().deleteSettings();

            LOG.debug("Deleted profiler settings via gRPC: projectId={}", request.getProjectId());

            return DeleteProfilerSettingsResponse.getDefaultInstance();
        });
    }

    @Override
    public void listAllSettings(ListAllProfilerSettingsRequest request, StreamObserver<ListAllProfilerSettingsResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            List<ProfilerInfo> allSettings = profilerRepository.findAllSettings();

            ListAllProfilerSettingsResponse.Builder responseBuilder = ListAllProfilerSettingsResponse.newBuilder();
            for (ProfilerInfo info : allSettings) {
                responseBuilder.addSettings(ProfilerSettingsEntry.newBuilder()
                        .setWorkspaceId(ProtoMappers.orEmpty(info.workspaceId()))
                        .setProjectId(ProtoMappers.orEmpty(info.projectId()))
                        .setAgentSettings(ProtoMappers.orEmpty(info.agentSettings()))
                        .build());
            }

            LOG.debug("Listed all profiler settings via gRPC: count={}", allSettings.size());

            return responseBuilder.build();
        });
    }

    @Override
    public void upsertSettingsAtLevel(UpsertProfilerSettingsAtLevelRequest request, StreamObserver<UpsertProfilerSettingsAtLevelResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            String workspaceId = request.getWorkspaceId().isEmpty() ? null : request.getWorkspaceId();
            String projectId = request.getProjectId().isEmpty() ? null : request.getProjectId();

            if (projectId != null && workspaceId == null) {
                throw GrpcExceptions.invalidArgument("Workspace ID is required when Project ID is provided");
            }

            ProfilerInfo profilerInfo = new ProfilerInfo(workspaceId, projectId, request.getAgentSettings());
            profilerRepository.upsertSettings(profilerInfo);

            LOG.debug("Upserted profiler settings at level via gRPC: workspaceId={} projectId={}", workspaceId, projectId);

            return UpsertProfilerSettingsAtLevelResponse.getDefaultInstance();
        });
    }

    @Override
    public void getWorkspaceEffectiveSettings(
            GetWorkspaceEffectiveSettingsRequest request,
            StreamObserver<GetWorkspaceEffectiveSettingsResponse> responseObserver) {

        GrpcUnary.respond(responseObserver, () -> {
            String workspaceId = request.getWorkspaceId();
            if (workspaceId == null || workspaceId.isBlank()) {
                throw GrpcExceptions.invalidArgument("Workspace ID is required");
            }

            List<ProfilerInfo> all = profilerRepository.findWorkspaceSettings(workspaceId);

            String workspaceSettings = all.stream()
                    .filter(s -> workspaceId.equals(s.workspaceId()) && s.projectId() == null)
                    .map(ProfilerInfo::agentSettings)
                    .findFirst()
                    .orElse(null);

            String globalSettings = all.stream()
                    .filter(s -> s.workspaceId() == null && s.projectId() == null)
                    .map(ProfilerInfo::agentSettings)
                    .findFirst()
                    .orElse(null);

            GetWorkspaceEffectiveSettingsResponse.Builder builder =
                    GetWorkspaceEffectiveSettingsResponse.newBuilder();
            if (workspaceSettings != null) {
                builder.setWorkspaceAgentSettings(workspaceSettings);
            }
            if (globalSettings != null) {
                builder.setGlobalAgentSettings(globalSettings);
            }

            LOG.debug("Fetched workspace effective profiler settings via gRPC: workspaceId={} workspaceSet={} globalSet={}",
                    workspaceId, workspaceSettings != null, globalSettings != null);

            return builder.build();
        });
    }

    @Override
    public void deleteSettingsAtLevel(DeleteProfilerSettingsAtLevelRequest request, StreamObserver<DeleteProfilerSettingsAtLevelResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            String workspaceId = request.getWorkspaceId().isEmpty() ? null : request.getWorkspaceId();
            String projectId = request.getProjectId().isEmpty() ? null : request.getProjectId();

            if (projectId != null && workspaceId == null) {
                throw GrpcExceptions.invalidArgument("Workspace ID is required when Project ID is provided");
            }

            profilerRepository.deleteSettings(workspaceId, projectId);

            LOG.debug("Deleted profiler settings at level via gRPC: workspaceId={} projectId={}", workspaceId, projectId);

            return DeleteProfilerSettingsAtLevelResponse.getDefaultInstance();
        });
    }

}
