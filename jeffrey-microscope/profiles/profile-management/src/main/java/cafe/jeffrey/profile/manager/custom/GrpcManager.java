/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.profile.manager.custom;

import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcOverviewData;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcServiceDetailData;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcTrafficData;
import cafe.jeffrey.microscope.model.ProfileInfo;


public interface GrpcManager {

    @FunctionalInterface
    /**
     * Built per direction: the server and client halves read different event types, so a manager is
     * bound to one of them rather than deciding per call.
     */
    interface Factory {
        GrpcManager apply(ProfileInfo profileInfo, ExchangeDirection direction);
    }

    GrpcOverviewData overviewData();

    GrpcOverviewData overviewData(String service);

    GrpcServiceDetailData serviceDetailData(String service);

    GrpcTrafficData trafficData();

    GrpcTrafficData trafficData(String service);
}
