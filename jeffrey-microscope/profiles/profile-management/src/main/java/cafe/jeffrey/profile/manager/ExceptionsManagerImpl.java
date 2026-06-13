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

import cafe.jeffrey.profile.manager.model.exceptions.ExceptionRateTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionTypeStat;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionTypesBuilder;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionsOverview;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.ArrayList;
import java.util.List;

public class ExceptionsManagerImpl implements ExceptionsManager {

    private static final String THROWABLES_FIELD = "throwables";

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;

    public ExceptionsManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public ExceptionsOverview overview() {
        long totalThrowables = eventRepository.latestJsonFields(Type.EXCEPTION_STATISTICS)
                .map(fields -> Math.max(0, Json.readLong(fields, THROWABLES_FIELD)))
                .orElse(0L);

        List<ExceptionTypeStat> types = topTypes();
        long sampledThrows = types.stream().filter(type -> !type.error()).mapToLong(ExceptionTypeStat::count).sum();
        long errors = types.stream().filter(ExceptionTypeStat::error).mapToLong(ExceptionTypeStat::count).sum();

        return new ExceptionsOverview(
                totalThrowables,
                sampledThrows,
                errors,
                types.size(),
                eventRepository.containsEventType(Type.JAVA_EXCEPTION_THROW),
                eventRepository.containsEventType(Type.JAVA_ERROR_THROW));
    }

    @Override
    public TimeseriesData timeline() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        // The builder computes deltas between consecutive cumulative samples — the stream
        // must be chronological.
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.EXCEPTION_STATISTICS)
                .withJsonFields()
                .orderedByTime();

        return eventStreamRepository.genericStreaming(configurer, new ExceptionRateTimeseriesBuilder(timeRange));
    }

    @Override
    public List<ExceptionTypeStat> topTypes() {
        List<Type> presentTypes = new ArrayList<>(2);
        if (eventRepository.containsEventType(Type.JAVA_EXCEPTION_THROW)) {
            presentTypes.add(Type.JAVA_EXCEPTION_THROW);
        }
        if (eventRepository.containsEventType(Type.JAVA_ERROR_THROW)) {
            presentTypes.add(Type.JAVA_ERROR_THROW);
        }
        if (presentTypes.isEmpty()) {
            return List.of();
        }

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(presentTypes)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(configurer, new ExceptionTypesBuilder());
    }
}
