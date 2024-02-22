package pbouda.jeffrey.repository.model;

import java.time.Instant;
import java.util.UUID;

public record HeatmapInfo(String id, String profileId, String name, Instant createdAt) {

    public HeatmapInfo(String profileId, String name) {
        this(UUID.randomUUID().toString(), profileId, name, Instant.now());
    }
}
