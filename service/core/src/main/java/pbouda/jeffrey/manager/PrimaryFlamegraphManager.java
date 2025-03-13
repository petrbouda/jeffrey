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

import pbouda.jeffrey.common.ProfilingStartEnd;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.config.GraphParametersBuilder;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.common.time.RelativeTimeRange;
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.provider.api.model.graph.GraphInfo;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;
import pbouda.jeffrey.provider.api.repository.ProfileGraphRepository;

import java.util.List;

public class PrimaryFlamegraphManager extends AbstractFlamegraphManager {

    private final ProfileInfo profileInfo;
    private final ProfileEventTypeRepository eventTypeRepository;
    private final GraphGenerator generator;

    public PrimaryFlamegraphManager(
            ProfileInfo profileInfo,
            ProfileEventTypeRepository eventTypeRepository,
            ProfileGraphRepository repository,
            GraphGenerator generator) {

        super(profileInfo, repository);
        this.profileInfo = profileInfo;
        this.eventTypeRepository = eventTypeRepository;
        this.generator = generator;
    }

    @Override
    public List<EventSummaryResult> eventSummaries() {
        return eventTypeRepository.eventSummaries().stream()
                .filter(eventSummary -> eventSummary.samples() > 0)
                .map(EventSummaryResult::new)
                .toList();
    }

    @Override
    public GraphData generate(Generate generateRequest) {
        ProfilingStartEnd primaryStartEnd = new ProfilingStartEnd(
                profileInfo.profilingStartedAt(), profileInfo.profilingFinishedAt());

        RelativeTimeRange relativeTimeRange = generateRequest.timeRange()
                .toRelativeTimeRange(primaryStartEnd);

        Config config = Config.primaryBuilder()
                .withPrimaryId(profileInfo.id())
                .withPrimaryStartEnd(primaryStartEnd)
                .withEventType(generateRequest.eventType())
                .withGraphParameters(generateRequest.graphParameters())
                .withTimeRange(relativeTimeRange)
                .withThreadInfo(generateRequest.threadInfo())
                .build();

        return generator.generate(config);
    }

    @Override
    public void save(Generate generateRequest, String flamegraphName) {
        GraphParameters params = generateRequest.graphParameters();

        GraphInfo graphInfo = GraphInfo.custom(
                profileInfo.id(),
                generateRequest.eventType(),
                params.threadMode(),
                params.useWeight(),
                flamegraphName);

        ProfilingStartEnd primaryStartEnd = new ProfilingStartEnd(
                profileInfo.profilingStartedAt(), profileInfo.profilingFinishedAt());

        RelativeTimeRange relativeTimeRange = generateRequest.timeRange()
                .toRelativeTimeRange(primaryStartEnd);

        Config config = Config.primaryBuilder()
                .withPrimaryId(profileInfo.id())
                .withPrimaryStartEnd(primaryStartEnd)
                .withEventType(generateRequest.eventType())
                .withTimeRange(relativeTimeRange)
                .withGraphParameters(params)
                .build();

        generateAndSave(graphInfo, () -> generator.generate(config));
    }
}
