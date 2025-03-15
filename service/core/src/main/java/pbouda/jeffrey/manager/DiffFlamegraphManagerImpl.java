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

import pbouda.jeffrey.common.EventSummary;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DiffFlamegraphManagerImpl implements FlamegraphManager {

    private static final List<Type> SUPPORTED_EVENTS = List.of(
            Type.EXECUTION_SAMPLE,
            Type.WALL_CLOCK_SAMPLE,
            Type.OBJECT_ALLOCATION_SAMPLE,
            Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
            Type.OBJECT_ALLOCATION_OUTSIDE_TLAB);

    private final ProfileEventTypeRepository primaryEventTypeRepository;
    private final ProfileEventTypeRepository secondaryEventTypeRepository;
    private final GraphGenerator generator;
    private final GraphRepositoryManager.Factory graphRepositoryManagerFactory;

    public DiffFlamegraphManagerImpl(
            ProfileEventTypeRepository primaryEventTypeRepository,
            ProfileEventTypeRepository secondaryEventTypeRepository,
            GraphGenerator generator,
            GraphRepositoryManager.Factory graphRepositoryManagerFactory) {

        this.primaryEventTypeRepository = primaryEventTypeRepository;
        this.secondaryEventTypeRepository = secondaryEventTypeRepository;
        this.generator = generator;
        this.graphRepositoryManagerFactory = graphRepositoryManagerFactory;
    }

    @Override
    public GraphRepositoryManager graphRepositoryManager() {
        return graphRepositoryManagerFactory.apply(this);
    }

    @Override
    public GraphData generate(GraphParameters parameters) {
        return generator.generate(parameters);
    }

    @Override
    public List<EventSummaryResult> eventSummaries() {
        List<EventSummary> primaryEvents = primaryEventTypeRepository.eventSummaries(SUPPORTED_EVENTS).stream()
                .filter(eventSummary -> eventSummary.samples() > 0)
                .toList();

        List<EventSummary> secondaryEvents = secondaryEventTypeRepository.eventSummaries(SUPPORTED_EVENTS).stream()
                .filter(eventSummary -> eventSummary.samples() > 0)
                .toList();

        List<EventSummaryResult> results = new ArrayList<>();
        for (EventSummary primary : primaryEvents) {
            Optional<EventSummary> secondaryOpt = findEventType(secondaryEvents, primary.name());
            if (secondaryOpt.isPresent()) {
                EventSummaryResult result = new EventSummaryResult(primary, secondaryOpt.get());
                results.add(result);
            }
        }

        return results;
    }

    private static Optional<EventSummary> findEventType(List<EventSummary> secondary, String eventType) {
        return secondary.stream()
                .filter(e -> eventType.equals(e.name()))
                .findFirst();
    }
}
