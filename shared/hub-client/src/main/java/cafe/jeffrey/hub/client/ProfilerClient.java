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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.microscope.grpc.client.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.shared.common.model.EffectiveProfilerSettings;
import cafe.jeffrey.shared.common.model.EffectiveProfilerSettings.SettingsLevel;
import cafe.jeffrey.shared.common.model.ProfilerInfo;

import java.util.List;

public class ProfilerClient {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilerClient.class);

    private final ProfilerSettingsServiceGrpc.ProfilerSettingsServiceBlockingStub stub;

    public ProfilerClient(GrpcHubConnection connection) {
        this.stub = ProfilerSettingsServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public EffectiveProfilerSettings fetchProfilerSettings(String projectId) {
        GetProfilerSettingsResponse response = stub.getSettings(
                GetProfilerSettingsRequest.newBuilder()
                        .setProjectId(projectId)
                        .build());

        String agentSettings = ClientProtoMappers.nullIfEmpty(response.getAgentSettings());
        SettingsLevel level = ClientProtoMappers.settingsLevel(response.getLevel());

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
                        ClientProtoMappers.nullIfEmpty(entry.getWorkspaceId()),
                        ClientProtoMappers.nullIfEmpty(entry.getProjectId()),
                        ClientProtoMappers.nullIfEmpty(entry.getAgentSettings())))
                .toList();

        LOG.debug("Listed all profiler settings via gRPC: count={}", result.size());
        return result;
    }

    public void upsertSettingsAtLevel(String workspaceId, String projectId, String agentSettings) {
        stub.upsertSettingsAtLevel(
                UpsertProfilerSettingsAtLevelRequest.newBuilder()
                        .setWorkspaceId(ClientProtoMappers.orEmpty(workspaceId))
                        .setProjectId(ClientProtoMappers.orEmpty(projectId))
                        .setAgentSettings(ClientProtoMappers.orEmpty(agentSettings))
                        .build());

        LOG.debug("Upserted profiler settings at level via gRPC: workspaceId={} projectId={}", workspaceId, projectId);
    }

    public WorkspaceProfilerLevels getWorkspaceEffectiveSettings(String workspaceId) {
        GetWorkspaceEffectiveSettingsResponse response = stub.getWorkspaceEffectiveSettings(
                GetWorkspaceEffectiveSettingsRequest.newBuilder()
                        .setWorkspaceId(workspaceId)
                        .build());

        String workspaceSettings = response.hasWorkspaceAgentSettings()
                ? response.getWorkspaceAgentSettings()
                : null;
        String globalSettings = response.hasGlobalAgentSettings()
                ? response.getGlobalAgentSettings()
                : null;

        LOG.debug("Fetched workspace effective profiler settings via gRPC: workspaceId={} workspaceSet={} globalSet={}",
                workspaceId, workspaceSettings != null, globalSettings != null);

        return new WorkspaceProfilerLevels(workspaceSettings, globalSettings);
    }

    public record WorkspaceProfilerLevels(String workspaceSettings, String globalSettings) {
    }

    public void deleteSettingsAtLevel(String workspaceId, String projectId) {
        stub.deleteSettingsAtLevel(
                DeleteProfilerSettingsAtLevelRequest.newBuilder()
                        .setWorkspaceId(ClientProtoMappers.orEmpty(workspaceId))
                        .setProjectId(ClientProtoMappers.orEmpty(projectId))
                        .build());

        LOG.debug("Deleted profiler settings at level via gRPC: workspaceId={} projectId={}", workspaceId, projectId);
    }

}
