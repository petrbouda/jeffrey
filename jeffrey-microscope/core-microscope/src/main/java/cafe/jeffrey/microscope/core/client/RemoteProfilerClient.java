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

package cafe.jeffrey.microscope.core.client;

import cafe.jeffrey.microscope.grpc.client.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.server.api.v1.*;
import cafe.jeffrey.shared.common.model.EffectiveProfilerSettings;
import cafe.jeffrey.shared.common.model.EffectiveProfilerSettings.SettingsLevel;
import cafe.jeffrey.shared.common.model.ProfilerInfo;

import java.util.List;

public class RemoteProfilerClient {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteProfilerClient.class);

    private final ProfilerSettingsServiceGrpc.ProfilerSettingsServiceBlockingStub stub;

    public RemoteProfilerClient(GrpcServerConnection connection) {
        this.stub = ProfilerSettingsServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public EffectiveProfilerSettings fetchProfilerSettings(String projectId) {
        GetProfilerSettingsResponse response = stub.getSettings(
                GetProfilerSettingsRequest.newBuilder()
                        .setProjectId(projectId)
                        .build());

        String agentSettings = response.getAgentSettings().isEmpty() ? null : response.getAgentSettings();
        SettingsLevel level = fromProtoLevel(response.getLevel());

        LOG.debug("Fetched profiler settings via gRPC: projectId={} level={}",
                projectId, level);

        return new EffectiveProfilerSettings(agentSettings, level);
    }

    public void upsertProfilerSettings(String projectId, String agentSettings) {
        stub.upsertSettings(
                UpsertProfilerSettingsRequest.newBuilder()
                        .setProjectId(projectId)
                        .setAgentSettings(agentSettings)
                        .build());

        LOG.debug("Upserted profiler settings via gRPC: projectId={}", projectId);
    }

    public void deleteProfilerSettings(String projectId) {
        stub.deleteSettings(
                DeleteProfilerSettingsRequest.newBuilder()
                        .setProjectId(projectId)
                        .build());

        LOG.debug("Deleted profiler settings via gRPC: projectId={}", projectId);
    }

    public List<ProfilerInfo> listAllSettings() {
        ListAllProfilerSettingsResponse response = stub.listAllSettings(
                ListAllProfilerSettingsRequest.getDefaultInstance());

        List<ProfilerInfo> result = response.getSettingsList().stream()
                .map(entry -> new ProfilerInfo(
                        entry.getWorkspaceId().isEmpty() ? null : entry.getWorkspaceId(),
                        entry.getProjectId().isEmpty() ? null : entry.getProjectId(),
                        entry.getAgentSettings().isEmpty() ? null : entry.getAgentSettings()))
                .toList();

        LOG.debug("Listed all profiler settings via gRPC: count={}", result.size());
        return result;
    }

    public void upsertSettingsAtLevel(String workspaceId, String projectId, String agentSettings) {
        stub.upsertSettingsAtLevel(
                UpsertProfilerSettingsAtLevelRequest.newBuilder()
                        .setWorkspaceId(workspaceId != null ? workspaceId : "")
                        .setProjectId(projectId != null ? projectId : "")
                        .setAgentSettings(agentSettings != null ? agentSettings : "")
                        .build());

        LOG.debug("Upserted profiler settings at level via gRPC: workspaceId={} projectId={}", workspaceId, projectId);
    }

    public void deleteSettingsAtLevel(String workspaceId, String projectId) {
        stub.deleteSettingsAtLevel(
                DeleteProfilerSettingsAtLevelRequest.newBuilder()
                        .setWorkspaceId(workspaceId != null ? workspaceId : "")
                        .setProjectId(projectId != null ? projectId : "")
                        .build());

        LOG.debug("Deleted profiler settings at level via gRPC: workspaceId={} projectId={}", workspaceId, projectId);
    }

    private static SettingsLevel fromProtoLevel(cafe.jeffrey.server.api.v1.SettingsLevel level) {
        return switch (level) {
            case SETTINGS_LEVEL_PROJECT -> SettingsLevel.PROJECT;
            case SETTINGS_LEVEL_WORKSPACE -> SettingsLevel.WORKSPACE;
            case SETTINGS_LEVEL_GLOBAL -> SettingsLevel.GLOBAL;
            default -> SettingsLevel.NONE;
        };
    }
}
