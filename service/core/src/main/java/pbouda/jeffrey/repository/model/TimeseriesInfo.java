package pbouda.jeffrey.repository.model;

import pbouda.jeffrey.common.EventType;

import java.time.Instant;
import java.util.UUID;

public record TimeseriesInfo(
        String id,
        String profileId,
        EventType eventType,
        Instant createdAt
) {

    public TimeseriesInfo(String profileId, EventType eventType) {
        this(UUID.randomUUID().toString(), profileId, eventType, Instant.now());
    }
}
