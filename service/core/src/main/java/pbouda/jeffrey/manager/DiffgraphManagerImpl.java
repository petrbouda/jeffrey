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
import pbouda.jeffrey.common.ProfilingStartEnd;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.common.time.RelativeTimeRange;
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.provider.api.model.graph.GraphInfo;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.ProfileGraphRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DiffgraphManagerImpl extends AbstractFlamegraphManager {

    private static final List<Type> SUPPORTED_EVENTS = List.of(
            Type.EXECUTION_SAMPLE,
            Type.WALL_CLOCK_SAMPLE,
            Type.OBJECT_ALLOCATION_SAMPLE,
            Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
            Type.OBJECT_ALLOCATION_OUTSIDE_TLAB);

    private final ProfileInfo primaryProfileInfo;
    private final ProfileInfo secondaryProfileInfo;
    private final ProfileEventRepository primaryEventsReadRepository;
    private final ProfileEventRepository secondaryEventsReadRepository;
    private final GraphGenerator generator;

    public DiffgraphManagerImpl(
            ProfileInfo primaryProfileInfo,
            ProfileInfo secondaryProfileInfo,
            ProfileEventRepository primaryEventsReadRepository,
            ProfileEventRepository secondaryEventsReadRepository,
            ProfileGraphRepository graphRepository,
            GraphGenerator generator) {

        super(primaryProfileInfo, graphRepository);
        this.primaryProfileInfo = primaryProfileInfo;
        this.secondaryProfileInfo = secondaryProfileInfo;
        this.primaryEventsReadRepository = primaryEventsReadRepository;
        this.secondaryEventsReadRepository = secondaryEventsReadRepository;
        this.generator = generator;
    }

    @Override
    public List<EventSummaryResult> eventSummaries() {
        List<EventSummary> primaryEvents = primaryEventsReadRepository.eventSummaries(SUPPORTED_EVENTS).stream()
                .filter(eventSummary -> eventSummary.samples() > 0)
                .toList();

        List<EventSummary> secondaryEvents = secondaryEventsReadRepository.eventSummaries(SUPPORTED_EVENTS).stream()
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

    private static Optional<EventSummary> findEventType(List<EventSummary> secondary, String eventName) {
        return secondary.stream()
                .filter(e -> eventName.equals(e.name()))
                .findFirst();
    }

    @Override
    public GraphData generate(Generate generateRequest) {
        ProfilingStartEnd primaryStartEnd = new ProfilingStartEnd(
                primaryProfileInfo.startedAt(), primaryProfileInfo.finishedAt());
        RelativeTimeRange relativeTimeRange = generateRequest.timeRange()
                .toRelativeTimeRange(primaryStartEnd);

        // Baseline is the secondary profile and comparison is the "new one" - primary
        Config config = Config.differentialBuilder()
                .withPrimaryId(primaryProfileInfo.id())
                .withPrimaryStartEnd(primaryStartEnd)
                .withSecondaryId(secondaryProfileInfo.id())
                .withSecondaryStartEnd(new ProfilingStartEnd(secondaryProfileInfo.startedAt(), secondaryProfileInfo.finishedAt()))
                .withGraphParameters(generateRequest.graphParameters())
                .withEventType(generateRequest.eventType())
                .withTimeRange(relativeTimeRange)
                .build();

        return generator.generate(config);
    }

    @Override
    public void save(Generate generateRequest, String flamegraphName) {
        GraphInfo graphInfo = GraphInfo.custom(
                primaryProfileInfo.id(),
                generateRequest.eventType(),
                generateRequest.graphParameters().threadMode(),
                generateRequest.graphParameters().collectWeight(),
                flamegraphName);

        ProfilingStartEnd primaryStartEnd = new ProfilingStartEnd(
                primaryProfileInfo.startedAt(), primaryProfileInfo.finishedAt());
        RelativeTimeRange relativeTimeRange = generateRequest.timeRange()
                .toRelativeTimeRange(primaryStartEnd);

        Config config = Config.differentialBuilder()
                .withPrimaryId(primaryProfileInfo.id())
                .withPrimaryStartEnd(primaryStartEnd)
                .withSecondaryId(secondaryProfileInfo.id())
                .withSecondaryStartEnd(new ProfilingStartEnd(secondaryProfileInfo.startedAt(), secondaryProfileInfo.finishedAt()))
                .withEventType(generateRequest.eventType())
                .withGraphParameters(generateRequest.graphParameters())
                .withTimeRange(relativeTimeRange)
                .build();

        generateAndSave(graphInfo, () -> generator.generate(config));
    }
}
