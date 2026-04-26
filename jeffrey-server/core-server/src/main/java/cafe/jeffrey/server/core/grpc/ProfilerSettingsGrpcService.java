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

package cafe.jeffrey.server.core.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.server.api.v1.*;
import cafe.jeffrey.server.persistence.repository.ProfilerRepository;
import cafe.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import cafe.jeffrey.server.core.manager.project.ProjectManager;
import cafe.jeffrey.shared.common.model.EffectiveProfilerSettings;
import cafe.jeffrey.shared.common.model.ProfilerInfo;

import java.util.List;

public class ProfilerSettingsGrpcService extends ProfilerSettingsServiceGrpc.ProfilerSettingsServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilerSettingsGrpcService.class);

    private final ServerPlatformRepositories platformRepositories;
    private final ProjectManager.Factory projectManagerFactory;
    private final ProfilerRepository profilerRepository;

    public ProfilerSettingsGrpcService(
            ServerPlatformRepositories platformRepositories,
            ProjectManager.Factory projectManagerFactory) {

        this.platformRepositories = platformRepositories;
        this.projectManagerFactory = projectManagerFactory;
        this.profilerRepository = platformRepositories.newProfilerRepository();
    }

    @Override
    public void getSettings(GetProfilerSettingsRequest request, StreamObserver<GetProfilerSettingsResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getProjectId());
            EffectiveProfilerSettings settings = project.profilerSettingsManager().fetchEffectiveSettings();

            LOG.debug("Fetched profiler settings via gRPC: projectId={}", request.getProjectId());

            GetProfilerSettingsResponse response = GetProfilerSettingsResponse.newBuilder()
                    .setAgentSettings(settings.agentSettings() != null ? settings.agentSettings() : "")
                    .setLevel(toProtoLevel(settings.level()))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to get profiler settings: projectId={}", request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void upsertSettings(UpsertProfilerSettingsRequest request, StreamObserver<UpsertProfilerSettingsResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getProjectId());
            project.profilerSettingsManager().upsertSettings(request.getAgentSettings());

            LOG.debug("Upserted profiler settings via gRPC: projectId={}", request.getProjectId());

            responseObserver.onNext(UpsertProfilerSettingsResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to upsert profiler settings: projectId={}", request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void deleteSettings(DeleteProfilerSettingsRequest request, StreamObserver<DeleteProfilerSettingsResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getProjectId());
            project.profilerSettingsManager().deleteSettings();

            LOG.debug("Deleted profiler settings via gRPC: projectId={}", request.getProjectId());

            responseObserver.onNext(DeleteProfilerSettingsResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to delete profiler settings: projectId={}", request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listAllSettings(ListAllProfilerSettingsRequest request, StreamObserver<ListAllProfilerSettingsResponse> responseObserver) {
        try {
            List<ProfilerInfo> allSettings = profilerRepository.findAllSettings();

            ListAllProfilerSettingsResponse.Builder responseBuilder = ListAllProfilerSettingsResponse.newBuilder();
            for (ProfilerInfo info : allSettings) {
                responseBuilder.addSettings(ProfilerSettingsEntry.newBuilder()
                        .setWorkspaceId(info.workspaceId() != null ? info.workspaceId() : "")
                        .setProjectId(info.projectId() != null ? info.projectId() : "")
                        .setAgentSettings(info.agentSettings() != null ? info.agentSettings() : "")
                        .build());
            }

            LOG.debug("Listed all profiler settings via gRPC: count={}", allSettings.size());

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Failed to list all profiler settings", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void upsertSettingsAtLevel(UpsertProfilerSettingsAtLevelRequest request, StreamObserver<UpsertProfilerSettingsAtLevelResponse> responseObserver) {
        try {
            String workspaceId = request.getWorkspaceId().isEmpty() ? null : request.getWorkspaceId();
            String projectId = request.getProjectId().isEmpty() ? null : request.getProjectId();

            if (projectId != null && workspaceId == null) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Workspace ID is required when Project ID is provided")
                        .asRuntimeException());
                return;
            }

            ProfilerInfo profilerInfo = new ProfilerInfo(workspaceId, projectId, request.getAgentSettings());
            profilerRepository.upsertSettings(profilerInfo);

            LOG.debug("Upserted profiler settings at level via gRPC: workspaceId={} projectId={}", workspaceId, projectId);

            responseObserver.onNext(UpsertProfilerSettingsAtLevelResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to upsert profiler settings at level", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void deleteSettingsAtLevel(DeleteProfilerSettingsAtLevelRequest request, StreamObserver<DeleteProfilerSettingsAtLevelResponse> responseObserver) {
        try {
            String workspaceId = request.getWorkspaceId().isEmpty() ? null : request.getWorkspaceId();
            String projectId = request.getProjectId().isEmpty() ? null : request.getProjectId();

            if (projectId != null && workspaceId == null) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Workspace ID is required when Project ID is provided")
                        .asRuntimeException());
                return;
            }

            profilerRepository.deleteSettings(workspaceId, projectId);

            LOG.debug("Deleted profiler settings at level via gRPC: workspaceId={} projectId={}", workspaceId, projectId);

            responseObserver.onNext(DeleteProfilerSettingsAtLevelResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to delete profiler settings at level", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private ProjectManager findProject(String projectId) {
        return platformRepositories.newProjectRepository(projectId).find()
                .map(projectManagerFactory)
                .orElseThrow(() -> GrpcExceptions.notFound("Project not found: " + projectId));
    }

    private static SettingsLevel toProtoLevel(EffectiveProfilerSettings.SettingsLevel level) {
        return switch (level) {
            case PROJECT -> SettingsLevel.SETTINGS_LEVEL_PROJECT;
            case WORKSPACE -> SettingsLevel.SETTINGS_LEVEL_WORKSPACE;
            case GLOBAL -> SettingsLevel.SETTINGS_LEVEL_GLOBAL;
            case NONE -> SettingsLevel.SETTINGS_LEVEL_UNSPECIFIED;
        };
    }
}
