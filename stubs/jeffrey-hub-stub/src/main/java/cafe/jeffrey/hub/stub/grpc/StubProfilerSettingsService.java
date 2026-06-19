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

import cafe.jeffrey.hub.api.v1.GetProfilerSettingsRequest;
import cafe.jeffrey.hub.api.v1.GetProfilerSettingsResponse;
import cafe.jeffrey.hub.api.v1.GetWorkspaceEffectiveSettingsRequest;
import cafe.jeffrey.hub.api.v1.GetWorkspaceEffectiveSettingsResponse;
import cafe.jeffrey.hub.api.v1.ListAllProfilerSettingsRequest;
import cafe.jeffrey.hub.api.v1.ListAllProfilerSettingsResponse;
import cafe.jeffrey.hub.api.v1.ProfilerSettingsServiceGrpc;
import cafe.jeffrey.hub.api.v1.SettingsLevel;
import io.grpc.stub.StreamObserver;

/**
 * Minimal stub {@code ProfilerSettingsService}: the read RPCs return empty defaults so the
 * Microscope Profiler-Settings tab renders without erroring. Mutating RPCs fall through to
 * the generated {@code UNIMPLEMENTED} default.
 */
public class StubProfilerSettingsService extends ProfilerSettingsServiceGrpc.ProfilerSettingsServiceImplBase {

    @Override
    public void getSettings(
            GetProfilerSettingsRequest request,
            StreamObserver<GetProfilerSettingsResponse> responseObserver) {

        responseObserver.onNext(GetProfilerSettingsResponse.newBuilder()
                .setAgentSettings("")
                .setLevel(SettingsLevel.SETTINGS_LEVEL_GLOBAL)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void listAllSettings(
            ListAllProfilerSettingsRequest request,
            StreamObserver<ListAllProfilerSettingsResponse> responseObserver) {

        responseObserver.onNext(ListAllProfilerSettingsResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getWorkspaceEffectiveSettings(
            GetWorkspaceEffectiveSettingsRequest request,
            StreamObserver<GetWorkspaceEffectiveSettingsResponse> responseObserver) {

        responseObserver.onNext(GetWorkspaceEffectiveSettingsResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
