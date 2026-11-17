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

import cafe.jeffrey.profile.manager.custom.builder.GrpcOverviewEventBuilder;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcOverviewData;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcServiceDetailData;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcTrafficData;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;

import java.util.function.Predicate;

public class GrpcManagerImpl implements GrpcManager {

    private static final int MAX_SLOW_CALLS = 20;

    private final ProfileInfo profileInfo;
    private final ProfileEventStreamRepository eventStreamRepository;
    private final Type eventType;

    public GrpcManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventStreamRepository eventStreamRepository,
            Type eventType) {

        this.profileInfo = profileInfo;
        this.eventStreamRepository = eventStreamRepository;
        this.eventType = eventType;
    }

    @Override
    public GrpcOverviewData overviewData() {
        return _overviewData(null);
    }

    @Override
    public GrpcOverviewData overviewData(String service) {
        return _overviewData(service);
    }

    @Override
    public GrpcServiceDetailData serviceDetailData(String service) {
        GrpcOverviewEventBuilder builder = streamAndCollect(service);
        return builder.buildServiceDetail(service);
    }

    @Override
    public GrpcTrafficData trafficData() {
        return _trafficData(null);
    }

    @Override
    public GrpcTrafficData trafficData(String service) {
        return _trafficData(service);
    }

    private GrpcOverviewData _overviewData(String service) {
        Predicate<String> serviceFilter = service != null ? service::equals : null;
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        return eventStreamRepository.genericStreaming(
                createConfigurer(timeRange),
                new GrpcOverviewEventBuilder(timeRange, MAX_SLOW_CALLS, serviceFilter));
    }

    private GrpcTrafficData _trafficData(String service) {
        GrpcOverviewEventBuilder builder = streamAndCollect(service);
        return builder.buildTraffic();
    }

    /**
     * Creates a builder, streams all events through it via genericStreaming,
     * and returns the populated builder for calling specific build methods
     * (buildServiceDetail, buildTraffic) that are not accessible through
     * the standard RecordBuilder.build() contract.
     */
    private GrpcOverviewEventBuilder streamAndCollect(String service) {
        Predicate<String> serviceFilter = service != null ? service::equals : null;
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        GrpcOverviewEventBuilder builder =
                new GrpcOverviewEventBuilder(timeRange, MAX_SLOW_CALLS, serviceFilter);

        // genericStreaming populates the builder via onRecord calls and then
        // calls build() internally. The returned GrpcOverviewData is discarded
        // because we need a different build output from the same collected data.
        eventStreamRepository.genericStreaming(createConfigurer(timeRange), builder);
        return builder;
    }

    private EventQueryConfigurer createConfigurer(RelativeTimeRange timeRange) {
        return new EventQueryConfigurer()
                .withEventType(eventType)
                .withTimeRange(timeRange)
                .withJsonFields();
    }
}
