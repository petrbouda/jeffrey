package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.repository.CacheKey;
import pbouda.jeffrey.repository.CacheRepository;
import pbouda.jeffrey.viewer.EventViewerGenerator;

import java.nio.file.Path;
import java.util.Optional;

public class DbBasedViewerManager implements EventViewerManager {

    private final Path recordingPath;
    private final CacheRepository cacheRepository;
    private final EventViewerGenerator generator;

    public DbBasedViewerManager(
            Path recordingPath,
            CacheRepository cacheRepository,
            EventViewerGenerator generator) {

        this.recordingPath = recordingPath;
        this.cacheRepository = cacheRepository;
        this.generator = generator;
    }

    @Override
    public JsonNode allEventTypes() {
        Optional<JsonNode> resultOpt = cacheRepository.get(CacheKey.ALL_EVENT_TYPES);
        if (resultOpt.isPresent()) {
            return resultOpt.get();
        } else {
            JsonNode allEventTypes = generator.allEventTypes(recordingPath);
            cacheRepository.insert(CacheKey.ALL_EVENT_TYPES, allEventTypes);
            return allEventTypes;
        }
    }

    @Override
    public JsonNode events(Type eventType) {
        return generator.events(recordingPath, eventType);
    }

    @Override
    public JsonNode eventColumns(Type eventType) {
        return generator.eventColumns(recordingPath, eventType);
    }
}
