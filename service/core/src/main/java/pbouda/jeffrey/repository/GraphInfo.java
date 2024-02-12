package pbouda.jeffrey.repository;

import pbouda.jeffrey.common.EventType;

import java.time.Instant;
import java.util.UUID;

public record GraphInfo(
        String id,
        String profileId,
        EventType eventType,
        boolean complete,
        String name,
        Instant createdAt
) {

    public GraphInfo(String profileId, EventType eventType, boolean complete, String name) {
        this(UUID.randomUUID().toString(),
                profileId,
                eventType,
                complete,
                name,
                Instant.now());
    }

    public static GraphInfo complete(String profileId, EventType eventType) {
        String flamegraphName = profileId + "-" + eventType.code();
        return new GraphInfo(profileId, eventType, true, flamegraphName);
    }

    public static GraphInfo custom(String profileId, EventType eventType, String name) {
        return new GraphInfo(profileId, eventType, false, name);
    }
}
