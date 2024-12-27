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

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.EventSummary;
import pbouda.jeffrey.common.ProfilingStartEnd;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.profile.summary.EventSummaryProvider;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.model.GraphInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PrimaryFlamegraphManager extends AbstractFlamegraphManager {

    private final ProfileInfo profileInfo;
    private final EventSummaryProvider summaryProvider;
    private final GraphGenerator generator;
    private final Path profileRecordingDir;

    public PrimaryFlamegraphManager(
            ProfileInfo profileInfo,
            ProfileDirs profileDirs,
            EventSummaryProvider summaryProvider,
            GraphRepository repository,
            GraphGenerator generator) {

        super(profileInfo, repository);
        this.profileRecordingDir = profileDirs.recordingsDir();
        this.profileInfo = profileInfo;
        this.summaryProvider = summaryProvider;
        this.generator = generator;
    }

    @Override
    public Map<String, EventSummaryResult> eventSummaries() {
        List<EventSummary> eventSummaries = summaryProvider.get();
        return eventSummaries.stream()
                .collect(Collectors.toMap(EventSummary::name, EventSummaryResult::new));
    }

    @Override
    public ObjectNode generate(Generate generateRequest) {
        Config config = Config.primaryBuilder()
                .withPrimaryRecordingDir(profileRecordingDir)
                .withPrimaryStartEnd(new ProfilingStartEnd(profileInfo.startedAt(), profileInfo.endedAt()))
                .withEventType(generateRequest.eventType())
                .withGraphParameters(generateRequest.graphParameters())
                .withTimeRange(generateRequest.timeRange())
                .withThreadInfo(generateRequest.threadInfo())
                .build();

        return generator.generate(config, generateRequest.markers());
    }

    @Override
    public void save(Generate generateRequest, String flamegraphName) {
        GraphInfo graphInfo = GraphInfo.custom(
                profileInfo.id(),
                generateRequest.graphParameters().threadMode(),
                generateRequest.graphParameters().collectWeight(),
                flamegraphName);

        Config config = Config.primaryBuilder()
                .withPrimaryRecordingDir(profileRecordingDir)
                .withPrimaryStartEnd(new ProfilingStartEnd(profileInfo.startedAt(), profileInfo.endedAt()))
                .withEventType(generateRequest.eventType())
                .withTimeRange(generateRequest.timeRange())
                .build();

        generateAndSave(graphInfo, () -> generator.generate(config, generateRequest.markers()));
    }
}
