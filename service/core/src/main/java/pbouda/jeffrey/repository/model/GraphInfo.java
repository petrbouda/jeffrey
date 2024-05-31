package pbouda.jeffrey.repository.model;

import pbouda.jeffrey.common.Type;

import java.time.Instant;
import java.util.UUID;

public record GraphInfo(
        String id,
        String profileId,
        Type eventType,
        boolean useThreadMode,
        boolean useWeight,
        boolean complete,
        String name,
        Instant createdAt
) {

    public GraphInfo(
            String profileId,
            Type eventType,
            boolean useThreadMode,
            boolean useWeight,
            boolean complete,
            String name) {

        this(UUID.randomUUID().toString(),
                profileId,
                eventType,
                useThreadMode,
                useWeight,
                complete,
                name,
                Instant.now());
    }

    public static GraphInfo custom(
            String profileId,
            Type eventType,
            boolean useThreadMode,
            boolean useWeight,
            String name) {

        return new GraphInfo(profileId, eventType, useThreadMode, useWeight, false, name);
    }
}
