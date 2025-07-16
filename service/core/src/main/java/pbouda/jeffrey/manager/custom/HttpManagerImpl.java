/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.manager.custom;

import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.manager.custom.builder.HttpOverviewEventBuilder;
import pbouda.jeffrey.manager.custom.model.http.HttpOverviewData;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;

import java.util.function.Predicate;

public class HttpManagerImpl implements HttpManager {

    private static final int MAX_SLOW_REQUESTS = 20;

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;

    public HttpManagerImpl(ProfileInfo profileInfo, ProfileEventRepository eventRepository) {
        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
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
                .withEventType(Type.HTTP_SERVER_EXCHANGE)
                .withTimeRange(timeRange)
                .withJsonFields();

        Predicate<String> uriFilter = null;
        if (uri != null) {
            uriFilter = uri::equals;
        }

        return eventRepository.newEventStreamerFactory(configurer)
                .newGenericStreamer()
                .startStreaming(new HttpOverviewEventBuilder(timeRange, MAX_SLOW_REQUESTS, uriFilter));
    }
}
