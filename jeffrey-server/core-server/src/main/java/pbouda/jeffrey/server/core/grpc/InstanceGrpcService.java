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

package pbouda.jeffrey.server.core.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.api.v1.*;
import pbouda.jeffrey.server.core.manager.RepositoryManager;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.repository.InstanceEnvironment;
import pbouda.jeffrey.shared.common.model.repository.InstanceStats;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class InstanceGrpcService extends InstanceServiceGrpc.InstanceServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceGrpcService.class);

    private final ServerPlatformRepositories platformRepositories;
    private final RepositoryManager.Factory repositoryManagerFactory;
    private final Clock clock;

    public InstanceGrpcService(
            ServerPlatformRepositories platformRepositories,
            RepositoryManager.Factory repositoryManagerFactory,
            Clock clock) {
        this.platformRepositories = platformRepositories;
        this.repositoryManagerFactory = repositoryManagerFactory;
        this.clock = clock;
    }

    @Override
    public void listInstances(ListInstancesRequest request, StreamObserver<ListInstancesResponse> responseObserver) {
        try {
            String projectId = request.getProjectId();
            List<ProjectInstanceInfo> rawInstances = platformRepositories
                    .newProjectInstanceRepository(projectId).findAll();

            Map<String, List<ProjectInstanceSessionInfo>> sessionsByInstanceId;
            if (request.getIncludeSessions()) {
                sessionsByInstanceId = platformRepositories.findSessionsByProjectId(projectId).stream()
                        .collect(Collectors.groupingBy(ProjectInstanceSessionInfo::instanceId));
            } else {
                sessionsByInstanceId = Map.of();
            }

            List<InstanceInfo> instances = rawInstances.stream()
                    .map(info -> toProto(info, sessionsByInstanceId.getOrDefault(info.id(), List.of()), clock))
                    .toList();

            LOG.debug("Listed instances via gRPC: projectId={} count={} include_sessions={}",
                    projectId, instances.size(), request.getIncludeSessions());

            responseObserver.onNext(ListInstancesResponse.newBuilder()
                    .addAllInstances(instances)
                    .build());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to list instances: projectId={}", request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getInstance(GetInstanceRequest request, StreamObserver<GetInstanceResponse> responseObserver) {
        try {
            ProjectInstanceInfo instance = platformRepositories.findInstanceById(request.getInstanceId())
                    .orElseThrow(() -> GrpcExceptions.notFound("Instance not found: " + request.getInstanceId()));

            LOG.debug("Fetched instance via gRPC: instanceId={}", request.getInstanceId());

            responseObserver.onNext(GetInstanceResponse.newBuilder()
                    .setInstance(toProto(instance, List.of(), clock))
                    .build());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to get instance: instanceId={}", request.getInstanceId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listInstanceSessions(ListInstanceSessionsRequest request, StreamObserver<ListInstanceSessionsResponse> responseObserver) {
        try {
            List<InstanceSessionInfo> sessions = platformRepositories
                    .findSessionsByInstanceId(request.getInstanceId()).stream()
                    .map(s -> toSessionProto(s, clock))
                    .toList();

            LOG.debug("Listed instance sessions via gRPC: instanceId={} count={}",
                    request.getInstanceId(), sessions.size());

            responseObserver.onNext(ListInstanceSessionsResponse.newBuilder()
                    .addAllSessions(sessions)
                    .build());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to list instance sessions: instanceId={}", request.getInstanceId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getInstanceDetail(GetInstanceDetailRequest request, StreamObserver<GetInstanceDetailResponse> responseObserver) {
        try {
            String instanceId = request.getInstanceId();

            ProjectInstanceInfo info = platformRepositories.findInstanceById(instanceId)
                    .orElseThrow(() -> GrpcExceptions.notFound("Instance not found: " + instanceId));

            List<ProjectInstanceSessionInfo> sessions = platformRepositories.findSessionsByInstanceId(instanceId);

            ProjectInfo projectInfo = platformRepositories.newProjectRepository(info.projectId()).find()
                    .orElseThrow(() -> GrpcExceptions.notFound("Project not found: " + info.projectId()));
            RepositoryManager repoManager = repositoryManagerFactory.apply(projectInfo);
            InstanceStats stats = repoManager.instanceStats(instanceId);

            boolean expectShutdown = info.status() == ProjectInstanceInfo.ProjectInstanceStatus.FINISHED;
            Optional<InstanceEnvironment> environment = repoManager.instanceEnvironment(instanceId, expectShutdown);

            LOG.debug("Fetched instance detail via gRPC: instanceId={} sessions={} files={} totalSize={} hasEnvironment={}",
                    instanceId, sessions.size(), stats.fileCount(), stats.totalSizeBytes(), environment.isPresent());

            GetInstanceDetailResponse.Builder respBuilder = GetInstanceDetailResponse.newBuilder()
                    .setInstance(toProto(info, sessions, clock))
                    .setStats(toProtoStats(stats));
            environment.ifPresent(env -> respBuilder.setEnvironment(toProtoEnvironment(env)));

            responseObserver.onNext(respBuilder.build());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to get instance detail: instanceId={}", request.getInstanceId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private static pbouda.jeffrey.server.api.v1.InstanceStats toProtoStats(InstanceStats stats) {
        return pbouda.jeffrey.server.api.v1.InstanceStats.newBuilder()
                .setFileCount(stats.fileCount())
                .setTotalSizeBytes(stats.totalSizeBytes())
                .build();
    }

    private static pbouda.jeffrey.server.api.v1.InstanceEnvironment toProtoEnvironment(InstanceEnvironment env) {
        pbouda.jeffrey.server.api.v1.InstanceEnvironment.Builder b =
                pbouda.jeffrey.server.api.v1.InstanceEnvironment.newBuilder();
        env.jvm().map(InstanceGrpcService::toProtoJvm).ifPresent(b::setJvm);
        env.os().map(InstanceGrpcService::toProtoOs).ifPresent(b::setOs);
        env.cpu().map(InstanceGrpcService::toProtoCpu).ifPresent(b::setCpu);
        env.gc().map(InstanceGrpcService::toProtoGc).ifPresent(b::setGc);
        env.gcHeap().map(InstanceGrpcService::toProtoGcHeap).ifPresent(b::setGcHeap);
        env.compiler().map(InstanceGrpcService::toProtoCompiler).ifPresent(b::setCompiler);
        env.container().map(InstanceGrpcService::toProtoContainer).ifPresent(b::setContainer);
        env.virtualization().map(InstanceGrpcService::toProtoVirtualization).ifPresent(b::setVirtualization);
        env.shutdown().map(InstanceGrpcService::toProtoShutdown).ifPresent(b::setShutdown);
        return b.build();
    }

    private static pbouda.jeffrey.server.api.v1.JvmInformation toProtoJvm(InstanceEnvironment.JvmInformation j) {
        pbouda.jeffrey.server.api.v1.JvmInformation.Builder b = pbouda.jeffrey.server.api.v1.JvmInformation.newBuilder();
        j.jvmName().ifPresent(b::setJvmName);
        j.jvmVersion().ifPresent(b::setJvmVersion);
        j.jvmArguments().ifPresent(b::setJvmArguments);
        j.jvmFlags().ifPresent(b::setJvmFlags);
        j.javaArguments().ifPresent(b::setJavaArguments);
        j.jvmStartTime().ifPresent(b::setJvmStartTime);
        j.pid().ifPresent(b::setPid);
        return b.build();
    }

    private static pbouda.jeffrey.server.api.v1.OsInformation toProtoOs(InstanceEnvironment.OsInformation o) {
        pbouda.jeffrey.server.api.v1.OsInformation.Builder b = pbouda.jeffrey.server.api.v1.OsInformation.newBuilder();
        o.osVersion().ifPresent(b::setOsVersion);
        return b.build();
    }

    private static pbouda.jeffrey.server.api.v1.CpuInformation toProtoCpu(InstanceEnvironment.CpuInformation c) {
        pbouda.jeffrey.server.api.v1.CpuInformation.Builder b = pbouda.jeffrey.server.api.v1.CpuInformation.newBuilder();
        c.cpu().ifPresent(b::setCpu);
        c.description().ifPresent(b::setDescription);
        c.sockets().ifPresent(b::setSockets);
        c.cores().ifPresent(b::setCores);
        c.hwThreads().ifPresent(b::setHwThreads);
        return b.build();
    }

    private static pbouda.jeffrey.server.api.v1.GcConfiguration toProtoGc(InstanceEnvironment.GcConfiguration g) {
        pbouda.jeffrey.server.api.v1.GcConfiguration.Builder b = pbouda.jeffrey.server.api.v1.GcConfiguration.newBuilder();
        g.youngCollector().ifPresent(b::setYoungCollector);
        g.oldCollector().ifPresent(b::setOldCollector);
        g.parallelGCThreads().ifPresent(b::setParallelGcThreads);
        g.concurrentGCThreads().ifPresent(b::setConcurrentGcThreads);
        g.usesDynamicGCThreads().ifPresent(b::setUsesDynamicGcThreads);
        g.isExplicitGCConcurrent().ifPresent(b::setIsExplicitGcConcurrent);
        g.isExplicitGCDisabled().ifPresent(b::setIsExplicitGcDisabled);
        g.pauseTargetMillis().ifPresent(b::setPauseTargetMillis);
        g.gcTimeRatio().ifPresent(b::setGcTimeRatio);
        return b.build();
    }

    private static pbouda.jeffrey.server.api.v1.GcHeapConfiguration toProtoGcHeap(InstanceEnvironment.GcHeapConfiguration h) {
        pbouda.jeffrey.server.api.v1.GcHeapConfiguration.Builder b = pbouda.jeffrey.server.api.v1.GcHeapConfiguration.newBuilder();
        h.minSize().ifPresent(b::setMinSize);
        h.maxSize().ifPresent(b::setMaxSize);
        h.initialSize().ifPresent(b::setInitialSize);
        h.usesCompressedOops().ifPresent(b::setUsesCompressedOops);
        h.compressedOopsMode().ifPresent(b::setCompressedOopsMode);
        h.objectAlignment().ifPresent(b::setObjectAlignment);
        h.heapAddressBits().ifPresent(b::setHeapAddressBits);
        return b.build();
    }

    private static pbouda.jeffrey.server.api.v1.CompilerConfiguration toProtoCompiler(InstanceEnvironment.CompilerConfiguration c) {
        pbouda.jeffrey.server.api.v1.CompilerConfiguration.Builder b = pbouda.jeffrey.server.api.v1.CompilerConfiguration.newBuilder();
        c.threadCount().ifPresent(b::setThreadCount);
        c.tieredCompilation().ifPresent(b::setTieredCompilation);
        c.dynamicCompilerThreadCount().ifPresent(b::setDynamicCompilerThreadCount);
        return b.build();
    }

    private static pbouda.jeffrey.server.api.v1.ContainerConfiguration toProtoContainer(InstanceEnvironment.ContainerConfiguration c) {
        pbouda.jeffrey.server.api.v1.ContainerConfiguration.Builder b = pbouda.jeffrey.server.api.v1.ContainerConfiguration.newBuilder();
        c.containerType().ifPresent(b::setContainerType);
        c.cpuSlicePeriod().ifPresent(b::setCpuSlicePeriod);
        c.cpuQuota().ifPresent(b::setCpuQuota);
        c.cpuShares().ifPresent(b::setCpuShares);
        c.effectiveCpuCount().ifPresent(b::setEffectiveCpuCount);
        c.memorySoftLimit().ifPresent(b::setMemorySoftLimit);
        c.memoryLimit().ifPresent(b::setMemoryLimit);
        c.swapMemoryLimit().ifPresent(b::setSwapMemoryLimit);
        c.hostTotalMemory().ifPresent(b::setHostTotalMemory);
        c.hostTotalSwapMemory().ifPresent(b::setHostTotalSwapMemory);
        return b.build();
    }

    private static pbouda.jeffrey.server.api.v1.VirtualizationInformation toProtoVirtualization(InstanceEnvironment.VirtualizationInformation v) {
        pbouda.jeffrey.server.api.v1.VirtualizationInformation.Builder b = pbouda.jeffrey.server.api.v1.VirtualizationInformation.newBuilder();
        v.name().ifPresent(b::setName);
        return b.build();
    }

    private static pbouda.jeffrey.server.api.v1.ShutdownInfo toProtoShutdown(InstanceEnvironment.ShutdownInfo s) {
        pbouda.jeffrey.server.api.v1.ShutdownInfo.Builder b = pbouda.jeffrey.server.api.v1.ShutdownInfo.newBuilder();
        s.reason().ifPresent(b::setReason);
        s.eventTime().ifPresent(b::setEventTime);
        b.setKind(toProtoShutdownKind(s.kind()));
        return b.build();
    }

    private static pbouda.jeffrey.server.api.v1.ShutdownKind toProtoShutdownKind(InstanceEnvironment.ShutdownKind kind) {
        return switch (kind) {
            case GRACEFUL -> pbouda.jeffrey.server.api.v1.ShutdownKind.SHUTDOWN_KIND_GRACEFUL;
            case VM_ERROR -> pbouda.jeffrey.server.api.v1.ShutdownKind.SHUTDOWN_KIND_VM_ERROR;
            case CRASH_OOM -> pbouda.jeffrey.server.api.v1.ShutdownKind.SHUTDOWN_KIND_CRASH_OOM;
            case UNKNOWN -> pbouda.jeffrey.server.api.v1.ShutdownKind.SHUTDOWN_KIND_UNKNOWN;
        };
    }

    private static InstanceInfo toProto(ProjectInstanceInfo info, List<ProjectInstanceSessionInfo> sessions, Clock clock) {
        InstanceInfo.Builder builder = InstanceInfo.newBuilder()
                .setId(info.id())
                .setInstanceName(info.instanceName() != null ? info.instanceName() : "")
                .setStatus(toProtoInstanceStatus(info.status()))
                .setCreatedAt(info.startedAt().toEpochMilli())
                .setSessionCount(info.sessionCount());

        if (info.finishedAt() != null) {
            builder.setFinishedAt(info.finishedAt().toEpochMilli());
        }
        if (info.expiringAt() != null) {
            builder.setExpiringAt(info.expiringAt().toEpochMilli());
        }
        if (info.expiredAt() != null) {
            builder.setExpiredAt(info.expiredAt().toEpochMilli());
        }
        if (info.activeSessionId() != null) {
            builder.setActiveSessionId(info.activeSessionId());
        }

        for (ProjectInstanceSessionInfo session : sessions) {
            builder.addSessions(toSessionProto(session, clock));
        }

        return builder.build();
    }

    private static InstanceSessionInfo toSessionProto(ProjectInstanceSessionInfo info, Clock clock) {
        InstanceSessionInfo.Builder builder = InstanceSessionInfo.newBuilder()
                .setId(info.sessionId())
                .setRepositoryId(info.repositoryId() != null ? info.repositoryId() : "")
                .setCreatedAt(info.createdAt().toEpochMilli())
                .setIsActive(info.finishedAt() == null);

        if (info.finishedAt() != null) {
            builder.setFinishedAt(info.finishedAt().toEpochMilli());
        }

        return builder.build();
    }

    private static pbouda.jeffrey.server.api.v1.InstanceStatus toProtoInstanceStatus(
            ProjectInstanceInfo.ProjectInstanceStatus status) {
        return switch (status) {
            case PENDING -> pbouda.jeffrey.server.api.v1.InstanceStatus.INSTANCE_STATUS_PENDING;
            case ACTIVE -> pbouda.jeffrey.server.api.v1.InstanceStatus.INSTANCE_STATUS_ACTIVE;
            case FINISHED -> pbouda.jeffrey.server.api.v1.InstanceStatus.INSTANCE_STATUS_FINISHED;
            case EXPIRED -> pbouda.jeffrey.server.api.v1.InstanceStatus.INSTANCE_STATUS_EXPIRED;
        };
    }
}
