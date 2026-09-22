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

import cafe.jeffrey.hub.api.v1.ConfigEntry;
import cafe.jeffrey.hub.api.v1.ConfigScopeKey;
import cafe.jeffrey.hub.api.v1.DeleteConfigRequest;
import cafe.jeffrey.hub.api.v1.DeleteConfigResponse;
import cafe.jeffrey.hub.api.v1.GetConfigRequest;
import cafe.jeffrey.hub.api.v1.GetConfigResponse;
import cafe.jeffrey.hub.api.v1.ListWorkspaceConfigsRequest;
import cafe.jeffrey.hub.api.v1.ListWorkspaceConfigsResponse;
import cafe.jeffrey.hub.api.v1.ScopedConfigServiceGrpc;
import cafe.jeffrey.hub.api.v1.UpsertConfigRequest;
import cafe.jeffrey.hub.api.v1.UpsertConfigResponse;
import cafe.jeffrey.hub.core.config.ScopedConfigManager;
import cafe.jeffrey.hub.model.config.ScopedConfigEntry;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.config.ConfigType;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The hub's configuration API: typed values per scope, never free-form text.
 *
 * <p>Both enums are translated rather than trusted. A value proto3 does not recognise arrives as
 * the zero constant, so an unspecified or unknown scope or type is rejected here instead of
 * defaulting to whichever member happens to be first — which for a type would mean storing a value
 * as something the caller never asked for.</p>
 */
public class ScopedConfigGrpcService extends ScopedConfigServiceGrpc.ScopedConfigServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(ScopedConfigGrpcService.class);

    private final ScopedConfigManager configManager;
    private final GrpcLookups lookups;

    public ScopedConfigGrpcService(ScopedConfigManager configManager, GrpcLookups lookups) {
        this.configManager = configManager;
        this.lookups = lookups;
    }

    @Override
    public void getConfig(GetConfigRequest request, StreamObserver<GetConfigResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ScopedConfigKey key = scopeKey(request.getKey());
            lookups.requireExists(key);

            return GetConfigResponse.newBuilder()
                    .addAllEntries(entries(configManager.find(key)))
                    .build();
        });
    }

    @Override
    public void listWorkspaceConfigs(
            ListWorkspaceConfigsRequest request, StreamObserver<ListWorkspaceConfigsResponse> responseObserver) {

        GrpcUnary.respond(responseObserver, () -> {
            String workspaceId = request.getWorkspaceId();
            if (workspaceId == null || workspaceId.isBlank()) {
                throw GrpcExceptions.invalidArgument("Workspace ID is required");
            }
            lookups.requireExists(ScopedConfigKey.workspace(workspaceId));

            return ListWorkspaceConfigsResponse.newBuilder()
                    .addAllEntries(entries(configManager.findForWorkspace(workspaceId)))
                    .build();
        });
    }

    @Override
    public void upsertConfig(UpsertConfigRequest request, StreamObserver<UpsertConfigResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ScopedConfigKey key = scopeKey(request.getKey());
            lookups.requireExists(key);
            ConfigType type = configType(request.getType());

            List<ScopedConfigEntry> stored = configManager.upsert(key, type, request.getValue());

            LOG.debug("Upserted configuration via gRPC: scope={} workspace_id={} project_id={} type={}",
                    key.scope(), key.workspaceId(), key.projectId(), type);

            return UpsertConfigResponse.newBuilder()
                    .addAllEntries(entries(stored))
                    .build();
        });
    }

    @Override
    public void deleteConfig(DeleteConfigRequest request, StreamObserver<DeleteConfigResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ScopedConfigKey key = scopeKey(request.getKey());
            lookups.requireExists(key);
            ConfigType type = configType(request.getType());

            List<ScopedConfigEntry> remaining = configManager.delete(key, type);

            LOG.debug("Deleted configuration via gRPC: scope={} workspace_id={} project_id={} type={}",
                    key.scope(), key.workspaceId(), key.projectId(), type);

            return DeleteConfigResponse.newBuilder()
                    .addAllEntries(entries(remaining))
                    .build();
        });
    }

    private static List<ConfigEntry> entries(List<ScopedConfigEntry> stored) {
        return stored.stream().map(ProtoMappers::configEntry).toList();
    }

    /**
     * The domain record enforces which ids a scope takes, so an inconsistent key arrives here as an
     * IllegalArgumentException and leaves as INVALID_ARGUMENT with the record's own message.
     */
    private static ScopedConfigKey scopeKey(ConfigScopeKey key) {
        return new ScopedConfigKey(
                configScope(key.getScope()),
                nullIfEmpty(key.getWorkspaceId()),
                nullIfEmpty(key.getProjectId()));
    }

    private static ConfigScope configScope(cafe.jeffrey.hub.api.v1.ConfigScope scope) {
        return switch (scope) {
            case CONFIG_SCOPE_GLOBAL -> ConfigScope.GLOBAL;
            case CONFIG_SCOPE_WORKSPACE -> ConfigScope.WORKSPACE;
            case CONFIG_SCOPE_PROJECT -> ConfigScope.PROJECT;
            case CONFIG_SCOPE_UNSPECIFIED, UNRECOGNIZED ->
                    throw GrpcExceptions.invalidArgument("A configuration scope is required");
        };
    }

    private static ConfigType configType(cafe.jeffrey.hub.api.v1.ConfigType type) {
        return switch (type) {
            case CONFIG_TYPE_ASPROF_SETTINGS -> ConfigType.ASPROF_SETTINGS;
            case CONFIG_TYPE_UNSPECIFIED, UNRECOGNIZED ->
                    throw GrpcExceptions.invalidArgument(
                            "Unknown configuration type; this hub publishes " + List.of(ConfigType.values()));
        };
    }

    private static String nullIfEmpty(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
