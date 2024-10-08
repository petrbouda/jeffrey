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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.TimeUtils;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.TimeRange;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.basic.event.EventSummary;
import pbouda.jeffrey.generator.basic.info.EventInformationProvider;
import pbouda.jeffrey.generator.flamegraph.GraphExporter;
import pbouda.jeffrey.generator.flamegraph.GraphGenerator;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.model.GraphInfo;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DbBasedFlamegraphManager extends AbstractDbBasedGraphManager {

    private final ProfileInfo profileInfo;
    private final GraphGenerator generator;
    private final TimeseriesGenerator timeseriesGenerator;
    private final Path profileRecordingDir;
    private final WorkingDirs workingDirs;

    public DbBasedFlamegraphManager(
            ProfileInfo profileInfo,
            WorkingDirs workingDirs,
            GraphRepository repository,
            GraphGenerator generator,
            GraphExporter graphExporter,
            TimeseriesGenerator timeseriesGenerator) {

        super(profileInfo, workingDirs, repository, graphExporter);

        this.workingDirs = workingDirs;
        this.profileRecordingDir = workingDirs.profileRecordingDir(profileInfo);
        this.profileInfo = profileInfo;
        this.generator = generator;
        this.timeseriesGenerator = timeseriesGenerator;
    }

    @Override
    public Map<String, EventSummaryResult> supportedEvents() {
        List<EventSummary> eventSummaries =
                new EventInformationProvider(workingDirs.profileRecordings(profileInfo)).get();

        return eventSummaries.stream()
                .collect(Collectors.toMap(s -> s.eventType().getName(), EventSummaryResult::new));
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
    public ArrayNode timeseries(Type eventType, boolean useWeight) {
        Config config = Config.primaryBuilder()
                .withPrimaryRecordingDir(profileRecordingDir)
                .withEventType(eventType)
                .withPrimaryStart(profileInfo.startedAt())
                .withCollectWeight(useWeight)
                .build();

        return timeseriesGenerator.generate(config);
    }

    @Override
    public ArrayNode timeseries(Type eventType, String searchPattern, boolean useWeight) {
        Config config = Config.primaryBuilder()
                .withPrimaryRecordingDir(profileRecordingDir)
                .withPrimaryStart(profileInfo.startedAt())
                .withEventType(eventType)
                .withSearchPattern(searchPattern)
                .withCollectWeight(useWeight)
                .build();

        return timeseriesGenerator.generate(config);
    }

    @Override
    public String generateFilename(Type eventType) {
        return profileInfo.id() + "-" + eventType.code() + "-" + TimeUtils.currentDateTime();
    }
}
