/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.manager.custom.builder.MethodTimingBuilder;
import cafe.jeffrey.profile.manager.custom.builder.MethodTracingCumulatedBuilder;
import cafe.jeffrey.profile.manager.custom.builder.MethodTracingOverviewBuilder;
import cafe.jeffrey.profile.manager.custom.builder.MethodTracingSlowestBuilder;
import cafe.jeffrey.profile.manager.custom.model.method.CumulationMode;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTimingData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingCumulatedData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingOverviewData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingSlowestData;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;

public class MethodTracingManagerImpl implements MethodTracingManager {

    private final ProfileInfo profileInfo;
    private final ProfileEventStreamRepository eventStreamRepository;

    public MethodTracingManagerImpl(ProfileInfo profileInfo, ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public MethodTracingOverviewData overview() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.METHOD_TRACE)
                .withTimeRange(timeRange);

        return eventStreamRepository.genericStreaming(configurer, new MethodTracingOverviewBuilder(timeRange));
    }

    @Override
    public MethodTracingSlowestData slowest() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.METHOD_TRACE)
                .withThreads()
                .withTimeRange(timeRange);

        return eventStreamRepository.genericStreaming(configurer, new MethodTracingSlowestBuilder());
    }

    @Override
    public MethodTracingCumulatedData cumulated(CumulationMode mode) {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.METHOD_TRACE)
                .withTimeRange(timeRange);

        return eventStreamRepository.genericStreaming(configurer, new MethodTracingCumulatedBuilder(mode));
    }

    @Override
    public MethodTimingData methodTiming() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        // No withThreads(): the event has no thread, by design -- it is a counter on the method, not
        // a record of any one call.
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.METHOD_TIMING)
                .withJsonFields()
                .withTimeRange(timeRange);

        return eventStreamRepository.genericStreaming(configurer, new MethodTimingBuilder());
    }
}
