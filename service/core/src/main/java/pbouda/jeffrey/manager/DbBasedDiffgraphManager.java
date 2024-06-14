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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.TimeUtils;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.TimeRange;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.flamegraph.GraphExporter;
import pbouda.jeffrey.generator.flamegraph.diff.DiffgraphGenerator;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.jfr.info.EventInformationProvider;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.model.GraphInfo;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.nio.file.Path;
import java.util.List;

public class DbBasedDiffgraphManager extends AbstractDbBasedGraphManager {

    private static final List<Type> SUPPORTED_EVENTS = List.of(
            Type.EXECUTION_SAMPLE,
            Type.OBJECT_ALLOCATION_SAMPLE,
            Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
            Type.OBJECT_ALLOCATION_OUTSIDE_TLAB);

    private final ProfileInfo primaryProfileInfo;
    private final ProfileInfo secondaryProfileInfo;
    private final DiffgraphGenerator generator;
    private final TimeseriesGenerator timeseriesGenerator;
    private final Path primaryRecording;
    private final Path secondaryRecording;
    private final WorkingDirs workingDirs;

    public DbBasedDiffgraphManager(
            ProfileInfo primaryProfileInfo,
            ProfileInfo secondaryProfileInfo,
            WorkingDirs workingDirs,
            GraphRepository repository,
            DiffgraphGenerator generator,
            GraphExporter graphExporter,
            TimeseriesGenerator timeseriesGenerator) {

        super(GraphType.PRIMARY, primaryProfileInfo, workingDirs, repository, graphExporter);

        this.workingDirs = workingDirs;
        this.primaryRecording = workingDirs.profileRecording(primaryProfileInfo);
        this.secondaryRecording = workingDirs.profileRecording(secondaryProfileInfo);
        this.primaryProfileInfo = primaryProfileInfo;
        this.secondaryProfileInfo = secondaryProfileInfo;
        this.generator = generator;
        this.timeseriesGenerator = timeseriesGenerator;
    }

    @Override
    public JsonNode supportedEvents() {
        ArrayNode primaryEvents = new EventInformationProvider(
                workingDirs.profileRecording(primaryProfileInfo), SUPPORTED_EVENTS)
                .get();
        ArrayNode secondaryEvents = new EventInformationProvider(
                workingDirs.profileRecording(secondaryProfileInfo), SUPPORTED_EVENTS)
                .get();

        ArrayNode result = Json.createArray();
        for (JsonNode primaryEvent : primaryEvents) {
            String primaryCode = primaryEvent.get("code").asText();
            if (containsEventType(secondaryEvents, primaryCode)) {
                result.add(primaryEvent);
            }
        }



        return result;
    }

    private static boolean containsEventType(ArrayNode events, String code) {
        for (JsonNode event : events) {
            if (code.equals(event.get("code").asText())) {
                return true;
            }
        }

        return false;
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

        // Baseline is the secondary profile and comparison is the "new one" - primary
        Config config = Config.differentialBuilder()
                .withPrimaryRecording(primaryRecording)
                .withPrimaryStart(primaryProfileInfo.startedAt())
                .withSecondaryRecording(secondaryRecording)
                .withSecondaryStart(secondaryProfileInfo.startedAt())
                .withEventType(eventType)
                .withThreadMode(threadMode)
                .withTimeRange(timeRange)
                .build();

        return generator.generate(config);
    }

    @Override
    public void save(Type eventType, TimeRangeRequest timeRange, String flamegraphName, boolean threadMode, boolean weight) {
        GraphInfo graphInfo = GraphInfo.custom(primaryProfileInfo.id(), eventType, threadMode, weight, flamegraphName);
        Config config = Config.differentialBuilder()
                .withPrimaryRecording(primaryRecording)
                .withPrimaryStart(primaryProfileInfo.startedAt())
                .withSecondaryRecording(secondaryRecording)
                .withSecondaryStart(secondaryProfileInfo.startedAt())
                .withEventType(eventType)
                .withThreadMode(threadMode)
                .withCollectWeight(weight)
                .withTimeRange(TimeRange.create(timeRange.start(), timeRange.end(), timeRange.absoluteTime()))
                .build();

        generateAndSave(graphInfo, () -> generator.generate(config));
    }

    @Override
    public ArrayNode timeseries(Type eventType, boolean useWeight) {
        Config timeseriesConfig = Config.differentialBuilder()
                .withPrimaryRecording(primaryRecording)
                .withSecondaryRecording(secondaryRecording)
                .withEventType(eventType)
                .withPrimaryStart(primaryProfileInfo.startedAt())
                .withSecondaryStart(secondaryProfileInfo.startedAt())
                .withCollectWeight(useWeight)
                .build();

        return timeseriesGenerator.generate(timeseriesConfig);
    }

    @Override
    public ArrayNode timeseries(Type eventType, String searchPattern, boolean useWeight) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String generateFilename(Type eventType) {
        return primaryProfileInfo.id() + "-diff-" + eventType.code() + "-" + TimeUtils.currentDateTime();
    }
}
