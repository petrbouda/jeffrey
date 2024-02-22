package pbouda.jeffrey.repository.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public record ProfileInfo(String id, String name, Instant createdAt, Instant startedAt, Path recordingPath) {

    public ProfileInfo(String name, Instant startedAt, Path recordingPath) {
        this(UUID.randomUUID().toString(), name, Instant.now(), startedAt, recordingPath);
    }
}
