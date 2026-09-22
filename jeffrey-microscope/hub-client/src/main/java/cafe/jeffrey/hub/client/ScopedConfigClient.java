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

import cafe.jeffrey.hub.api.v1.ConfigScopeKey;
import cafe.jeffrey.hub.api.v1.DeleteConfigRequest;
import cafe.jeffrey.hub.api.v1.GetConfigRequest;
import cafe.jeffrey.hub.api.v1.ListWorkspaceConfigsRequest;
import cafe.jeffrey.hub.api.v1.ListWorkspaceConfigsResponse;
import cafe.jeffrey.hub.api.v1.ScopedConfigServiceGrpc;
import cafe.jeffrey.hub.api.v1.UpsertConfigRequest;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import cafe.jeffrey.microscope.model.config.ConfigEntry;
import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.config.ConfigType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Reads and writes the configuration the hub publishes to the shared volume.
 *
 * <p>Values only; the hub renders them into the file the provisioner reads, and Microscope never
 * sees that file. What the editor shows and what a JVM merges are therefore the same thing, told
 * by the same side.</p>
 */
public class ScopedConfigClient {

    private static final Logger LOG = LoggerFactory.getLogger(ScopedConfigClient.class);

    private final ScopedConfigServiceGrpc.ScopedConfigServiceBlockingStub stub;

    public ScopedConfigClient(GrpcHubConnection connection) {
        this.stub = ScopedConfigServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public List<ConfigEntry> get(ConfigScope scope, String workspaceId, String projectId) {
        return entries(stub.getConfig(GetConfigRequest.newBuilder()
                .setKey(key(scope, workspaceId, projectId))
                .build()).getEntriesList());
    }

    /** The global scope, the workspace's own and every project's, in merge order. */
    public List<ConfigEntry> listForWorkspace(String workspaceId) {
        ListWorkspaceConfigsResponse response = stub.listWorkspaceConfigs(
                ListWorkspaceConfigsRequest.newBuilder().setWorkspaceId(workspaceId).build());

        LOG.debug("Listed workspace configuration via gRPC: workspace_id={} entries={}",
                workspaceId, response.getEntriesCount());

        return entries(response.getEntriesList());
    }

    public List<ConfigEntry> upsert(
            ConfigScope scope, String workspaceId, String projectId, ConfigType type, String value) {

        return entries(stub.upsertConfig(UpsertConfigRequest.newBuilder()
                .setKey(key(scope, workspaceId, projectId))
                .setType(ClientProtoMappers.configType(type))
                .setValue(value)
                .build()).getEntriesList());
    }

    public List<ConfigEntry> delete(
            ConfigScope scope, String workspaceId, String projectId, ConfigType type) {

        return entries(stub.deleteConfig(DeleteConfigRequest.newBuilder()
                .setKey(key(scope, workspaceId, projectId))
                .setType(ClientProtoMappers.configType(type))
                .build()).getEntriesList());
    }

    private static List<ConfigEntry> entries(List<cafe.jeffrey.hub.api.v1.ConfigEntry> wire) {
        return wire.stream().map(ClientProtoMappers::configEntry).toList();
    }

    private static ConfigScopeKey key(ConfigScope scope, String workspaceId, String projectId) {
        return ConfigScopeKey.newBuilder()
                .setScope(ClientProtoMappers.configScope(scope))
                .setWorkspaceId(ClientProtoMappers.orEmpty(workspaceId))
                .setProjectId(ClientProtoMappers.orEmpty(projectId))
                .build();
    }
}
