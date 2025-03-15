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
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;

import java.util.List;

public class PrimaryFlamegraphManager implements FlamegraphManager {

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
        return eventTypeRepository.eventSummaries().stream()
                .filter(eventSummary -> eventSummary.samples() > 0)
                .map(EventSummaryResult::new)
                .toList();
    }

    @Override
    public GraphData generate(GraphParameters params) {
        return generator.generate(params);
    }
}
