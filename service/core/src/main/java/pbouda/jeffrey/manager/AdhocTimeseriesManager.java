package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.time.Instant;

public class AdhocTimeseriesManager implements TimeseriesManager {

    private final ProfileInfo profileInfo;
    private final WorkingDirs workingDirs;
    private final TimeseriesGenerator generator;

    public AdhocTimeseriesManager(
            ProfileInfo profileInfo,
            WorkingDirs workingDirs,
            TimeseriesGenerator generator) {

        this.profileInfo = profileInfo;
        this.workingDirs = workingDirs;
        this.generator = generator;
    }

    @Override
    public ArrayNode contentByEventType(Type eventType) {
        return generate(eventType);
    }

    @Override
    public ArrayNode contentByEventType(Type eventType, Instant start, Instant end) {
        ArrayNode arrayNode = contentByEventType(eventType);
        return filter(arrayNode, start, end);
    }

    private static ArrayNode filter(ArrayNode arrayNode, Instant start, Instant end) {
        ArrayNode result = Json.createArray();
        for (JsonNode cell : arrayNode) {
            ArrayNode timeSamples = (ArrayNode) cell;
            long time = timeSamples.get(0).asLong();

            if (time >= start.toEpochMilli() && time <= end.toEpochMilli()) {
                result.add(cell);
            }
        }
        return result;
    }

    private ArrayNode generate(Type eventType) {
        Config timeseriesConfig = Config.primaryBuilder()
                .withPrimaryRecording(workingDirs.profileRecording(profileInfo))
                .withEventType(eventType)
                .withPrimaryStart(profileInfo.startedAt())
                .build();

        return generator.generate(timeseriesConfig);
    }
}
