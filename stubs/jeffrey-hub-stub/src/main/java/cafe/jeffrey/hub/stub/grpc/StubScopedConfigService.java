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


package cafe.jeffrey.hub.stub.grpc;

import cafe.jeffrey.hub.api.v1.GetConfigRequest;
import cafe.jeffrey.hub.api.v1.GetConfigResponse;
import cafe.jeffrey.hub.api.v1.ListWorkspaceConfigsRequest;
import cafe.jeffrey.hub.api.v1.ListWorkspaceConfigsResponse;
import cafe.jeffrey.hub.api.v1.ScopedConfig;
import cafe.jeffrey.hub.api.v1.ScopedConfigServiceGrpc;
import io.grpc.stub.StreamObserver;

/**
 * Minimal stub {@code ScopedConfigService}: the read RPCs answer with an empty scope so the
 * Microscope Configuration tab renders without erroring. The mutating RPCs fall through to the
 * generated {@code UNIMPLEMENTED} default, which is the honest answer — this stub stores nothing
 * and publishes nothing.
 */
public class StubScopedConfigService extends ScopedConfigServiceGrpc.ScopedConfigServiceImplBase {

    @Override
    public void getConfig(GetConfigRequest request, StreamObserver<GetConfigResponse> responseObserver) {
        responseObserver.onNext(GetConfigResponse.newBuilder()
                .setConfig(ScopedConfig.newBuilder().setKey(request.getKey()).build())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void listWorkspaceConfigs(
            ListWorkspaceConfigsRequest request,
            StreamObserver<ListWorkspaceConfigsResponse> responseObserver) {

        responseObserver.onNext(ListWorkspaceConfigsResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
