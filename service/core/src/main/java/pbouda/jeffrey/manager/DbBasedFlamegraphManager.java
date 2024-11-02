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
import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.TimeUtils;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.TimeRange;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.generator.basic.event.EventSummary;
import pbouda.jeffrey.generator.basic.info.EventInformationProvider;
import pbouda.jeffrey.generator.flamegraph.GraphExporter;
import pbouda.jeffrey.generator.flamegraph.GraphGenerator;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.model.GraphInfo;
import pbouda.jeffrey.settings.ActiveSettingsProvider;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DbBasedFlamegraphManager extends AbstractDbBasedFlamegraphManager {

    private final ProfileInfo profileInfo;
    private final ActiveSettingsProvider settingsProvider;
    private final GraphGenerator generator;
    private final Path profileRecordingDir;
    private final ProfileDirs profileDirs;

    public DbBasedFlamegraphManager(
            ProfileInfo profileInfo,
            ProfileDirs profileDirs,
            ActiveSettingsProvider settingsProvider,
            GraphRepository repository,
            GraphGenerator generator,
            GraphExporter graphExporter) {

        super(profileInfo, repository, graphExporter);

        this.profileDirs = profileDirs;
        this.profileRecordingDir = profileDirs.recordingsDir();
        this.profileInfo = profileInfo;
        this.settingsProvider = settingsProvider;
        this.generator = generator;
    }

    @Override
    public Map<String, EventSummaryResult> supportedEvents() {
        List<EventSummary> eventSummaries =
                new EventInformationProvider(settingsProvider, profileDirs.allRecordingPaths(), ProcessableEvents.all()).get();

        return eventSummaries.stream()
                .collect(Collectors.toMap(EventSummary::name, EventSummaryResult::new));
    }

    @Override
    public ObjectNode generate(Type eventType, TimeRangeRequest timeRangeRequest, boolean threadMode) {
        TimeRange timeRange = null;
        if (timeRangeRequest != null) {
            timeRange = TimeRange.create(
                    timeRangeRequest.start(),
                    timeRangeRequest.end(),
                    timeRangeRequest.absoluteTime());
        }

        Config config = Config.primaryBuilder()
                .withPrimaryRecordingDir(profileRecordingDir)
                .withPrimaryStart(profileInfo.startedAt())
                .withEventType(eventType)
                .withThreadMode(threadMode)
                .withTimeRange(timeRange)
                .build();

        return generator.generate(config);
    }

    @Override
    public void save(Type eventType, TimeRangeRequest timeRange, String flamegraphName, boolean threadMode, boolean weight) {
        GraphInfo graphInfo = GraphInfo.custom(profileInfo.id(), eventType, threadMode, weight, flamegraphName);
        Config config = Config.primaryBuilder()
                .withPrimaryRecordingDir(profileRecordingDir)
                .withPrimaryStart(profileInfo.startedAt())
                .withEventType(eventType)
                .withThreadMode(threadMode)
                .withCollectWeight(weight)
                .withTimeRange(TimeRange.create(timeRange.start(), timeRange.end(), timeRange.absoluteTime()))
                .build();

        generateAndSave(graphInfo, () -> generator.generate(config));
    }

    @Override
    public String generateFilename(Type eventType) {
        return profileInfo.id() + "-" + eventType.code() + "-" + TimeUtils.currentDateTime();
    }
}
