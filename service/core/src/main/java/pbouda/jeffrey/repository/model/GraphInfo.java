package pbouda.jeffrey.repository.model;

import pbouda.jeffrey.common.Type;

import java.time.Instant;
import java.util.UUID;

public record GraphInfo(
        String id,
        String profileId,
        Type eventType,
        boolean complete,
        String name,
        Instant createdAt
) {

    public GraphInfo(String profileId, Type eventType, boolean complete, String name) {
        this(UUID.randomUUID().toString(),
                profileId,
                eventType,
                complete,
                name,
                Instant.now());
    }

    public static GraphInfo complete(String profileId, Type eventType) {
        String flamegraphName = profileId + "-" + eventType.code();
        return new GraphInfo(profileId, eventType, true, flamegraphName);
    }

    public static GraphInfo custom(String profileId, Type eventType, String name) {
        return new GraphInfo(profileId, eventType, false, name);
    }
}
