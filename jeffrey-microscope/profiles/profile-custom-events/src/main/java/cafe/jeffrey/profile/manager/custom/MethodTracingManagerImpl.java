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

package cafe.jeffrey.profile.manager.custom;

import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.manager.custom.builder.MethodTracingCumulatedBuilder;
import cafe.jeffrey.profile.manager.custom.builder.MethodTracingOverviewBuilder;
import cafe.jeffrey.profile.manager.custom.builder.MethodTracingSlowestBuilder;
import cafe.jeffrey.profile.manager.custom.model.method.CumulationMode;
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
}
