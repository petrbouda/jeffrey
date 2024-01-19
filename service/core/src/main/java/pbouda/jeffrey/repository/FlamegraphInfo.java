package pbouda.jeffrey.repository;

import java.time.Instant;
import java.util.UUID;

public record FlamegraphInfo(String id, String profileId, String name, Instant createdAt) {

    public FlamegraphInfo(String profileId, String name) {
        this(UUID.randomUUID().toString(), profileId, name, Instant.now());
    }
}
