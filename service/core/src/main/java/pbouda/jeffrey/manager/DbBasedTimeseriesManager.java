package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import pbouda.jeffrey.Json;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.generator.timeseries.TimeseriesConfig;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.repository.TimeseriesRepository;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.time.Instant;

public class DbBasedTimeseriesManager implements TimeseriesManager {

    private final ProfileInfo profileInfo;
    private final TimeseriesGenerator generator;
    private final TimeseriesRepository repository;

    public DbBasedTimeseriesManager(
            ProfileInfo profileInfo,
            TimeseriesRepository repository,
            TimeseriesGenerator generator) {

        this.profileInfo = profileInfo;
        this.generator = generator;
        this.repository = repository;
    }

    @Override
    public ArrayNode contentByEventType(EventType eventType) {
        return generate(eventType);
    }

    @Override
    public ArrayNode contentByEventType(EventType eventType, Instant start, Instant end) {
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

    private ArrayNode generate(EventType eventType) {
        TimeseriesConfig timeseriesConfig = TimeseriesConfig.primaryBuilder()
                .withPrimaryRecording(profileInfo.recordingPath())
                .withEventType(eventType)
                .withPrimaryStart(profileInfo.startedAt())
                .build();

        return generator.generate(timeseriesConfig);
    }
}
