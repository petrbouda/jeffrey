/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;

import java.util.List;

public class PrimaryFlamegraphManager implements FlamegraphManager {

    private static final List<Type> SUPPORTED_EVENTS = List.of(
            Type.NATIVE_LEAK,
            Type.MALLOC,
            Type.EXECUTION_SAMPLE,
            Type.WALL_CLOCK_SAMPLE,
            Type.OBJECT_ALLOCATION_SAMPLE,
            Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
            Type.OBJECT_ALLOCATION_OUTSIDE_TLAB,
            Type.THREAD_PARK,
            Type.THREAD_SLEEP,
            Type.JAVA_MONITOR_ENTER,
            Type.JAVA_MONITOR_WAIT);

    private final ProfileEventTypeRepository eventTypeRepository;
    private final GraphGenerator generator;
    private final GraphRepositoryManager.Factory graphRepositoryManagerFactory;

    public PrimaryFlamegraphManager(
            ProfileEventTypeRepository eventTypeRepository,
            GraphGenerator generator,
            GraphRepositoryManager.Factory graphRepositoryManagerFactory) {

        this.eventTypeRepository = eventTypeRepository;
        this.generator = generator;
        this.graphRepositoryManagerFactory = graphRepositoryManagerFactory;
    }

    @Override
    public GraphRepositoryManager graphRepositoryManager() {
        return graphRepositoryManagerFactory.apply(this);
    }

    @Override
    public List<EventSummaryResult> eventSummaries() {
        return eventTypeRepository.eventSummaries(SUPPORTED_EVENTS).stream()
                .filter(eventSummary -> eventSummary.samples() > 0)
                .map(EventSummaryResult::new)
                .toList();
    }

    @Override
    public GraphData generate(GraphParameters params) {
        return generator.generate(params);
    }
}
