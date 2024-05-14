package pbouda.jeffrey.repository.model;

import pbouda.jeffrey.common.Type;

import java.time.Instant;
import java.util.UUID;

public record TimeseriesInfo(
        String id,
        String profileId,
        Type eventType,
        Instant createdAt
) {

    public TimeseriesInfo(String profileId, Type eventType) {
        this(UUID.randomUUID().toString(), profileId, eventType, Instant.now());
    }
}
