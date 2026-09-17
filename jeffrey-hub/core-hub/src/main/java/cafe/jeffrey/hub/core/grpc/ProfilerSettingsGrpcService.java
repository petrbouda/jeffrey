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
import cafe.jeffrey.hub.model.EffectiveProfilerSettings;
import cafe.jeffrey.hub.model.ProfilerInfo;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.core.EffectiveSettingsResolver;

import java.util.List;

public class ProfilerSettingsGrpcService extends ProfilerSettingsServiceGrpc.ProfilerSettingsServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilerSettingsGrpcService.class);

    private final GrpcLookups lookups;
    private final ProfilerRepository profilerRepository;

    public ProfilerSettingsGrpcService(ProfilerRepository profilerRepository, GrpcLookups lookups) {
        this.profilerRepository = profilerRepository;
        this.lookups = lookups;
    }

    @Override
    public void getSettings(GetProfilerSettingsRequest request, StreamObserver<GetProfilerSettingsResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ProjectInfo project = lookups.projectInfo(request.getProjectId());
            EffectiveProfilerSettings settings = EffectiveSettingsResolver.resolve(
                    profilerRepository.fetchProfilerSettings(project.workspaceId(), project.id()));

            LOG.debug("Fetched profiler settings via gRPC: project_id={}", request.getProjectId());

            return GetProfilerSettingsResponse.newBuilder()
                    .setAgentSettings(ProtoMappers.orEmpty(settings.agentSettings()))
                    .setLevel(ProtoMappers.settingsLevel(settings.level()))
                    .build();
        });
    }

    @Override
    public void upsertSettings(UpsertProfilerSettingsRequest request, StreamObserver<UpsertProfilerSettingsResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ProjectInfo project = lookups.projectInfo(request.getProjectId());
            profilerRepository.upsertSettings(new ProfilerInfo(project.workspaceId(), project.id(), request.getAgentSettings()));

            LOG.debug("Upserted profiler settings via gRPC: project_id={}", request.getProjectId());

            return UpsertProfilerSettingsResponse.getDefaultInstance();
        });
    }

    @Override
    public void deleteSettings(DeleteProfilerSettingsRequest request, StreamObserver<DeleteProfilerSettingsResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ProjectInfo project = lookups.projectInfo(request.getProjectId());
            profilerRepository.deleteSettings(project.workspaceId(), project.id());

            LOG.debug("Deleted profiler settings via gRPC: project_id={}", request.getProjectId());

            return DeleteProfilerSettingsResponse.getDefaultInstance();
        });
    }

    @Override
    public void upsertSettingsAtLevel(UpsertProfilerSettingsAtLevelRequest request, StreamObserver<UpsertProfilerSettingsAtLevelResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            SettingsScope scope = SettingsScope.of(request.getWorkspaceId(), request.getProjectId());
            lookups.requireExists(scope);

            profilerRepository.upsertSettings(
                    new ProfilerInfo(scope.workspaceId(), scope.projectId(), request.getAgentSettings()));

            LOG.debug("Upserted profiler settings at level via gRPC: workspace_id={} project_id={}",
                    scope.workspaceId(), scope.projectId());

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

            LOG.debug("Fetched workspace effective profiler settings via gRPC: workspace_id={} workspace_set={} global_set={}",
                    workspaceId, workspaceSettings != null, globalSettings != null);

            return builder.build();
        });
    }

    @Override
    public void deleteSettingsAtLevel(DeleteProfilerSettingsAtLevelRequest request, StreamObserver<DeleteProfilerSettingsAtLevelResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            SettingsScope scope = SettingsScope.of(request.getWorkspaceId(), request.getProjectId());
            lookups.requireExists(scope);

            profilerRepository.deleteSettings(scope.workspaceId(), scope.projectId());

            LOG.debug("Deleted profiler settings at level via gRPC: workspace_id={} project_id={}",
                    scope.workspaceId(), scope.projectId());

            return DeleteProfilerSettingsAtLevelResponse.getDefaultInstance();
        });
    }

}
