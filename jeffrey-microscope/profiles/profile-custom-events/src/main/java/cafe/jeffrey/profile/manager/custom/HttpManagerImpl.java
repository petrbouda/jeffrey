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
import cafe.jeffrey.profile.manager.custom.builder.HttpOverviewEventBuilder;
import cafe.jeffrey.profile.manager.custom.model.http.HttpOverviewData;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;

import java.util.function.Predicate;

public class HttpManagerImpl implements HttpManager {

    private static final int MAX_SLOW_REQUESTS = 20;

    private final ProfileInfo profileInfo;
    private final ProfileEventStreamRepository eventStreamRepository;
    private final Type eventType;

    public HttpManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventStreamRepository eventStreamRepository,
            Type eventType) {

        this.profileInfo = profileInfo;
        this.eventStreamRepository = eventStreamRepository;
        this.eventType = eventType;
    }

    @Override
    public HttpOverviewData overviewData() {
        return _overviewData(null);
    }

    @Override
    public HttpOverviewData overviewData(String uri) {
        return _overviewData(uri);
    }

    private HttpOverviewData _overviewData(String uri) {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(eventType)
                .withTimeRange(timeRange)
                .withJsonFields();

        Predicate<String> uriFilter = null;
        if (uri != null) {
            uriFilter = uri::equals;
        }

        return eventStreamRepository.genericStreaming(
                configurer, new HttpOverviewEventBuilder(timeRange, MAX_SLOW_REQUESTS, uriFilter));
    }
}
