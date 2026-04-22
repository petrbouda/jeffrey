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

package pbouda.jeffrey.local.core.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.api.v1.*;
import pbouda.jeffrey.local.core.resources.response.InstanceDetailResponse;
import pbouda.jeffrey.local.core.resources.response.InstanceEnvironmentResponse;
import pbouda.jeffrey.local.core.resources.response.InstanceResponse;
import pbouda.jeffrey.local.core.resources.response.InstanceSessionResponse;
import pbouda.jeffrey.local.core.resources.response.InstanceStatsResponse;

import java.util.List;

public class RemoteInstancesClient {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteInstancesClient.class);

    private final InstanceServiceGrpc.InstanceServiceBlockingStub stub;

    public RemoteInstancesClient(GrpcServerConnection connection) {
        this.stub = InstanceServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public List<InstanceResponse> projectInstances(String projectId, boolean includeSessions) {
        ListInstancesResponse response = stub.listInstances(
                ListInstancesRequest.newBuilder()
                        .setProjectId(projectId)
                        .setIncludeSessions(includeSessions)
                        .build());

        LOG.debug("Listed instances via gRPC: projectId={} count={} include_sessions={}",
                projectId, response.getInstancesCount(), includeSessions);

        return response.getInstancesList().stream()
                .map(RemoteInstancesClient::toInstanceResponse)
                .toList();
    }

    public InstanceResponse projectInstance(String instanceId) {
        GetInstanceResponse response = stub.getInstance(
                GetInstanceRequest.newBuilder()
                        .setInstanceId(instanceId)
                        .build());

        LOG.debug("Fetched instance via gRPC: instanceId={}", instanceId);

        return toInstanceResponse(response.getInstance());
    }

    public InstanceDetailResponse instanceDetail(String instanceId) {
        GetInstanceDetailResponse response = stub.getInstanceDetail(
                GetInstanceDetailRequest.newBuilder()
                        .setInstanceId(instanceId)
                        .build());

        LOG.debug("Fetched instance detail via gRPC: instanceId={} files={} totalSize={} hasEnvironment={}",
                instanceId, response.getStats().getFileCount(), response.getStats().getTotalSizeBytes(),
                response.hasEnvironment());

        return new InstanceDetailResponse(
                toInstanceResponse(response.getInstance()),
                new InstanceStatsResponse(
                        response.getStats().getFileCount(),
                        response.getStats().getTotalSizeBytes()),
                response.hasEnvironment() ? toEnvironmentResponse(response.getEnvironment()) : null);
    }

    private static InstanceEnvironmentResponse toEnvironmentResponse(InstanceEnvironment env) {
        return new InstanceEnvironmentResponse(
                env.hasJvm() ? toJvm(env.getJvm()) : null,
                env.hasOs() ? toOs(env.getOs()) : null,
                env.hasCpu() ? toCpu(env.getCpu()) : null,
                env.hasGc() ? toGc(env.getGc()) : null,
                env.hasGcHeap() ? toGcHeap(env.getGcHeap()) : null,
                env.hasCompiler() ? toCompiler(env.getCompiler()) : null,
                env.hasContainer() ? toContainer(env.getContainer()) : null,
                env.hasVirtualization() ? toVirtualization(env.getVirtualization()) : null,
                env.hasShutdown() ? toShutdown(env.getShutdown()) : null);
    }

    private static InstanceEnvironmentResponse.JvmInformation toJvm(JvmInformation j) {
        return new InstanceEnvironmentResponse.JvmInformation(
                j.hasJvmName() ? j.getJvmName() : null,
                j.hasJvmVersion() ? j.getJvmVersion() : null,
                j.hasJvmArguments() ? j.getJvmArguments() : null,
                j.hasJvmFlags() ? j.getJvmFlags() : null,
                j.hasJavaArguments() ? j.getJavaArguments() : null,
                j.hasJvmStartTime() ? j.getJvmStartTime() : null,
                j.hasPid() ? j.getPid() : null);
    }

    private static InstanceEnvironmentResponse.OsInformation toOs(OsInformation o) {
        return new InstanceEnvironmentResponse.OsInformation(
                o.hasOsVersion() ? o.getOsVersion() : null);
    }

    private static InstanceEnvironmentResponse.CpuInformation toCpu(CpuInformation c) {
        return new InstanceEnvironmentResponse.CpuInformation(
                c.hasCpu() ? c.getCpu() : null,
                c.hasDescription() ? c.getDescription() : null,
                c.hasSockets() ? c.getSockets() : null,
                c.hasCores() ? c.getCores() : null,
                c.hasHwThreads() ? c.getHwThreads() : null);
    }

    private static InstanceEnvironmentResponse.GcConfiguration toGc(GcConfiguration g) {
        return new InstanceEnvironmentResponse.GcConfiguration(
                g.hasYoungCollector() ? g.getYoungCollector() : null,
                g.hasOldCollector() ? g.getOldCollector() : null,
                g.hasParallelGcThreads() ? g.getParallelGcThreads() : null,
                g.hasConcurrentGcThreads() ? g.getConcurrentGcThreads() : null,
                g.hasUsesDynamicGcThreads() ? g.getUsesDynamicGcThreads() : null,
                g.hasIsExplicitGcConcurrent() ? g.getIsExplicitGcConcurrent() : null,
                g.hasIsExplicitGcDisabled() ? g.getIsExplicitGcDisabled() : null,
                g.hasPauseTargetMillis() ? g.getPauseTargetMillis() : null,
                g.hasGcTimeRatio() ? g.getGcTimeRatio() : null);
    }

    private static InstanceEnvironmentResponse.GcHeapConfiguration toGcHeap(GcHeapConfiguration h) {
        return new InstanceEnvironmentResponse.GcHeapConfiguration(
                h.hasMinSize() ? h.getMinSize() : null,
                h.hasMaxSize() ? h.getMaxSize() : null,
                h.hasInitialSize() ? h.getInitialSize() : null,
                h.hasUsesCompressedOops() ? h.getUsesCompressedOops() : null,
                h.hasCompressedOopsMode() ? h.getCompressedOopsMode() : null,
                h.hasObjectAlignment() ? h.getObjectAlignment() : null,
                h.hasHeapAddressBits() ? h.getHeapAddressBits() : null);
    }

    private static InstanceEnvironmentResponse.CompilerConfiguration toCompiler(CompilerConfiguration c) {
        return new InstanceEnvironmentResponse.CompilerConfiguration(
                c.hasThreadCount() ? c.getThreadCount() : null,
                c.hasTieredCompilation() ? c.getTieredCompilation() : null,
                c.hasDynamicCompilerThreadCount() ? c.getDynamicCompilerThreadCount() : null);
    }

    private static InstanceEnvironmentResponse.ContainerConfiguration toContainer(ContainerConfiguration c) {
        return new InstanceEnvironmentResponse.ContainerConfiguration(
                c.hasContainerType() ? c.getContainerType() : null,
                c.hasCpuSlicePeriod() ? c.getCpuSlicePeriod() : null,
                c.hasCpuQuota() ? c.getCpuQuota() : null,
                c.hasCpuShares() ? c.getCpuShares() : null,
                c.hasEffectiveCpuCount() ? c.getEffectiveCpuCount() : null,
                c.hasMemorySoftLimit() ? c.getMemorySoftLimit() : null,
                c.hasMemoryLimit() ? c.getMemoryLimit() : null,
                c.hasSwapMemoryLimit() ? c.getSwapMemoryLimit() : null,
                c.hasHostTotalMemory() ? c.getHostTotalMemory() : null,
                c.hasHostTotalSwapMemory() ? c.getHostTotalSwapMemory() : null);
    }

    private static InstanceEnvironmentResponse.VirtualizationInformation toVirtualization(VirtualizationInformation v) {
        return new InstanceEnvironmentResponse.VirtualizationInformation(
                v.hasName() ? v.getName() : null);
    }

    private static InstanceEnvironmentResponse.ShutdownInfo toShutdown(ShutdownInfo s) {
        return new InstanceEnvironmentResponse.ShutdownInfo(
                s.hasReason() ? s.getReason() : null,
                s.hasEventTime() ? s.getEventTime() : null,
                s.hasKind() ? toShutdownKind(s.getKind()) : null);
    }

    private static String toShutdownKind(ShutdownKind kind) {
        return switch (kind) {
            case SHUTDOWN_KIND_GRACEFUL -> "GRACEFUL";
            case SHUTDOWN_KIND_VM_ERROR -> "VM_ERROR";
            case SHUTDOWN_KIND_CRASH_OOM -> "CRASH_OOM";
            case SHUTDOWN_KIND_UNKNOWN, SHUTDOWN_KIND_UNSPECIFIED, UNRECOGNIZED -> "UNKNOWN";
        };
    }

    public List<InstanceSessionResponse> projectInstanceSessions(String instanceId) {

        ListInstanceSessionsResponse response = stub.listInstanceSessions(
                ListInstanceSessionsRequest.newBuilder()
                        .setInstanceId(instanceId)
                        .build());

        LOG.debug("Listed instance sessions via gRPC: instanceId={} count={}",
                instanceId, response.getSessionsCount());

        return response.getSessionsList().stream()
                .map(RemoteInstancesClient::toSessionResponse)
                .toList();
    }

    private static InstanceResponse toInstanceResponse(InstanceInfo proto) {
        List<InstanceSessionResponse> sessions = proto.getSessionsList().stream()
                .map(RemoteInstancesClient::toSessionResponse)
                .toList();

        return new InstanceResponse(
                proto.getId(),
                proto.getInstanceName().isEmpty() ? null : proto.getInstanceName(),
                fromProtoInstanceStatus(proto.getStatus()),
                proto.getCreatedAt(),
                proto.hasFinishedAt() ? proto.getFinishedAt() : null,
                proto.hasExpiringAt() ? proto.getExpiringAt() : null,
                proto.hasExpiredAt() ? proto.getExpiredAt() : null,
                proto.getSessionCount(),
                proto.hasActiveSessionId() ? proto.getActiveSessionId() : null,
                proto.hasFinishedAt() ? proto.getFinishedAt() - proto.getCreatedAt() : null,
                sessions);
    }

    private static InstanceSessionResponse toSessionResponse(InstanceSessionInfo proto) {
        return new InstanceSessionResponse(
                proto.getId(),
                proto.getRepositoryId().isEmpty() ? null : proto.getRepositoryId(),
                proto.getCreatedAt(),
                proto.hasFinishedAt() ? proto.getFinishedAt() : null,
                proto.getIsActive(),
                proto.hasFinishedAt() ? proto.getFinishedAt() - proto.getCreatedAt() : null);
    }

    private static String fromProtoInstanceStatus(pbouda.jeffrey.server.api.v1.InstanceStatus status) {
        return switch (status) {
            case INSTANCE_STATUS_PENDING -> "PENDING";
            case INSTANCE_STATUS_ACTIVE -> "ACTIVE";
            case INSTANCE_STATUS_FINISHED -> "FINISHED";
            case INSTANCE_STATUS_EXPIRED -> "EXPIRED";
            default -> "UNKNOWN";
        };
    }

    private static Long parseLongOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
