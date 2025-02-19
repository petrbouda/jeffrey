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
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.common.time.RelativeTimeRange;
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.persistence.profile.EventsReadRepository;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.model.GraphInfo;

import java.nio.file.Path;
import java.util.List;

public class PrimaryFlamegraphManager extends AbstractFlamegraphManager {

    private final ProfileInfo profileInfo;
    private final EventsReadRepository eventsReadRepository;
    private final GraphGenerator generator;
    private final Path profileRecordingDir;

    public PrimaryFlamegraphManager(
            ProfileInfo profileInfo,
            ProfileDirs profileDirs,
            EventsReadRepository eventsReadRepository,
            GraphRepository repository,
            GraphGenerator generator) {

        super(profileInfo, repository);
        this.profileRecordingDir = profileDirs.recordingsDir();
        this.profileInfo = profileInfo;
        this.eventsReadRepository = eventsReadRepository;
        this.generator = generator;
    }

    @Override
    public List<EventSummaryResult> eventSummaries() {
        return eventsReadRepository.eventSummaries().stream()
                .filter(eventSummary -> eventSummary.samples() > 0)
                .map(EventSummaryResult::new)
                .toList();
    }

    @Override
    public GraphData generate(Generate generateRequest) {
        ProfilingStartEnd primaryStartEnd = new ProfilingStartEnd(profileInfo.startedAt(), profileInfo.finishedAt());
        RelativeTimeRange relativeTimeRange = generateRequest.timeRange()
                .toRelativeTimeRange(primaryStartEnd);

        Config config = Config.primaryBuilder()
                .withPrimaryRecordingDir(profileRecordingDir)
                .withPrimaryStartEnd(primaryStartEnd)
                .withEventType(generateRequest.eventType())
                .withGraphParameters(generateRequest.graphParameters())
                .withTimeRange(relativeTimeRange)
                .withThreadInfo(generateRequest.threadInfo())
                .build(false);

        return generator.generate(config, generateRequest.markers());
    }

    @Override
    public void save(Generate generateRequest, String flamegraphName) {
        GraphInfo graphInfo = GraphInfo.custom(
                profileInfo.id(),
                generateRequest.eventType(),
                generateRequest.graphParameters().threadMode(),
                generateRequest.graphParameters().collectWeight(),
                flamegraphName);

        ProfilingStartEnd primaryStartEnd = new ProfilingStartEnd(profileInfo.startedAt(), profileInfo.finishedAt());
        RelativeTimeRange relativeTimeRange = generateRequest.timeRange()
                .toRelativeTimeRange(primaryStartEnd);

        Config config = Config.primaryBuilder()
                .withPrimaryRecordingDir(profileRecordingDir)
                .withPrimaryStartEnd(primaryStartEnd)
                .withEventType(generateRequest.eventType())
                .withTimeRange(relativeTimeRange)
                .build();

        generateAndSave(graphInfo, () -> generator.generate(config, generateRequest.markers()));
    }
}
