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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.stw.StwBudgetBuilder;
import cafe.jeffrey.profile.manager.model.stw.StwClassifier;
import cafe.jeffrey.profile.manager.model.stw.StwEvent;
import cafe.jeffrey.profile.manager.model.stw.StwTimelineBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

public class StwTimelineManagerImpl implements StwTimelineManager {

    private final ProfileInfo profileInfo;
    private final ProfileEventStreamRepository eventStreamRepository;

    public StwTimelineManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public List<StwEvent> timeline(long minDurationNanos) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(StwClassifier.SOURCE_TYPES)
                .withJsonFields();
        return eventStreamRepository.genericStreaming(configurer, new StwTimelineBuilder(minDurationNanos));
    }

    @Override
    public TimeseriesData budget() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(StwClassifier.SOURCE_TYPES)
                .withJsonFields();
        return eventStreamRepository.genericStreaming(configurer, new StwBudgetBuilder(timeRange));
    }
}
